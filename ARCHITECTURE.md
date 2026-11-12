# Yabai OCR — Estrutura do projeto

Documento de referência para quem precisa **encontrar onde mexer**. Descreve os três módulos, o papel
de cada arquivo, os fluxos principais e as armadilhas conhecidas. Para o histórico de decisões e o
porquê de cada correção, ver [ROADMAP.md](ROADMAP.md); para o uso do app, [README.md](README.md).

- **110+ arquivos Kotlin**, ~50 mil linhas, três módulos Gradle.
- `:core` — Kotlin/JVM puro (sem Android): OCR, protobuf, dicionários, japonês, geometria.
- `:desktop` — Compose Desktop para Windows e Linux (Steam Deck).
- `:app` — Android (`minSdk 26`, `compileSdk/targetSdk 36`), overlays em Views.

---

## 1. Visão em uma página

```
                 ┌──────────────────────┐
   Android ──────┤  :app (Activities,   │
                 │  Services, Overlays) │
                 └──────────┬───────────┘
                            │
   Desktop ──────┐          ▼
   (Compose)     │   ┌──────────────┐
                 └──►│    :core     │  Kotlin/JVM puro
                     │  OCR · dict  │  sem dependência de Android
                     │  jp · layout │
                     └──────────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
          Google Lens   ONNX Runtime   ML Kit (app)
          (HTTP)        (PP-OCRv6)     (offline)
```

O `:core` é a única fonte de verdade de OCR e dicionários. O desktop e o Android só **orquestram**:
capturam a tela, chamam o motor escolhido, mostram o resultado e consultam o dicionário.

---

## 2. Build e comandos

| tarefa | o que faz |
| --- | --- |
| `.\gradlew.bat :desktop:test` | suíte do desktop (JVM, sem abrir janela) |
| `.\gradlew.bat :desktop:run --args="--minimized"` | abre o app na bandeja |
| `.\gradlew.bat :desktop:compileKotlin` | compilação rápida do desktop |
| `.\gradlew.bat :core:test` | suíte do core (JVM puro) |
| `.\gradlew.bat :app:testDebugUnitTest` | suíte Android com Robolectric (sem aparelho) |
| `.\gradlew.bat :app:assembleDebug` | APK de debug (`dev.yabai.ocr.debug`) |
| `.\gradlew.bat :desktop:packagePortableTarGz` | `.tar.gz` autocontido (Linux) |
| `.\gradlew.bat :desktop:packageLinuxAppImage` | `.AppImage` (requer `appimagetool`) |

Opções de teste que ligam casos reais (nenhuma roda por padrão):

| flag | módulo | liga |
| --- | --- | --- |
| `-Plive=1` | `:core` | fala com o endpoint real do Google Lens |
| `-Pdicts="C:\zips"` | `:core` | ZIPs reais do Yomitan |
| `-PcompiledDicts="C:\compiled"` | `:core` | `.yabai` já compilados (índice de tradução) |
| `-Pppocr="C:\home"` | `:desktop` | modelos do PP-OCR em `home/ppocr_v6` (teste ponta a ponta) |

Contagens atuais: **`:core` ~190 testes**, **`:desktop` ~258**, **`:app` ~411**. Números redondos:
confirme com a suíte antes de citá-los.

---

## 3. Árvore de diretórios

```
.
├── build.gradle.kts            # plugins e versões compartilhadas (version catalog em gradle/)
├── settings.gradle.kts         # inclui :core, :desktop e :app
├── gradle/libs.versions.toml   # todas as dependências e versões
├── AGENTS.md                   # regras de trabalho no repositório
├── ARCHITECTURE.md             # este documento
├── CONTRIBUTING.md             # como contribuir (compilar, testar, enviar)
├── LICENSE                     # MIT
├── README.md                   # visão geral pública
├── ROADMAP.md                  # histórico técnico item a item (B/F/M/D/I/N/R)
├── LANGUAGES.md                # i18n: chaves e onde cada texto vive
├── LINUX.md / STEAMOS.md       # suporte a Linux e Steam Deck
├── ACCESSIBILITY_SERVICE_GUIDE.md
└── apk-output/                 # APKs renomeados pelo build (git-ignored)
```

