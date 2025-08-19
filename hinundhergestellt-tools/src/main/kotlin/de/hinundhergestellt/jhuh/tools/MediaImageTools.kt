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
     *
     * @param leftPct   linke Kante in Prozent (0..100)
     * @param topPct    obere Kante in Prozent (0..100)
     * @param rightPct  rechte Kante in Prozent (0..100)
     * @param bottomPct untere Kante in Prozent (0..100)
     * @param ignoreFullyTransparent  PNG: voll transparente Pixel ignorieren (Alpha==0)
     * @return Color (sRGB) + Hex via .toHex()
     */
    fun averageColorPercent(
        img: BufferedImage,
        leftPct: Double = 0.0,
        topPct: Double = 0.0,
        rightPct: Double = 100.0,
        bottomPct: Double = 100.0,
        ignoreFullyTransparent: Boolean = true
    ): Color {
        require(leftPct in 0.0..100.0 && topPct in 0.0..100.0 && rightPct in 0.0..100.0 && bottomPct in 0.0..100.0) {
            "Percents must be in 0..100"
        }
        val w = img.width
        val h = img.height
        require(w > 0 && h > 0) { "Empty image" }

        val x0 = clampInt(floor(w * (leftPct / 100.0)).toInt(), 0, w - 1)
        val y0 = clampInt(floor(h * (topPct / 100.0)).toInt(), 0, h - 1)
        val x1 = clampInt(ceil(w * (rightPct / 100.0)).toInt() - 1, 0, w - 1)
        val y1 = clampInt(ceil(h * (bottomPct / 100.0)).toInt() - 1, 0, h - 1)

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
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
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
        leftPct: Double = 0.0,
        topPct: Double = 0.0,
        rightPct: Double = 100.0,
        bottomPct: Double = 100.0,
        ignoreFullyTransparent: Boolean = true
    ): Color = averageColorPercent(load(path), leftPct, topPct, rightPct, bottomPct, ignoreFullyTransparent)

    private fun clampInt(v: Int, minV: Int, maxV: Int): Int = max(minV, min(v, maxV))
}

/**
 * Hex-Helfer (#RRGGBB).
 */
fun Color.toHex(): String = "#%02x%02x%02x".format(this.red, this.green, this.blue)