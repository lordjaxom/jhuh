package de.hinundhergestellt.jhuh.tools

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object MediaImageTools {

    /**
     * Lädt ein Bild (jpg/png) von einer Datei.
     */
    fun load(path: Path): BufferedImage =
        Files.newInputStream(path).use { ImageIO.read(it) ?: error("Unsupported/empty image: $path") }

    /**
     * Lädt ein Bild (jpg/png) aus einem InputStream (Stream wird NICHT geschlossen).
     */
    fun load(input: InputStream): BufferedImage =
        ImageIO.read(input) ?: error("Unsupported/empty image from stream")

    /**
     * Berechnet die durchschnittliche Farbe eines Bildausschnitts in Prozent (0..100).
     */
    fun averageColorPercent(
        img: BufferedImage,
        rect: RectPct = RectPct.EVERYTHING,
        ignoreFullyTransparent: Boolean = true,
        ignoreWhite: Boolean = false
    ): Color {
        val (start, end) = rect
        val (left, top) = start
        val (right, bottom) = end

        val w = img.width
        val h = img.height
        require(w > 0 && h > 0) { "Empty image" }

        val x0 = clampInt(floor(w * (left / 100.0)).toInt(), 0, w - 1)
        val y0 = clampInt(floor(h * (top / 100.0)).toInt(), 0, h - 1)
        val x1 = clampInt(ceil(w * (right / 100.0)).toInt() - 1, 0, w - 1)
        val y1 = clampInt(ceil(h * (bottom / 100.0)).toInt() - 1, 0, h - 1)

        require(x1 >= x0 && y1 >= y0) {
            "Invalid crop: right/bottom must be greater than left/top and inside image"
        }

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L

        val rowWidth = x1 - x0 + 1
        val rowBuf = IntArray(rowWidth)

        for (y in y0..y1) {
            img.getRGB(x0, y, rowWidth, 1, rowBuf, 0, rowWidth)
            for (px in rowBuf) {
                val a = (px ushr 24) and 0xFF
                if (!ignoreFullyTransparent || a != 0) {
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = (px) and 0xFF
                    if (!ignoreWhite || r != 255 || g != 255 || b != 255) {
                        rSum += r
                        gSum += g
                        bSum += b
                        count++
                    }
                }
            }
        }

        if (count == 0L) {
            // Fallback: wenn alles transparent war und wir ignoriert haben, nimm Mittelwert inkl. Transparenz
            for (y in y0..y1) {
                img.getRGB(x0, y, rowWidth, 1, rowBuf, 0, rowWidth)
                for (px in rowBuf) {
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = (px) and 0xFF
                    rSum += r
                    gSum += g
                    bSum += b
                }
            }
            count = (rowWidth.toLong() * (y1 - y0 + 1))
        }

        val rAvg = (rSum / count).toInt()
        val gAvg = (gSum / count).toInt()
        val bAvg = (bSum / count).toInt()
        return Color(rAvg, gAvg, bAvg)
    }

    /**
     * Komfort: Direkt aus Datei mit Prozenten.
     */
    fun averageColorPercent(
        path: Path,
        rect: RectPct = RectPct.EVERYTHING,
        ignoreFullyTransparent: Boolean = true,
        ignoreWhite: Boolean = false
    ): Color = averageColorPercent(load(path), rect, ignoreFullyTransparent, ignoreWhite)

    /**
     * Extrahiert einen Bildausschnitt (RectPct in Prozent), skaliert ihn auf 100x100 Pixel
     * und speichert ihn als PNG.
     */
    fun extractColorSwatch(img: BufferedImage, rect: RectPct, output: Path) {
        val (start, end) = rect
        val (left, top) = start
        val (right, bottom) = end

        val w = img.width
        val h = img.height

        val x0 = clampInt(floor(w * (left / 100.0)).toInt(), 0, w - 1)
        val y0 = clampInt(floor(h * (top / 100.0)).toInt(), 0, h - 1)
        val x1 = clampInt(ceil(w * (right / 100.0)).toInt() - 1, 0, w - 1)
        val y1 = clampInt(ceil(h * (bottom / 100.0)).toInt() - 1, 0, h - 1)

        require(x1 >= x0 && y1 >= y0) {
            "Invalid crop: right/bottom must be greater than left/top and inside image"
        }

        val width = x1 - x0 + 1
        val height = y1 - y0 + 1

        // Ausschnitt extrahieren
        val sub = img.getSubimage(x0, y0, width, height)

        // auf 100x100 skalieren
        val scaled = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val g2 = scaled.createGraphics()
        g2.drawImage(sub, 0, 0, 100, 100, null)
        g2.dispose()

        // als PNG speichern
        Files.newOutputStream(output).use { out ->
            ImageIO.write(scaled, "png", out)
        }
    }

    fun extractColorSwatch(input: Path, rect: RectPct, output: Path) = extractColorSwatch(load(input), rect, output)

    private fun clampInt(v: Int, minV: Int, maxV: Int): Int = max(minV, min(v, maxV))
}

/**
 * Hex-Helfer (#RRGGBB).
 */
fun Color.toHex(): String = "#%02x%02x%02x".format(this.red, this.green, this.blue)

class PointPct(
    val x: Double,
    val y: Double
) {
    init {
        require(x in 0.0..100.0 && y in 0.0..100.0) { "Percents must be in 0..100" }
    }

    operator fun component1() = x
    operator fun component2() = y

    companion object {
        val CENTER = PointPct(50.0, 50.0)
    }
}

class RectPct(
    val start: PointPct,
    val end: PointPct
) {
    constructor(left: Double, top: Double, right: Double, bottom: Double) : this(PointPct(left, top), PointPct(right, bottom))

    operator fun component1() = start
    operator fun component2() = end

    companion object {
        val EVERYTHING = RectPct(0.0, 0.0, 100.0, 100.0)
        val CENTER_33 = RectPct(33.3, 33.3, 66.6, 66.6)
        val CENTER_20 = RectPct(40.0, 40.0, 60.0, 60.0)
        val CENTER_40 = RectPct(30.0, 30.0, 70.0, 70.0)
        val CENTER_60 = RectPct(20.0, 20.0, 80.0, 80.0)
    }
}