---

## 4. `:core` — Kotlin/JVM puro

`core/src/main/kotlin/dev/yabai/ocr/core/`

### `lens/` — Google Lens (HTTP + protobuf)

| arquivo | papel |
| --- | --- |
| `LensClient.kt` | cliente OkHttp do endpoint do Lens: envia PNG, recebe protobuf, mantém cookie jar |
| `LensModels.kt` | `EncodedImage`, `PixelBox`, `LensSegment`, `LensBlock`, `LensResult`, `LensException` — o formato que **todo** o app consome |
| `LensProto.kt` | leitor de protobuf à mão (varint, campos, tamanhos) |
| `LensResultBuilder.kt` | monta `LensResult` a partir da resposta bruta, na ordem de leitura |
| `FuriganaMode.kt` | `REMOVE` / `PARENTHESES`: o que fazer com a leitura impressa ao lado do kanji |
| `RubyGeometry.kt` | geometria de rubi (leitura sobre o kanji) para desenhar no overlay/picker |

**Regra de ouro:** o texto reconhecido é o dado. Nada aqui "corrige" caractere reconhecido.

### `ppocr/` — motor offline (PP-OCRv6_manga)

| arquivo | papel |
| --- | --- |
| `PPOcrSpec.kt` | constantes e geometria: lado maior 960 (ou orçamento 3:4 em tiras), limiar 0,15, caixa 0,25, unclip 1,4, altura 48 |
| `PPOcrPreprocess.kt` | tensor do detector (ImageNet) e da linha (girada à esquerda, 48 px, −1..1) |
| `PPOcrPostprocess.kt` | mapa DB → caixas (componentes conexos, confiança, unclip) e ordem de leitura direita→esquerda |
| `PPOcrEngine.kt` | sessões ONNX (det + rec), CTC guloso; `PPOcrOutcome`/`PPOcrBlock`/`PPOcrLine` |
| `PPOcrModelStore.kt` | caminhos e URLs dos três arquivos (`manga_det.onnx`, `manga_rec.onnx`, `ppocrv6_dict.txt`) |
| `PPOcrModelDownloader.kt` | download com `.tmp`, retomada por arquivo e progresso |

ONNX é `compileOnly` no core: quem consome (`:desktop`, `:app`) traz o runtime.

### `dict/` — dicionários e busca

| arquivo | papel |
| --- | --- |
| `CompactDictionary.kt` | abre um `.yabai`/pasta instalada e expõe entradas |
| `DictionaryCompiler.kt` | compila ZIP do Yomitan → `.yabai` (blocos, chaves, índice de leituras) |
| `DictionaryData.kt` | estruturas serializadas do dicionário compilado |
| `DictionaryQuery.kt` | normalização da consulta (`stripReadings` etc.) |
| `Deinflector.kt` | deinflexão japonesa (verbos/adjetivos) para alcançar a forma de dicionário |
| `LookupEngine.kt` | busca por termo/leitura, ranking, `TokenMatch` com motivos de flexão |
| `Furigana.kt` | leitura gerada pelo dicionário, alinhada ao texto reconhecido |
| `GlossaryRenderer.kt` | formatação do glossário (tags, listas, exemplos) |
| `Romaji.kt` | kana → romaji (busca e exibição) |
| `TranslationIndex.kt` | índice lateral para buscar nas **definições** (`search_by_translation`) |

### `jp/`, `layout/`, `proto/`

| arquivo | papel |
| --- | --- |
| `jp/JapaneseText.kt` | normalização de texto japonês (espaço só entre dois CJK), contagens, utilidades |
| `layout/BlockColumns.kt` | junta colunas lado a lado de um mesmo balão em um bloco |
| `layout/ReadingOrder.kt` | ordena caixas em ordem de leitura de mangá |
| `layout/KanjiGrid.kt` | grade de traços/radicais para o card de kanji |
| `proto/Proto.kt` | leitor de protobuf genérico (`ProtoException`), base do `LensProto` |

