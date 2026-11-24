package com.example

import android.graphics.Color
import com.example.chuvaradar.data.RainIntensity
import com.example.chuvaradar.engine.RadarAnalysisEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RadarAnalysisEngineTest {

    @Test
    fun testUspRadarCoordinatesMapping() {
        // USP IAG Pelletron radar coordinates: ~ -23.5587, -46.7314
        val pixel = RadarAnalysisEngine.geoToPixel(-23.5587, -46.7314)
        assertNotNull(pixel)
        val (px, py) = pixel!!
        // Should be near center (500, 500)
        assertTrue("px=$px should be between 480 and 530", px in 480..530)
        assertTrue("py=$py should be between 470 and 520", py in 470..520)
    }

    @Test
    fun testRadarColorClassification() {
        // 1. Transparent
        val transparent = Color.argb(0, 255, 255, 255)
        assertEquals(RainIntensity.NONE, RadarAnalysisEngine.classifyColor(transparent))

        // 2. Grayscale (labels / rings)
        val gray = Color.argb(255, 200, 200, 200)
        assertEquals(RainIntensity.NONE, RadarAnalysisEngine.classifyColor(gray))

        // 3. Blue (garoa / fraca)
        val blue = Color.argb(255, 0, 88, 241)
        assertEquals(RainIntensity.VERY_LIGHT, RadarAnalysisEngine.classifyColor(blue))

        // 4. Green (fraca)
        val green = Color.argb(255, 0, 217, 0)
        assertEquals(RainIntensity.LIGHT, RadarAnalysisEngine.classifyColor(green))

        // 5. Yellow / Orange (moderada)
        val yellow = Color.argb(255, 255, 218, 23)
        assertEquals(RainIntensity.MODERATE, RadarAnalysisEngine.classifyColor(yellow))

        // 6. Red (forte)
        val red = Color.argb(255, 255, 0, 0)
        assertEquals(RainIntensity.HEAVY, RadarAnalysisEngine.classifyColor(red))

        // 7. Magenta / Purple (torrencial / granizo)
        val purple = Color.argb(255, 255, 0, 139)
        assertEquals(RainIntensity.INTENSE, RadarAnalysisEngine.classifyColor(purple))
    }

    @Test
    fun testInverseGeoTransform() {
        val lat = -23.5615
        val lon = -46.6560
        val px = RadarAnalysisEngine.geoToPixel(lat, lon)
        assertNotNull(px)

        val (backLat, backLon) = RadarAnalysisEngine.pixelToGeo(px!!.first, px.second)
        assertEquals(lat, backLat, 0.01)
        assertEquals(lon, backLon, 0.01)
    }

    @Test
    fun testOutdatedNowcastSuppression() {
        val bmp = android.graphics.Bitmap.createBitmap(1000, 1000, android.graphics.Bitmap.Config.ARGB_8888)
        // Fill user area with rain color (red)
        val userPx = RadarAnalysisEngine.geoToPixel(-23.5615, -46.6560)!!
        for (dy in -20..20) {
            for (dx in -20..20) {
                bmp.setPixel(userPx.first + dx, userPx.second + dy, Color.RED)
            }
        }

        val frame = com.example.chuvaradar.data.RadarScanFrame(
            frameIndex = 10,
            url = "http://example.com/test.png",
            timestampText = "17:30",
            timestampMillis = System.currentTimeMillis() - 7200000L, // 2 horas atrás
            bitmap = bmp,
            isOutdated = true,
            fullDateTimeText = "07/11/2026 às 17:30",
            dataAgeDescription = "há 2 horas"
        )

        val nowcast = RadarAnalysisEngine.computeNowcast(
            frames = listOf(frame),
            userLat = -23.5615,
            userLon = -46.6560,
            addressLabel = "Av. Paulista",
            isOutdated = true,
            dataAgeMinutes = 120L,
            dataAgeDescription = "há 2 horas",
            fullScanDateTimeText = "07/11/2026 às 17:30"
        )

        // Verificações críticas:
        assertTrue("Nowcast deve indicar dados desatualizados", nowcast.isDataOutdated)
        assertFalse("NÃO pode indicar que está chovendo agora se os dados forem antigos", nowcast.isRainingNow)
        assertEquals(null, nowcast.estimatedArrivalMinutes)
        assertTrue(nowcast.statusHeadline.contains("Desatualizado"))
    }
}
