package com.example.chuvaradar.data

/**
 * Definições geográficas dos distritos e subprefeituras de São Paulo
 * e municípios vizinhos abrangidos pelo radar IAG-USP (raio 36 km).
 */
data class DistrictDefinition(
    val id: String,
    val name: String,
    val zone: String,
    val lat: Double,
    val lon: Double,
    val radiusKm: Double = 3.2,
    val polygons: List<List<Pair<Double, Double>>> = emptyList()
)

object DistrictData {
    val defaultDistricts: List<DistrictDefinition> = listOf(
        // CENTRO
        DistrictDefinition("se", "Sé", "Centro", -23.5505, -46.6333, 2.0),
        DistrictDefinition("republica", "República", "Centro", -23.5432, -46.6425, 1.8),
        DistrictDefinition("bela_vista", "Bela Vista", "Centro", -23.5601, -46.6490, 2.0),
        DistrictDefinition("consolacao", "Consolação", "Centro", -23.5528, -46.6578, 2.0),
        DistrictDefinition("bom_retiro", "Bom Retiro", "Centro", -23.5270, -46.6385, 2.2),
        DistrictDefinition("cambuci", "Cambuci", "Centro", -23.5685, -46.6210, 2.2),
        DistrictDefinition("santa_cecilia", "Santa Cecília", "Centro", -23.5350, -46.6520, 2.0),

        // ZONA OESTE
        DistrictDefinition("paulista", "Av. Paulista / Jardins", "Zona Oeste", -23.5615, -46.6560, 2.5),
        DistrictDefinition("pinheiros", "Pinheiros", "Zona Oeste", -23.5670, -46.7000, 2.8),
        DistrictDefinition("lapa", "Lapa", "Zona Oeste", -23.5180, -46.7020, 3.0),
        DistrictDefinition("perdizes", "Perdizes", "Zona Oeste", -23.5380, -46.6740, 2.5),
        DistrictDefinition("vila_madalena", "Vila Madalena", "Zona Oeste", -23.5530, -46.6910, 2.2),
        DistrictDefinition("butanta", "Butantã / USP", "Zona Oeste", -23.5680, -46.7260, 3.5),
        DistrictDefinition("jaguare", "Jaguaré", "Zona Oeste", -23.5420, -46.7450, 3.0),
        DistrictDefinition("rio_pequeno", "Rio Pequeno", "Zona Oeste", -23.5780, -46.7580, 3.2),

        // ZONA SUL
        DistrictDefinition("morumbi", "Morumbi", "Zona Sul", -23.6020, -46.7150, 3.5),

        // ZONA NORTE
        DistrictDefinition("santana", "Santana", "Zona Norte", -23.5000, -46.6300, 3.0),
        DistrictDefinition("tucuruvi", "Tucuruvi", "Zona Norte", -23.4780, -46.6020, 3.0),
        DistrictDefinition("casa_verde", "Casa Verde", "Zona Norte", -23.5080, -46.6620, 2.8),
        DistrictDefinition("freguesia_o", "Freguesia do Ó", "Zona Norte", -23.4980, -46.6980, 3.2),
        DistrictDefinition("vila_maria", "Vila Maria", "Zona Norte", -23.5120, -46.5920, 3.0),
        DistrictDefinition("pirituba", "Pirituba", "Zona Norte", -23.4830, -46.7320, 3.5),
        DistrictDefinition("brasilandia", "Brasilândia", "Zona Norte", -23.4680, -46.6850, 3.5),
        DistrictDefinition("jacana", "Jaçanã", "Zona Norte", -23.4650, -46.5820, 3.2),

        // ZONA LESTE
        DistrictDefinition("mooca", "Mooca", "Zona Leste", -23.5580, -46.5980, 2.8),
        DistrictDefinition("tatuape", "Tatuapé", "Zona Leste", -23.5410, -46.5740, 3.0),
        DistrictDefinition("belem", "Belém", "Zona Leste", -23.5390, -46.5950, 2.5),
        DistrictDefinition("penha", "Penha", "Zona Leste", -23.5250, -46.5450, 3.2),
        DistrictDefinition("vila_formosa", "Vila Formosa", "Zona Leste", -23.5650, -46.5500, 3.0),
        DistrictDefinition("itaquera", "Itaquera", "Zona Leste", -23.5350, -46.4550, 4.0),
        DistrictDefinition("vila_prudente", "Vila Prudente", "Zona Leste", -23.5850, -46.5820, 3.0),
        DistrictDefinition("sapopemba", "Sapopemba", "Zona Leste", -23.6080, -46.5120, 3.5),
        DistrictDefinition("sao_mateus", "São Mateus", "Zona Leste", -23.6120, -46.4750, 3.8),
        DistrictDefinition("sao_miguel", "São Miguel Paulista", "Zona Leste", -23.4980, -46.4420, 3.8),
        DistrictDefinition("aricanduva", "Aricanduva", "Zona Leste", -23.5780, -46.5180, 3.2),

        // ZONA SUL
        DistrictDefinition("vila_mariana", "Vila Mariana", "Zona Sul", -23.5850, -46.6380, 2.8),
        DistrictDefinition("moema", "Moema / Ibirapuera", "Zona Sul", -23.6010, -46.6620, 2.8),
        DistrictDefinition("saude", "Saúde", "Zona Sul", -23.6180, -46.6390, 2.8),
        DistrictDefinition("campo_belo", "Campo Belo / Congonhas", "Zona Sul", -23.6260, -46.6710, 3.0),
        DistrictDefinition("itaim_bibi", "Itaim Bibi", "Zona Sul", -23.5860, -46.6810, 2.6),
        DistrictDefinition("santo_amaro", "Santo Amaro", "Zona Sul", -23.6520, -46.7050, 3.5),
        DistrictDefinition("jabaquara", "Jabaquara", "Zona Sul", -23.6480, -46.6430, 3.0),
        DistrictDefinition("ipiranga", "Ipiranga", "Zona Sul", -23.5930, -46.6060, 3.0),
        DistrictDefinition("campo_limpo", "Campo Limpo", "Zona Sul", -23.6420, -46.7550, 3.5),
        DistrictDefinition("socorro", "Socorro", "Zona Sul", -23.6920, -46.7020, 3.5),
        DistrictDefinition("cidade_ademar", "Cidade Ademar", "Zona Sul", -23.6740, -46.6620, 3.5),
        DistrictDefinition("m_boi_mirim", "M'Boi Mirim", "Zona Sul", -23.6850, -46.7550, 4.0),

        // GRANDE SP (Cobertura do Radar 36km)
        DistrictDefinition("osasco", "Osasco", "Grande SP", -23.5320, -46.7910, 3.8),
        DistrictDefinition("santo_andre", "Santo André", "Grande SP", -23.6580, -46.5320, 3.8),
        DistrictDefinition("sao_bernardo", "São Bernardo do Campo", "Grande SP", -23.6910, -46.5650, 4.2),
        DistrictDefinition("sao_caetano", "São Caetano do Sul", "Grande SP", -23.6220, -46.5550, 2.5),
        DistrictDefinition("diadema", "Diadema", "Grande SP", -23.6860, -46.6230, 3.0),
        DistrictDefinition("guarulhos", "Guarulhos (Aeroporto)", "Grande SP", -23.4350, -46.4750, 4.5),
        DistrictDefinition("taboao_serra", "Taboão da Serra", "Grande SP", -23.6080, -46.7580, 3.0),
        DistrictDefinition("barueri", "Barueri / Alphaville", "Grande SP", -23.5110, -46.8750, 4.0)
    )