### Testes do core

`core/src/test/kotlin/dev/yabai/ocr/core/` — um arquivo por área (`dict/`, `jp/`, `layout/`,
`lens/`, `ppocr/`). `LensLiveTest`, `TranslationIndexRealTest` e o teste ponta a ponta do motor só
rodam com as flags acima.

---

## 5. `:desktop` — Compose Desktop (Windows/Linux)

`desktop/src/main/kotlin/dev/yabai/ocr/desktop/`

### Entrada, janelas e configuração

| arquivo | papel |
| --- | --- |
| `Main.kt` | ponto de entrada: janela principal, bandeja, atalhos, abre picker/recorte/resultado/dicionário |
| `DesktopSettings.kt` | `settings.properties` em `~/.yabai_ocr`: atalhos, motor, tema, dicionários, IA, tradução |
| `SettingsWindow.kt` | a tela de Ajustes inteira (aparência, atalhos, motor + download, dicionários, IA) |
| `ResultWindow.kt` | painel de resultado: texto editável, imagem, botões (copiar, IA, dicionário, recortar) |
| `QuickDictWindow.kt` | busca rápida de dicionário em janela própria |
| `DictionaryManagerWindow.kt` | conteúdo do gerenciador: busca, instalados, catálogo, créditos |
| `DesktopTheme.kt` | paletas clara/escura, `contrastRatio`, aplicação do tema |
| `AppIconGenerator.kt` | ícone em runtime (janela, bandeja) |
| `CaptureNoticeWindow.kt` | aviso flutuante de captura |
| `i18n/DesktopStrings.kt` | **todas** as strings pt/en do desktop (nada fixo nas telas) |

### Captura de tela

| arquivo | papel |
| --- | --- |
| `ScreenCapture.kt` | captura primária/região via `Robot`, tamanho nativo em HiDPI |
| `PlatformServices.kt` | escolhe o backend de captura por plataforma/sessão |
| `ScreenshotMethod.kt` | override de método (X11/Wayland/portal) por variável de ambiente |
| `PortalScreenshotCapture.kt` | portal `Screenshot` do KDE (Wayland) via D-Bus |
| `SegmentPickerWindow.kt` | seletor de tela inteira: desenha as caixas e devolve o trecho escolhido |
| `PickerBoxes.kt` / `PickerReadings.kt` / `PickerWindowPlacement.kt` | geometria das caixas, leituras no picker e posicionamento da janela |
| `RegionSelectionWindow.kt` | recorte à mão com alças (`CropHandle`) |

### Hover

| arquivo | papel |
| --- | --- |
| `HoverOcrManager.kt` | ciclo do hover: captura → OCR (motor escolhido) → dicionário → cartão |
| `HoverGate.kt` / `HoverDismissGate.kt` | quando disparar (parada do ponteiro) e quando fechar |
| `HoverGeometry.kt` | recorte ao redor do ponteiro, coordenadas locais, HiDPI |
| `HoverTextSelector.kt` | escolhe a linha/palavra sob o ponteiro e monta o hit |
| `HoverTooltipWindow.kt` | cartão flutuante (tamanho, posição, conteúdo) |
| `HoverCapability.kt` | o hover é suportado nesta sessão? (X11/Wayland/Win) |
| `PointerSource.kt` | fonte da posição do ponteiro (AWT ou KWin) |
| `KWinHoverBridge.kt` / `KWinIntegration.kt` / `KWinScript.kt` | script KWin + D-Bus que entrega o ponteiro no Wayland |

### OCR do desktop

| arquivo | papel |
| --- | --- |
| `DesktopOcr.kt` | **porta única**: ordem dos motores (`AUTO`/`LENS`/`PP_OCR`), fallback e `DesktopOcrAnswer` |
| `DesktopPPOcr.kt` | motor offline: sessões em cache, conversão `PPOcrOutcome` → `LensResult` |
| `DesktopDictLoader.kt` | abre/caches o `LookupEngine` a partir da pasta de dicionários; `sizeBytes` soma o conteúdo de um `.yabai` (que é pasta) |

