package com.example.chuvaradar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray

/**
 * Provedor de dados de mapas OpenStreetMap (OSM) e Shapefile oficial dos distritos de São Paulo.
 * Fornece a camada de base cartográfica real e os polígonos vetoriais dos 96 distritos da capital.
 */
object OpenStreetMapProvider {
    private const val TAG = "OpenStreetMapProvider"

    private var cachedDarkBasemap: Bitmap? = null
    private var cachedStandardBasemap: Bitmap? = null
    private var cachedDistricts: List<DistrictDefinition>? = null

    /**
     * Retorna a imagem do mapa base OpenStreetMap em estilo Dark Matter (CartoDB / OSM).
     * Cobre exatamente a extensão do radar IAG-USP de 36km (1000x1000 px).
     */
    fun getDarkBasemap(context: Context): Bitmap? {
        if (cachedDarkBasemap != null && !cachedDarkBasemap!!.isRecycled) {
            return cachedDarkBasemap
        }
        return try {
            context.assets.open("osm_dark_basemap.png").use { stream ->
                BitmapFactory.decodeStream(stream).also {
                    cachedDarkBasemap = it
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar mapa OSM Dark: ${e.message}", e)
            null
        }
    }

    /**
     * Retorna a imagem do mapa base OpenStreetMap padrão (colorido oficial).
     */
    fun getStandardBasemap(context: Context): Bitmap? {
        if (cachedStandardBasemap != null && !cachedStandardBasemap!!.isRecycled) {
            return cachedStandardBasemap
        }
        return try {
            context.assets.open("osm_standard_basemap.jpg").use { stream ->
                BitmapFactory.decodeStream(stream).also {
                    cachedStandardBasemap = it
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar mapa OSM Padrão: ${e.message}", e)
            null
        }
    }

    /**
     * Carrega a lista completa dos 96 distritos oficiais de São Paulo com suas geometrias
     * reais (shapefile/GeoJSON convertido da Prefeitura de SP / GeoSampa).
     */
    fun getDistricts(context: Context): List<DistrictDefinition> {
        if (cachedDistricts != null) {
            return cachedDistricts!!
        }

        val loaded = try {
            val jsonStr = context.assets.open("sp_districts.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DistrictDefinition>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "dist_$i")
                val name = obj.optString("name", "Distrito $i")
                val zone = obj.optString("zone", "Centro")
                val lat = obj.optDouble("lat", -23.55)
                val lon = obj.optDouble("lon", -46.63)

                val polyArr = obj.optJSONArray("polygons")
                val rings = mutableListOf<List<Pair<Double, Double>>>()

                if (polyArr != null) {
                    for (r in 0 until polyArr.length()) {
                        val ringArr = polyArr.optJSONArray(r) ?: continue
                        val ring = mutableListOf<Pair<Double, Double>>()
                        for (p in 0 until ringArr.length()) {
                            val ptArr = ringArr.optJSONArray(p) ?: continue
                            if (ptArr.length() >= 2) {
                                ring.add(Pair(ptArr.getDouble(0), ptArr.getDouble(1)))
                            }
                        }
                        if (ring.isNotEmpty()) {
                            rings.add(ring)
                        }
                    }
                }

                list.add(
                    DistrictDefinition(
                        id = id,
                        name = name,
                        zone = zone,
                        lat = lat,
                        lon = lon,
                        radiusKm = 2.8,
                        polygons = rings
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler sp_districts.json: ${e.message}", e)
            DistrictData.defaultDistricts
        }

        cachedDistricts = loaded
        return loaded
    }

    /**
     * Algoritmo de teste de inclusão ponto-em-polígono (Ray-casting)
     * para identificar se uma coordenada (lat, lon) está dentro de um anel poligonal.
     */
    fun isPointInPolygon(lat: Double, lon: Double, polygonRing: List<Pair<Double, Double>>): Boolean {
        if (polygonRing.size < 3) return false
        var inside = false
        var j = polygonRing.size - 1

        for (i in polygonRing.indices) {
            val (yi, xi) = polygonRing[i] // lat, lon
            val (yj, xj) = polygonRing[j]

            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)

            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Encontra qual distrito de São Paulo contém o ponto (lat, lon).
     */
    fun findDistrictAt(lat: Double, lon: Double, districts: List<DistrictRainStatus>): DistrictRainStatus? {
        // 1. Testa os polígonos reais dos distritos
        for (dist in districts) {
            for (ring in dist.polygons) {
                if (isPointInPolygon(lat, lon, ring)) {
                    return dist
                }
            }
        }
        // 2. Se tocar muito perto da fronteira, seleciona pelo centroide mais próximo
        return districts.minByOrNull { dist ->
            val dLat = dist.centroidLat - lat
            val dLon = dist.centroidLon - lon
            dLat * dLat + dLon * dLon
        }?.takeIf { dist ->
            val dLat = dist.centroidLat - lat
            val dLon = dist.centroidLon - lon
            (dLat * dLat + dLon * dLon) < 0.0025 // ~5km
        }
    }
}
