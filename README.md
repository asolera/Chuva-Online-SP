# Chuva Radar SP

Aplicativo Android desenvolvido em **Kotlin** e **Jetpack Compose** para monitoramento de chuva em tempo real na Região Metropolitana de São Paulo, previsão meteorológica horária e de 8 dias, além do acompanhamento diário do volume armazenado do **Sistema Cantareira**.

---

## Funcionalidades Principais

- **Nowcasting em Tempo Real (Próximos 60 min):**
  - Baixa os frames reais do **Radar Meteorológico IAG-USP (raio de 36 km)**, aplica remoção de fundo, despeckle e supressão multitemporal de *ground clutter* (ecos estáticos).
  - Calcula o vetor de deslocamento das nuvens (velocidade em km/h e direção) e projeta a chegada e a intensidade da chuva no endereço escolhido em intervalos de `+0m`, `+15m`, `+30m`, `+45m` e `+60m`.
  - Extrai a data/hora real da captura com **OCR (ML Kit)** / legenda HTML / cabeçalhos HTTP e exibe alerta automático caso o servidor do radar esteja desatualizado (> 1 hora), pausando notificações falsas.
- **Radar Ao Vivo & Mapa de Distritos de São Paulo:**
  - Visualizador interativo com *zoom/pan*, reprodução em loop, controle quadro a quadro, pino de localização do usuário, anéis de distância e rótulos de bairros sobre mapa base OpenStreetMap.
  - Aba **Distritos SP** com polígonos dos distritos da capital (`sp_districts.json`), status de chuva por região e seleção rápida como endereço ativo.
- **Card "Vai chover hoje?" (Sim / Talvez / Não):**
  - Resposta direta e visual baseada nas horas restantes do dia atual na API **Open-Meteo** (probabilidade máxima e acumulado em mm) integrada à detecção de chuva ativa no radar ao vivo.
- **Previsão do Tempo (Gráfico Horário de Chuva + 8 Dias):**
  - Gráfico interativo em degraus de probabilidade de chuva (`00:00` a `21:00`) com toque/arraste para inspecionar hora, probabilidade (`%`), volume (`mm`), temperatura e condição.
  - Faixa horizontal dos próximos **8 dias** com ícones meteorológicos desenhados em Canvas, temperaturas máxima/mínima e seleção do dia para atualizar o gráfico horário acima.
  - Atualiza automaticamente de acordo com a latitude/longitude do endereço ativo (GPS, busca por endereço ou predefinições).
- **Monitoramento do Sistema Cantareira (SABESP):**
  - Consulta a série diária dos últimos **30 dias** no portal oficial **Mananciais da SABESP** (Local 64).
  - Exibe volume útil atual (`%`), faixa operacional ANA/DAEE (Normal, Atenção, Alerta, Restrição, Especial), variação diária/30d, chuva no dia/mês e gráfico interativo com cache em disco.
- **Widget na Tela Inicial & Alertas em Segundo Plano:**
  - App Widget (`ChuvaRadarWidgetProvider`) com resumo rápido de chuva e botão de atualização manual.
  - Verificação periódica em segundo plano (`RadarAlarmReceiver`) e notificações de aproximação de chuva (`RadarNotificationHelper`).

---

## Fontes de Dados