### Atalhos, bandeja e sistema

| arquivo | papel |
| --- | --- |
| `GlobalHotkeyManager.kt` | registro dos atalhos globais, recriação ao vivo |
| `HotkeyBackend.kt` / `HotkeyMatcher.kt` / `HotkeyBindings.kt` | backend (JNativeHook/portal), combinação de teclas e catálogo de ações |
| `PortalGlobalShortcuts.kt` | atalhos globais no Wayland via portal D-Bus |
| `XdgTrigger.kt` | atalhos por `gsettings`/XDG no Linux |
| `SystemTrayManager.kt` / `StatusNotifierTray.kt` / `TrayMenu.kt` / `TrayMenuPlacement.kt` / `TrayMenuInvoker.kt` | bandeja: AWT no Windows, StatusNotifier no Plasma, menu e posicionamento |
| `YabaiDbusService.kt` | serviço D-Bus próprio (hover, atalhos) |
| `StartupManager.kt` / `Autostart.kt` / `WindowsAutostart.kt` / `LinuxAutostart.kt` / `LinuxDesktopEntry.kt` / `LinuxSystemIntegration.kt` | iniciar com o sistema e integração com o desktop |
| `ClipboardMonitor.kt` | observa o clipboard (copiar do cartão) |
| `DesktopPlatform.kt` | detecção de SO/sessão (SteamOS, Wayland, X11) |
| `DesktopAppUpdateChecker.kt` | procura release nova e escolhe o asset do SO |
| `DesktopDictionaryCatalog.kt` / `DesktopDictionaryUpdateChecker.kt` | catálogo de dicionários e checagem de atualização |

### Testes do desktop

`desktop/src/test/kotlin/dev/yabai/ocr/desktop/` — quase um arquivo por classe de produção. Os que
valem destaque:

- `DesktopOcrOrderTest` — ordem dos motores e conversão do resultado do PP-OCR.
- `DesktopEngineMigrationTest` — preferências antigas (`MANGA_OCR`) caem em `AUTO`.
- `DesktopPPOcrEndToEndTest` — **opt-in** (`-Pppocr`), lê uma página desenhada com os modelos reais.
- `PortalGlobalShortcutsTest`, `StatusNotifierTrayTest`, `KWinScriptTest` — D-Bus/Wayland sem sessão.
- `NativeRuntimeModulesTest` — o runtime empacotado declara `jdk.net`.

---

## 6. `:app` — Android

`app/src/main/java/dev/yabai/ocr/`

### Telas e entrada

| arquivo | papel |
| --- | --- |
| `MainActivity.kt` | ajustes e permissões; escolhe motor, baixa modelos, inicia o serviço |
| `StartOcrActivity.kt` | atalho/atalho de tela para iniciar a captura |
| `LookupActivity.kt` | abre a busca do dicionário a partir de um texto |
| `DictionaryActivity.kt` | tela de dicionários (instalar, atualizar, filtrar) |
| `HelpActivity.kt` | ajuda e explicações |
| `YabaiOcrApplication.kt` | `Application`: tema, idioma, agendador de atualizações |
| `AppSettings.kt` | `SharedPreferences` tipadas de **todo** o app |
| `UsageTracker.kt` | quantas chamadas ao Lens por dia (não há cota pública) |

### OCR

| arquivo | papel |
| --- | --- |
| `OcrDispatcher.kt` | escolhe o motor: Lens, ML Kit, PP-OCRv6; `AUTO` percorre a prioridade com fallback |
| `OcrEngine.kt` | contrato (`OcrEngine`) e resultado (`OcrOutcome`, `OcrBlock`, `OcrPiece`) |
| `LensOcrEngine.kt` | Lens sobre `Bitmap` (redimensiona, codifica, chama o core) |
| `MlKitOcrEngine.kt` | ML Kit japonês offline, agrupando linhas com `BlockColumns` |
| `ImagePreparer.kt` | redimensiona/amplia recortes antes do OCR |

