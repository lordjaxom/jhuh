package de.hinundhergestellt.jhuh.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.HuhProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val objectMapper = jacksonObjectMapper()
    private val watchService: WatchService = root.fileSystem.newWatchService()
    private val watchKeyToDir = ConcurrentHashMap<WatchKey, Path>()
    private val lock = ReentrantReadWriteLock()
    private val initialized = CompletableFuture<Unit>()
    private val closed = AtomicBoolean(false)
    private val job: Job

    private val dirContent = loadIndexJsonIfPresent()

    // Debounce / Throttle (>=5s nach letzter Änderung, <=10s nach erster) für index.json
    @Volatile
    private var firstDirtyAt: Long? = null

    @Volatile
    private var lastDirtyAt: Long? = null

    @Volatile
    private var writeJob: Job? = null

    init {
        require(Files.isDirectory(root)) { "path $root must be a directory" }

        job = applicationScope.launch {
            try {
                walkDirectory(root)
                writeIndexNow()
                initialized.complete(Unit)
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
        val absolute = if (path.isAbsolute) path.normalize() else root.resolve(path).normalize()
        require(absolute == root || absolute.startsWith(root)) { "path must be relative or below imageDirectory" }

        initialized.join() // ensure initial load
        return lock.read { dirContent[absolute]?.map { absolute.resolve(it) } ?: listOf() }
    }

    fun listDirectoryEntries(path: Path, glob: String): List<Path> {
        val matcher = root.fileSystem.getPathMatcher("glob:$glob")
        return listDirectoryEntries(path).filter { matcher.matches(it.fileName) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            job.cancel()
            writeJob?.cancel()
            safeCloseWatchService()
        }
    }

    private fun loadIndexJsonIfPresent() =
        runCatching {
            val indexFile = root.resolve(INDEX_JSON)
            val snapshot: Map<String, List<String>> = objectMapper.readValue(indexFile.toFile())
            if (snapshot.isNotEmpty()) {
                initialized.complete(Unit)
                logger.info { "Preliminary index with ${snapshot.size} directories loaded from $indexFile." }
            }
            snapshot.asSequence().associate { (dir, files) -> root.resolve(dir) to files.toMutableSet() }.toMutableMap()
        }.getOrElse { e ->
            logger.error(e) { "Couldn't load index.json - rebuilding tree from scratch" }
            mutableMapOf()
        }

    private fun walkDirectory(start: Path) {
        logger.info { "Start scanning directory $start" }

        val visitedDirs = mutableSetOf<Path>()
        val newContent = mutableMapOf<Path, MutableSet<String>>()
        Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                registerDirectoryWatcher(dir)
                visitedDirs.add(dir)
                newContent[dir] = mutableSetOf()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isDirectory) newContent[file.parent]!!.add(file.fileName.toString())
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                lock.write { dirContent[dir] = newContent[dir]!! }
                return FileVisitResult.CONTINUE
            }
        })
        lock.write {
            dirContent.keys
                .filter { it.startsWith(start) && it !in newContent }
                .forEach { dirContent.remove(it) }
        }

        logger.info { "Scanning directory $start finished" }
    }

    private fun eventLoop() {
        logger.info { "Starting file system watch service event loop" }

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
        logger.debug { "Handling CREATE event for $path" }
        try {
            if (Files.isDirectory(path)) {
                registerDirectoryWatcher(path)
                rewalkDirectory(path)
            } else {
                lock.write { dirContent.getOrPut(path.parent) { mutableSetOf() }.add(path.fileName.toString()) }
                markDirty()
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Couldn't handle CREATE event for $path" }
        }
    }

    private fun handleDelete(path: Path) {
        logger.debug { "Handling DELETE event for $path" }
        try {
            if (Files.isDirectory(path)) {
                removeDirectoryRecursively(path)
            } else {
                lock.write { dirContent[path.parent]?.remove(path.fileName.toString()) }
                markDirty()
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Couldn't handle DELETE event for $path" }
        }
    }

    private fun removeDirectoryRecursively(dir: Path) {
        lock.write {
            dirContent.keys
                .filter { it == dir || it.startsWith(dir) }
                .forEach { toRemove ->
                    toRemove.parent?.also { dirContent[it]?.remove(toRemove.fileName.toString()) }
                    dirContent.remove(toRemove)
                }
        }
        watchKeyToDir.entries
            .filter { it.value == dir || it.value.startsWith(dir) }
            .forEach { it.key.cancel(); watchKeyToDir.remove(it.key) }
        markDirty()
    }

    private fun rewalkDirectory(start: Path) {
        if (!Files.isDirectory(start)) return
        try {
            walkDirectory(start)
        } catch (ex: IOException) {
            logger.error(ex) { "Couldn't rewalk directory $start" }
        }
        markDirty()
    }

    private fun registerDirectoryWatcher(dir: Path) {
        if (watchKeyToDir.values.any { it == dir }) return
        val key = try {
            dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
        } catch (ex: Exception) {
            logger.error(ex) { "Couldn't register watcher for $dir" }
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
        writeJob = applicationScope.launch {
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
                .associate { (dir, files) -> root.relativize(dir).toString() to files.toList() }
        }

    private fun writeIndexNow() {
        try {
            val indexFile = root.resolve(INDEX_JSON)
            val snapshot = snapshotIndex()
            objectMapper.writeValue(indexFile.toFile(), snapshot)
            logger.info { "Wrote ${snapshot.size} directories to $indexFile" }
        } catch (e: Exception) {
            logger.error(e) { "Couldn't write index.json" }
        }
    }

    private fun isActiveAndOpen(): Boolean =
        !closed.get() && job.isActive

    private fun safeCloseWatchService() {
        runCatching { watchService.close() }
    }
}
