package de.hinundhergestellt.jhuh.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.HuhProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = KotlinLogging.logger {}

private const val INDEX_JSON = "index.json"

@Service
class ImageDirectoryService(
    private val applicationScope: CoroutineScope,
    properties: HuhProperties
) : AutoCloseable {

    private val root: Path = properties.imageDirectory.toAbsolutePath().normalize()
    private val indexFile: Path = root.resolve(INDEX_JSON)
    private val objectMapper = jacksonObjectMapper()
    private val watchService: WatchService = root.fileSystem.newWatchService()
    private val watchKeyToDir = ConcurrentHashMap<WatchKey, Path>()
    private val dirContent = HashMap<Path, MutableSet<Path>>() // directory -> immediate file children
    private val lock = ReentrantReadWriteLock()
    private val initialized = CompletableFuture<Unit>()
    private val closed = AtomicBoolean(false)
    private val job: Job

    // Debounce / Throttle (>=5s nach letzter Änderung, <=10s nach erster) für index.json
    @Volatile
    private var firstDirtyAt: Long? = null

    @Volatile
    private var lastDirtyAt: Long? = null

    @Volatile
    private var writeJob: Job? = null

    init {
        require(Files.isDirectory(root)) { "path $root must be a directory" }

        // Index laden (falls vorhanden) und initialisieren
        loadIndexJsonIfPresent()

        job = applicationScope.launch(Dispatchers.IO) {
            try {
                initialWalk() // ersetzt sukzessive geladene Einträge durch reale
                // Nach abgeschlossenem Walk einmal schreiben (sofern nicht schon durch Debounce erledigt)
                writeIndexNow()
                initialized.complete(Unit) // falls nicht schon gesetzt
                eventLoop()
            } catch (_: CancellationException) {
                // normal shutdown
            } catch (ex: Exception) {
                logger.error(ex) { "Unexpected error in ImageDirectoryService" }
                initialized.completeExceptionally(ex)
            } finally {
                safeCloseWatchService()
            }
        }
    }

    fun listDirectoryEntries(path: Path): List<Path> {
        // Blockiert nur, wenn kein Index vorhanden war und initialWalk noch läuft
        initialized.join()
        val absolute = if (path.isAbsolute) path.normalize() else root.resolve(path).normalize()
        require(absolute == root || absolute.startsWith(root)) { "path must be relative or below imageDirectory" }
        lock.read {
            val children = dirContent[absolute] ?: return listOf()
            return children.asSequence()
                .sortedBy { it.fileName.toString().lowercase() }
                .toList()
        }
    }

    private fun loadIndexJsonIfPresent() {
        if (!Files.exists(indexFile)) return
        runCatching {
            val map: Map<String, List<String>> = objectMapper.readValue(indexFile.toFile())
            lock.write {
                dirContent.clear()
                map.forEach { (relDir, files) ->
                    val dirPath = if (relDir.isEmpty()) root else root.resolve(relDir).normalize()
                    val set = dirContent.getOrPut(dirPath) { mutableSetOf() }
                    files.forEach { name -> set.add(dirPath.resolve(name)) }
                }
            }
            if (dirContent.isNotEmpty()) {
                // Sofortige Verfügbarkeit des (evtl. veralteten) Index
                initialized.complete(Unit)
                logger.info { "Loaded index.json with ${dirContent.size} entries will be used preliminary." }
            }
        }.onFailure { e ->
            logger.error(e) { "Couldn't load index.json - rebuilding tree" }
        }
    }

    private suspend fun initialWalk() {
        logger.info { "Starting initial walk of directory $root" }
        val visitedDirs = mutableSetOf<Path>()
        val temp = HashMap<Path, MutableSet<Path>>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                registerDirectoryWatcher(dir)
                visitedDirs.add(dir)
                // Lokalen Container vorbereiten, aber alten Inhalt noch nicht verwerfen
                temp[dir] = mutableSetOf()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isDirectory) {
                    temp[file.parent]?.add(file)
                }
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                // Jetzt atomar ersetzen
                val newSet = temp[dir] ?: mutableSetOf()
                lock.write { dirContent[dir] = newSet }
                return FileVisitResult.CONTINUE
            }
        })
        // Entferne Einträge für Verzeichnisse, die nicht mehr existieren
        lock.write {
            val toRemove = dirContent.keys.filter { it !in visitedDirs }
            toRemove.forEach { dirContent.remove(it) }
        }
        logger.info { "Initial walk of directory $root finished" }
    }

    private suspend fun eventLoop() {
        logger.info { "Starting WatchService event loop" }
        while (isActiveAndOpen()) {
            val key = try {
                watchService.take()
            } catch (_: ClosedWatchServiceException) {
                break
            }
            val dir = watchKeyToDir[key]
            if (dir == null) {
                key.reset()
                continue
            }
            var rewalkNeeded: Path? = null
            for (event in key.pollEvents()) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    logger.warn { "OVERFLOW for $dir – planning rewalk" }
                    rewalkNeeded = dir
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                val ev = event as WatchEvent<Path>
                val name = ev.context()
                val child = dir.resolve(name)
                when (kind) {
                    StandardWatchEventKinds.ENTRY_CREATE -> handleCreate(child)
                    StandardWatchEventKinds.ENTRY_DELETE -> handleDelete(child)
                    StandardWatchEventKinds.ENTRY_MODIFY -> { /* ignorieren */
                    }
                }
            }
            val valid = key.reset()
            if (!valid) {
                removeDirectoryRecursively(dir)
            }
            if (rewalkNeeded != null && Files.exists(rewalkNeeded)) {
                rewalkDirectory(rewalkNeeded)
            }
        }
    }

    private fun handleCreate(path: Path) {
        logger.debug { "handleCreate for $path" }
        try {
            if (Files.isDirectory(path)) {
                registerDirectoryWatcher(path)
                rewalkDirectory(path) // erfasst Dateien und Unterverzeichnisse
            } else {
                lock.write { dirContent.getOrPut(path.parent) { mutableSetOf() }.add(path) }
                markDirty()
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Couldn't handle CREATE $path" }
        }
    }

    private fun handleDelete(path: Path) {
        logger.debug { "handleDelete for $path" }
        try {
            if (Files.isDirectory(path)) {
                removeDirectoryRecursively(path)
            } else {
                lock.write { dirContent[path.parent]?.remove(path) }
                markDirty()
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Couldn't handle DELETE for $path" }
        }
    }

    private fun removeDirectoryRecursively(dir: Path) {
        var changed = false
        lock.write {
            val toRemove = dirContent.keys.filter { it == dir || it.startsWith(dir) }
            toRemove.forEach { d ->
                d.parent?.also { parent ->
                    if (dirContent[parent]?.remove(d) == true) changed = true
                }
                if (dirContent.remove(d) != null) changed = true
            }
        }
        watchKeyToDir.entries
            .filter { it.value == dir || it.value.startsWith(dir) }
            .forEach {
                it.key.cancel()
                watchKeyToDir.remove(it.key)
            }
        if (changed) markDirty()
    }

    private fun rewalkDirectory(start: Path) {
        if (!Files.isDirectory(start)) return
        val temp = HashMap<Path, MutableSet<Path>>()
        var changed = false
        try {
            Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    registerDirectoryWatcher(dir)
                    temp[dir] = mutableSetOf()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isDirectory) temp[file.parent]?.add(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    val newSet = temp[dir] ?: mutableSetOf()
                    lock.write {
                        val old = dirContent[dir]
                        if (old == null || old.size != newSet.size || !old.containsAll(newSet)) {
                            dirContent[dir] = newSet
                            changed = true
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (ex: IOException) {
            logger.warn(ex) { "Couldn't rewalk $start" }
        }
        if (changed) markDirty()
    }

    private fun registerDirectoryWatcher(dir: Path) {
        if (!Files.isDirectory(dir)) return
        if (watchKeyToDir.values.any { it == dir }) return
        val key = try {
            dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
        } catch (ex: Exception) {
            logger.warn(ex) { "Couldn't register watcher for $dir" }
            return
        }
        watchKeyToDir[key] = dir
    }

    private fun markDirty() {
        val now = System.currentTimeMillis()
        var launch = false
        synchronized(this) {
            if (firstDirtyAt == null) {
                firstDirtyAt = now
            }
            lastDirtyAt = now
            if (writeJob == null) {
                launch = true
            }
        }
        if (launch) scheduleWriteJob()
    }

    private fun scheduleWriteJob() {
        writeJob = applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                val (first, last) = synchronized(this@ImageDirectoryService) { firstDirtyAt to lastDirtyAt }
                if (first == null || last == null) break // nichts zu tun
                val now = System.currentTimeMillis()
                val target = minOf(last + 5_000, first + 10_000) // frühestens 5s nach letzter, spätestens 10s nach erster Änderung
                val delayMs = target - now
                if (delayMs > 0) {
                    delay(delayMs)
                    // Prüfen, ob neue Änderungen Zeitfenster verschoben haben
                    val still = synchronized(this@ImageDirectoryService) { firstDirtyAt == first && lastDirtyAt == last }
                    if (!still) continue // neu berechnen
                }
                // Schreiben
                writeIndexNow()
                break
            }
        }.also { job ->
            job.invokeOnCompletion {
                synchronized(this) { writeJob = null; firstDirtyAt = null; lastDirtyAt = null }
            }
        }
    }

    private fun snapshotIndex() =
        lock.read {
            dirContent
                .asSequence()
                .associate { (dir, files) -> root.relativize(dir).toString() to files.map { it.fileName.toString() }.sorted() }
        }

    private fun writeIndexNow() {
        val data = snapshotIndex()
        runCatching {
            Files.createDirectories(indexFile.parent)
            objectMapper.writeValue(indexFile.toFile(), data)
            logger.debug { "Wrote index.json (${data.size} directories)" }
        }.onFailure { e -> logger.error(e) { "Couldn't write index.json" } }
    }

    private fun isActiveAndOpen(): Boolean =
        !closed.get() && job.isActive

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            job.cancel()
            writeJob?.cancel()
            safeCloseWatchService()
        }
    }

    private fun safeCloseWatchService() {
        try {
            watchService.close()
        } catch (_: Exception) {
        }
    }
}
