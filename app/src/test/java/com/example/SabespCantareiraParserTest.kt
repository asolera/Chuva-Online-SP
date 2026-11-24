package com.example

import com.example.chuvaradar.data.CantareiraOperationalBand
import com.example.chuvaradar.data.SabespCantareiraParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SabespCantareiraParserTest {

    @Test
    fun `parses Sabesp API v4 daily summary for Sistema Cantareira (id 64)`() {
        val sampleV4Json = """
            {
              "data": [
                {
                  "idSistema": 65,
                  "nome": "Alto Tietê",
                  "volumeUtilArmazenadoPorcentagem": 51.2
                },
                {
                  "idSistema": 64,
                  "nome": "Cantareira",
                  "data": "2026-11-23",
                  "volumeUtilArmazenadoPorcentagem": 47.8,
                  "volumeUtilArmazenadoHm3": 469.5,
                  "variacaoVolumeUtil": 0.2,
                  "chuva": 3.4,
                  "chuvaAcumuladaNoMes": 84.2,
                  "chuvaMediaHistorica": 135.0
                }
              ]
            }
        """.trimIndent()

        val point = SabespCantareiraParser.parseDailyResponse(sampleV4Json, "2026-11-23")
        assertNotNull(point)
        assertEquals("2026-11-23", point!!.dateIso)
        assertEquals("23/11", point.shortDateLabel)
        assertEquals("23/11/2026", point.fullDateLabel)
        assertEquals(47.8, point.volumePercent, 0.001)
        assertEquals(469.5, point.volumeHm3!!, 0.001)
        assertEquals(0.2, point.dailyVariationPercent!!, 0.001)
        assertEquals(3.4, point.rainMm!!, 0.001)
        assertEquals(84.2, point.monthlyRainMm!!, 0.001)
        assertEquals(135.0, point.historicalAvgRainMm!!, 0.001)
    }

    @Test
    fun `parses Sabesp classic ResumoSistemas payload with pt-BR comma decimals`() {
        val sampleLegacyJson = """
            {
              "ReturnObj": {
                "DataString": "22/11/2026",
                "sistemas": [
                  {
                    "SistemaId": 0,
                    "Nome": "Cantareira",
                    "VolumePorcentagemAR": "47,6",
                    "VolumeOperacional": 467.2,
                    "VolumeVariacaoNum": -0.1,
                    "PrecDia": "0,8",
                    "PrecMensal": "80,8",
                    "PrecHist": "135,0"
                  },
                  {
                    "SistemaId": 1,
                    "Nome": "Alto Tietê",
                    "VolumePorcentagemAR": "51,0"
                  }
                ]
              }
            }
        """.trimIndent()

        val point = SabespCantareiraParser.parseDailyResponse(sampleLegacyJson, "2026-11-22")
        assertNotNull(point)
        assertEquals("2026-11-22", point!!.dateIso)
        assertEquals(47.6, point.volumePercent, 0.001)
        assertEquals(-0.1, point.dailyVariationPercent!!, 0.001)
        assertEquals(0.8, point.rainMm!!, 0.001)
        assertEquals(80.8, point.monthlyRainMm!!, 0.001)
        assertEquals(135.0, point.historicalAvgRainMm!!, 0.001)
    }

    @Test
    fun `classifies ANA DAEE operational bands accurately`() {
        assertEquals(CantareiraOperationalBand.NORMAL, CantareiraOperationalBand.fromVolumePercent(64.5))
        assertEquals(CantareiraOperationalBand.ATTENTION, CantareiraOperationalBand.fromVolumePercent(48.2))
        assertEquals(CantareiraOperationalBand.ALERT, CantareiraOperationalBand.fromVolumePercent(34.0))
        assertEquals(CantareiraOperationalBand.RESTRICTION, CantareiraOperationalBand.fromVolumePercent(25.1))
        assertEquals(CantareiraOperationalBand.SPECIAL, CantareiraOperationalBand.fromVolumePercent(15.0))
    }

    @Test
    fun `serializes and deserializes cached snapshot losslessly`() {
        val sampleV4Json = """
            {
              "data": [
                {
                  "idSistema": 64,
                  "volumeUtilArmazenadoPorcentagem": 52.3,
                  "chuva": 1.2
                }
              ]
            }
        """.trimIndent()

        val p1 = SabespCantareiraParser.parseDailyResponse(sampleV4Json, "2026-11-20")!!
        val snapshotJson = SabespCantareiraParser.serializeSnapshot(listOf(p1), 1700000000000L)
        val restored = SabespCantareiraParser.deserializeSnapshot(snapshotJson)

        assertNotNull(restored)
        assertEquals(1700000000000L, restored!!.first)
        assertEquals(1, restored.second.size)
        assertEquals(52.3, restored.second[0].volumePercent, 0.001)
        assertEquals(1.2, restored.second[0].rainMm!!, 0.001)
    }
}