| Módulo | Fonte | Observação |
| :--- | :--- | :--- |
| Radar de Chuva (36 km) | [IAG-USP Chuva Online / Starnet](https://www.chuvaonline.iag.usp.br/animacao_36km.php) | Imagens de loop do radar Pelletron (USP) |
| Previsão Horária & 8 Dias | [Open-Meteo Weather API](https://open-meteo.com/) | Aberta e gratuita, sem necessidade de API key |
| Sistema Cantareira | [Portal Mananciais SABESP](https://mananciais.sabesp.com.br/) | API oficial de resumo diário (Local 64) |
| Base Cartográfica / Distritos | OpenStreetMap + GeoJSON local | Assets pré-empacotados em `app/src/main/assets/` |

---

## Arquitetura e Estrutura do Projeto

O projeto segue o padrão **MVVM** com repositórios singleton, `StateFlow` e cache em disco para abertura rápida e resiliência offline.

```text
app/src/main/java/com/example/
├── MainActivity.kt
├── ui/theme/                          # Tema Material 3 (cores, tipografia)
└── chuvaradar/
    ├── background/
    │   ├── RadarAlarmReceiver.kt      # Verificação periódica em segundo plano
    │   └── RadarNotificationHelper.kt # Notificações de aproximação de chuva
    ├── data/
    │   ├── RadarModels.kt             # Modelos do radar, nowcast e endereços
    │   ├── RainIntensity.kt           # Classificação de intensidade (dBZ / cores)
    │   ├── RadarRepository.kt         # Download, transparência e cache dos frames USP
    │   ├── RadarTimestampParser.kt    # Leitura de timestamp (OCR + HTML + HTTP)
    │   ├── DistrictData.kt            # Modelo de distritos de São Paulo
    │   ├── OpenStreetMapProvider.kt   # Carregamento do mapa base e distritos
    │   ├── UserPreferencesDataStore.kt# Preferências e histórico de endereços (DataStore)
    │   ├── WeatherForecastModels.kt   # Modelos WMO, previsão horária/diária e veredito
    │   ├── OpenMeteoParser.kt         # Parser Open-Meteo e lógica "Vai chover hoje?"
    │   ├── WeatherForecastRepository.kt # Cliente Open-Meteo + cache em disco
    │   ├── CantareiraModels.kt        # Modelos e faixas ANA/DAEE do Cantareira
    │   ├── SabespCantareiraParser.kt  # Parser resiliente SABESP (v4 + clássico)
    │   └── CantareiraRepository.kt    # Coleta paralela de 30 dias + cache em disco
    ├── engine/
    │   └── RadarAnalysisEngine.kt     # Fluxo óptico/deslocamento, clutter e nowcasting
    ├── ui/
    │   ├── RadarViewModel.kt          # Estado central (radar, previsão e Cantareira)
    │   ├── RadarMainScreen.kt         # Tela principal com todos os cards e abas
    │   └── components/
    │       ├── NowcastHeroCard.kt     # Card principal de nowcasting (60 min)
    │       ├── RadarViewer.kt         # Visualizador animado do radar com gestos
    │       ├── DistrictMinimap.kt     # Mapa interativo dos distritos de SP
    │       ├── RainTodayVerdictCard.kt# Card "Vai chover hoje?" (Sim / Talvez / Não)
    │       ├── WeatherForecastCard.kt # Gráfico em degraus + previsão de 8 dias
    │       ├── WeatherConditionIcon.kt# Ícones meteorológicos em Canvas
    │       ├── CantareiraVolumeCard.kt# Card e gráfico interativo de 30d da SABESP
    │       ├── AddressSelectorSheet.kt# Seleção de endereço (GPS, busca e favoritos)
    │       └── SettingsSheet.kt       # Configurações de notificações e testes
    └── widget/
        └── ChuvaRadarWidgetProvider.kt# Widget Android para tela inicial
```

---

## Tecnologias Utilizadas

- **Kotlin** + **Coroutines / StateFlow**
- **Jetpack Compose** + **Material Design 3**
- **OkHttp** para requisições HTTP/HTTPS
- **Jetpack DataStore Preferences** para persistência de preferências e endereços recentes
- **Google Play Services Location** para obtenção opcional de localização via GPS
- **ML Kit Text Recognition** para extração de data/hora nos frames do radar
- **JUnit 4** + **Robolectric** para testes unitários locais

---

## Como Compilar e Testar

### Requisitos
- JDK 11+
- Android SDK configurado (`ANDROID_SDK_ROOT`) — **compileSdk / targetSdk 36**, **minSdk 24**
- Não é necessária nenhuma chave de API externa para radar, previsão Open-Meteo ou Sistema Cantareira.

### Comandos principais

```bash
# Compilar o APK de debug
gradle assembleDebug

# Executar os testes unitários e Robolectric
gradle :app:testDebugUnitTest
```

### Suíte de Testes Unitários (`app/src/test/java/com/example/`)
- `RadarAnalysisEngineTest.kt` — motor de nowcasting, clutter e detecção de chuva
- `RadarTimestampParserTest.kt` — parsing de timestamps e detecção de atraso do radar
- `GestureTransformTest.kt` — transformações de zoom e pan no radar
- `OpenMeteoParserTest.kt` — parsing da API Open-Meteo, cache e veredito "Vai chover hoje?"
- `SabespCantareiraParserTest.kt` — parsing SABESP (API v4 e clássica) e faixas ANA/DAEE
- `ExampleRobolectricTest.kt` — verificação de inicialização do app