### Captura, serviço e overlays

| arquivo | papel |
| --- | --- |
| `capture/ScreenCaptureSession.kt` | `MediaProjection`: uma captura de tela por sessão |
| `service/FloatingOcrService.kt` | serviço em primeiro plano: captura, OCR, mostra o resultado |
| `service/LookupOverlayService.kt` | busca do dicionário sobre outras apps |
| `service/YabaiTileService.kt` | bloco de configurações rápidas |
| `overlay/OverlayController.kt` | ciclo de vida dos overlays |
| `overlay/ResultOverlay.kt` | painel de resultado (texto, tradução, ações, dicionário) |
| `overlay/TextPickerOverlay.kt` | seletor de trechos sobre a captura |
| `overlay/PickerDecision.kt` / `PickerTargets.kt` / `PickerZoom.kt` / `PickerReadingLayout.kt` / `PickerReading.kt` | regra de "sempre mostrar o seletor", alvos de toque, zoom e leituras |
| `overlay/RegionOverlay.kt` | recorte à mão |
| `overlay/AppPickerOverlay.kt` | escolha do app de IA |
| `overlay/KanjiOverlay.kt` / `KanjiCardText.kt` | card de kanji (traços, leituras, compostos) |
| `overlay/DefinitionStyle.kt` / `ActionGrid.kt` / `SelectionToolbar.kt` / `FlowLayout.kt` / `Widgets.kt` | estilo do glossário, grade de ações, barra de seleção e widgets base |

### Dicionários, IA e tradução

| arquivo | papel |
| --- | --- |
| `dict/DictionaryCatalog.kt` | catálogo de dicionários disponíveis |
| `dict/DictionaryDownloader.kt` | download/instalação de ZIP |
| `dict/DictionaryRepository.kt` | dicionários instalados, filtro e ordem |
| `dict/DictionarySearch.kt` | busca (termo, leitura, definição) |
| `dict/DictionaryUpdates.kt` | verificação periódica (WorkManager) e notificação |
| `dict/KanjiDefault.kt` | qual dicionário responde pelo card de kanji |
| `ai/AskAi.kt` | monta a pergunta e abre o app de IA escolhido |
| `translate/OfflineTranslator.kt` | tradução offline com ML Kit |

### Recursos

`app/src/main/res/`: `layout/activity_main.xml` e `activity_help.xml`, `values/` (pt-BR),
`values-en/`, `values-night/colors.xml`, `drawable/`, `mipmap-anydpi-v26/`.
Toda string de interface vive em `values/strings.xml` **e** `values-en/strings.xml` — ver
[LANGUAGES.md](LANGUAGES.md).

### Testes do app

Robolectric (`app/src/test/java/dev/yabai/ocr/`), sem aparelho. Um arquivo por área:
`overlay/` é a maior parte (layout, toque, tema, picker), seguida de `dict/` e raiz
(configurações, telas, intents). `PpOcrDispatchTest` cobre a escolha do motor offline.

---

## 7. Fluxos principais

### Captura de tela inteira (desktop)

```
atalho → Main.kt → SegmentPickerWindow.scanScreen
        → DesktopOcr.recognize(imagem, settings)      (motor escolhido; AUTO percorre prioridade)
        → LensResult → desenha caixas → usuário escolhe
        → ResultWindow(texto do trecho) → dicionário → cartão
```

### Recorte à mão (desktop)

```
atalho → RegionSelectionWindow → BufferedImage recortado
        → ResultWindow → DesktopOcr.recognize → texto + motor usado no cabeçalho
```

### Hover (desktop)

```
ponteiro parado → HoverGate → HoverOcrManager.capture(recorte)
        → DesktopOcr.recognize → HoverTextSelector (palavra sob o ponteiro)
        → LookupEngine → HoverTooltipWindow
```

### Android

```
MainActivity/service → MediaProjection captura
        → OcrDispatcher.recognize (Lens / ML Kit / PP-OCRv6, AUTO com fallback)
        → ResultOverlay (+ TextPickerOverlay quando a tela inteira tem blocos)
        → dicionário e tradução no mesmo painel
```