    val zones = listOf("Todos", "Centro", "Zona Norte", "Zona Sul", "Zona Leste", "Zona Oeste")

    val quickAddressPresets = listOf(
        UserSavedAddress("p1", "Av. Paulista", "Av. Paulista, 1000, Bela Vista, São Paulo", -23.5615, -46.6560),
        UserSavedAddress("p2", "Centro / Sé", "Praça da Sé, Centro, São Paulo", -23.5505, -46.6333),
        UserSavedAddress("p3", "Pinheiros", "Rua dos Pinheiros, Pinheiros, São Paulo", -23.5670, -46.7000),
        UserSavedAddress("p4", "Itaquera", "Arena Corinthians, Itaquera, São Paulo", -23.5350, -46.4550),
        UserSavedAddress("p5", "Santana", "Rua Voluntários da Pátria, Santana, São Paulo", -23.5000, -46.6300),
        UserSavedAddress("p6", "Santo Amaro", "Largo Treze de Maio, Santo Amaro, São Paulo", -23.6520, -46.7050),
        UserSavedAddress("p7", "Morumbi", "Estádio do Morumbi, São Paulo", -23.6000, -46.7200),
        UserSavedAddress("p8", "USP / Butantã", "Cidade Universitária, USP, São Paulo", -23.5587, -46.7314),
        UserSavedAddress("p9", "ABC / Sto André", "Centro, Santo André, SP", -23.6580, -46.5320),
        UserSavedAddress("p10", "Guarulhos", "Centro, Guarulhos, SP", -23.4628, -46.5333)
    )
}