### Modelos offline (PP-OCRv6)

```
Ajustes (desktop/app) → PPOcrModelDownloader → ~23 MB em ppocr_v6/
        → PPOcrEngine lazy (det + rec) → reconhecimento local
```

---

## 8. Onde mexer para cada pedido

| pedido | comece por |
| --- | --- |
| texto reconhecido errado (Lens) | `core/lens/LensResultBuilder.kt`, `LensProto.kt` — **nunca** normalizar o texto |
| texto reconhecido errado (offline) | `core/ppocr/PPOcrPreprocess.kt` (tensor), `PPOcrPostprocess.kt` (caixas/ordem), `PPOcrEngine.kt` (CTC) |
| ordem/fallback de motores | `desktop/DesktopOcr.kt`, `app/ocr/OcrDispatcher.kt` |
| novo motor de OCR | contrato em `app/ocr/OcrEngine.kt`; no desktop, devolver `LensResult` |
| captura/picker desktop | `ScreenCapture.kt`, `SegmentPickerWindow.kt`, `PickerBoxes.kt` |
| hover | `HoverOcrManager.kt`, `HoverGeometry.kt`, `HoverTextSelector.kt` |
| atalhos | `HotkeyBindings.kt`, `HotkeyMatcher.kt`, `GlobalHotkeyManager.kt` |
| bandeja | `SystemTrayManager.kt`, `StatusNotifierTray.kt`, `TrayMenu*.kt` |
| dicionários (busca/importação) | `core/dict/*`, depois `app/dict/*` ou `desktop/DictionaryManagerWindow.kt` |
| furigana | `core/dict/Furigana.kt`, `core/lens/FuriganaMode.kt`, `overlay/PickerReadingLayout.kt` |
| textos de interface | `desktop/i18n/DesktopStrings.kt`; `app/src/main/res/values*/strings.xml` |
| tema | `desktop/DesktopTheme.kt`; `app/src/main/res/values*/themes.xml` |
| atualização do app | `desktop/DesktopAppUpdateChecker.kt`; Android depende do build/loja |

---

## 9. Convenções e armadilhas

- **Texto reconhecido é dado.** Não corrigir, completar ou "melhorar" caractere reconhecido.
- **UI em pt-BR**, comentários de código em inglês, nomes de API em inglês.
- **HiDPI:** use `createMultiResolutionScreenCapture`; desenhe 1:1 com `FilterQuality.None`;
  bounds AWT vão direto para `.dp`.
- **Picker:** sem véu; a tela inteira sempre para no seletor quando há ao menos um bloco.
- **Atalhos:** códigos `VC_*` do JNativeHook ≠ AWT; `GlobalHotkeyManager` é criado com `remember`.
- **`:core` não pode depender de Android.** Se a API é `android.*`, ela pertence a `:app`.
- **Mudança de comportamento** exige teste de regressão + prova de mutação + README/ROADMAP.
- **`settings.properties` é do usuário**: backup antes de E2E, restauração conferida no fim.
- **Gradle no Windows com caminho contendo espaço**: use o caminho curto (`C:\Users\ERICPC~1\...`).

---

## 10. Documentos

| arquivo | para quem |
| --- | --- |
| [README.md](README.md) | quem quer instalar e usar |
| **ARCHITECTURE.md** | quem vai mexer no código |
| [ROADMAP.md](ROADMAP.md) | histórico técnico: causa raiz, correção, testes, evidência |
| [LANGUAGES.md](LANGUAGES.md) | i18n: chaves, onde vivem, o que falta traduzir |
| [LINUX.md](LINUX.md) / [STEAMOS.md](STEAMOS.md) | Linux e Steam Deck |
| [AGENTS.md](AGENTS.md) | regras de trabalho no repositório (agentes e pessoas) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | como compilar, testar e enviar uma mudança |
| [ACCESSIBILITY_SERVICE_GUIDE.md](ACCESSIBILITY_SERVICE_GUIDE.md) | serviço de acessibilidade |
