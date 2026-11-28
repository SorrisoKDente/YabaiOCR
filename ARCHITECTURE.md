# Yabai OCR — Estrutura do projeto

**Português** | [English](ARCHITECTURE.en.md)

Documento de referência para quem precisa **encontrar onde mexer**. Descreve os três módulos, o papel
de cada arquivo, os fluxos principais e as armadilhas conhecidas. Para o histórico de decisões e o
porquê de cada correção, ver [ROADMAP.md](docs/ROADMAP.md); para o uso do app, [README.md](README.md).

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

Contagens atuais: **`:core` 197 testes**, **`:desktop` 546**, **`:app` 836**. Confirme com a suíte
antes de citá-los.

---

## 3. Árvore de diretórios

```
.
├── build.gradle.kts            # plugins e versões compartilhadas (version catalog em gradle/)
├── settings.gradle.kts         # inclui :core, :desktop e :app
├── gradle/libs.versions.toml   # todas as dependências e versões
├── AGENTS.md                   # regras de trabalho no repositório
├── .github/                    # agentes e skills do projeto (formato do GitHub Copilot)
│   ├── agents/yabai.md         # persona do engenheiro: causa raiz, mutação, E2E
│   └── skills/<nome>/SKILL.md  # skills carregadas sob demanda (+ scripts e referências)
├── ARCHITECTURE.md             # este documento
├── ARCHITECTURE.en.md          # este documento em inglês
├── CONTRIBUTING.md / .en.md    # como contribuir (compilar, testar, enviar)
├── LICENSE                     # PolyForm Noncommercial 1.0.0
├── README.md / README.pt-BR.md  # visão geral pública (en/pt)
├── docs/                       # documentação técnica, LINUX, STEAMOS e ROADMAP
│   ├── LINUX.md / STEAMOS.md   # suporte a Linux e Steam Deck
│   └── ROADMAP.md              # histórico técnico item a item (B/F/M/D/I/N/R)
├── LANGUAGES.md                # i18n: chaves e onde cada texto vive
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
| `LensModels.kt` | `EncodedImage`, `PixelBox`, `LensSegment`, `LensBlock`, `LensResult`, `LensException` — o formato que **todo** o app consome; `printedReadingsOf` acha a furigana impressa por geometria nas linhas de **qualquer** motor (Lens, PP-OCR, ML Kit — R64) |
| `LensProto.kt` | leitor de protobuf à mão (varint, campos, tamanhos) |
| `LensResultBuilder.kt` | monta `LensResult` a partir da resposta bruta, na ordem de leitura |
| `FuriganaMode.kt` | `REMOVE` / `PARENTHESES`: o que fazer com a leitura impressa ao lado do kanji |
| `RubyGeometry.kt` | geometria de rubi (leitura sobre o kanji) para desenhar no overlay/picker; as peças do `LensBlock` levam as leituras já impressas para o picker não as duplicar (R57) |

**Regra de ouro:** o texto reconhecido é o dado. Nada aqui "corrige" caractere reconhecido.

### `ppocr/` — motor offline (PP-OCRv6_manga)

| arquivo | papel |
| --- | --- |
| `PPOcrSpec.kt` | constantes e geometria: lado maior 960 (ou orçamento 3:4 em tiras), limiar 0,15, caixa 0,25, unclip 1,4, altura 48; `PPOcrLine.printed` e `withPrintedReadings` marcam a furigana impressa (R64) |
| `PPOcrPreprocess.kt` | tensor do detector (ImageNet) e da linha (girada à esquerda, 48 px, −1..1) |
| `PPOcrPostprocess.kt` | mapa DB → caixas (componentes conexos, confiança, unclip) e ordem de leitura direita→esquerda |
| `PPOcrEngine.kt` | sessões ONNX (det + rec), CTC guloso; `PPOcrOutcome`/`PPOcrBlock`/`PPOcrLine`; leitura **focada** com `focus` (só reconhece as linhas perto do ponto — D94) |
| `PPOcrFocus.kt` | `linesToRecognize`: quais linhas detectadas o reconhecedor precisa ler quando a pergunta é sobre um ponto (hover); sem foco, lê todas |
| `PPOcrModelStore.kt` | caminhos e URLs dos três arquivos (`manga_det.onnx`, `manga_rec.onnx`, `ppocrv6_dict.txt`) |
| `PPOcrModelDownloader.kt` | download com `.tmp`, retomada por arquivo e progresso |

ONNX é `compileOnly` no core: quem consome (`:desktop`, `:app`) traz o runtime.

| `DonationInfo.kt` | chave Pix e link do Ko-fi, compartilhados pelos dois apps |

### `md/` — Markdown das release notes

| arquivo | papel |
| --- | --- |
| `Markdown.kt` | parser mínimo usado pelos dois apps: títulos, parágrafos, listas, citações, código, divisórias e ênfase/links inline |

### `update/` — comparação de versões

| arquivo | papel |
| --- | --- |
| `VersionCompare.kt` | `compareVersions`/`isNewerVersion`: componente ausente conta zero, `v`/`-debug`/`+build` não mudam a versão |

### `dict/` — dicionários e busca

| arquivo | papel |
| --- | --- |
| `CompactDictionary.kt` | abre um `.yabai`/pasta instalada e expõe entradas |
| `DictionaryCompiler.kt` | compila ZIP do Yomitan → `.yabai` (blocos, chaves, índice de leituras) |
| `DictionaryData.kt` | estruturas serializadas do dicionário compilado |
| `DictionaryQuery.kt` | normalização da consulta (`stripReadings` etc.) |
| `Deinflector.kt` | deinflexão japonesa (verbos/adjetivos) para alcançar a forma de dicionário |
| `LookupEngine.kt` | busca por termo/leitura, ranking, `TokenMatch` com motivos de flexão; segue o redirecionamento de forma antiga no mesmo dicionário (R46e) |
| `Furigana.kt` | leitura gerada pelo dicionário, alinhada ao texto reconhecido |
| `GlossaryRenderer.kt` | formatação do glossário (tags, listas, exemplos) e o alvo do link quando a entrada só aponta para outra (R46e) |
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
| `Main.kt` | ponto de entrada: janela principal com três telas (`MainScreen`: Ajustes, Explicações e Dicas, Gerenciador de Dicionários), bandeja, atalhos, abre picker/recorte/resultado; guarda a aba de Ajustes para voltar do manual nela; o aviso de hover sem dicionário abre o gerenciador na aba de instalados (R59) |
| `DesktopSettings.kt` | `settings.properties` em `~/.yabai_ocr`: atalhos, motor, tema, dicionários, IA, tradução, Anki (ligar botões, baralho, tipo de nota, endereço, etiquetas) |
| `SettingsWindow.kt` | a tela de Ajustes: `SettingsTopBar` (título, `LanguageMenu`, «❓ Ajuda»), `SettingsTabRow` e cinco abas (`SettingsTab`) com os cartões de cada área — Geral (aparência, renderizador, monitor, inicialização, Linux, KDE), Atalhos, Leitura (motor + download do PP-OCR, hover, resultado, tradução, IA), Dicionários e Sobre; `TabColumn` é o corpo rolável comum |
| `ResultWindow.kt` | painel de resultado: texto editável, imagem, botões (copiar, IA, Anki, recortar); «＋ Anki» manda a frase e cada cartão de dicionário tem o seu, com aviso de enviado/guardado e «Desfazer» |
| `AnkiPanel.kt` | o caminho único dos três painéis para o Anki: `sendToAnki` (cartão → fila → cliente → texto do aviso, com o baralho resolvido) e `wordNote` (cartão por palavra, com a frase vazia porque o dicionário rápido e o hover não têm frase) |
| `DictionaryMatchText.kt` | a linha «forma lida → verbete · forma antiga» que o hover e o resultado desenham (R46e) |
| `AnkiConnect.kt` | o add-on do Anki falado por HTTP local (`127.0.0.1:8765`): JSON legado (`version: 6`) e, quando ele responde 404/405, o JSON-RPC `/api/v1` (`api.describe`, `decks.*`, `noteTypes.*`, `notes.*`, chave em `Authorization: Bearer`), com o formato que respondeu memorizado; versão, baralho, tipo de nota, duplicata, `addNote` e `deleteNotes`; distingue add-on ausente, Anki fechado, chave recusada e duplicata em vez de devolver um erro só — duplicata só quando o `error` de `canAddNotesWithErrorDetail` diz «duplicate», porque o mesmo `false` cobre um cartão que o tipo de nota recusa; timeouts de 1 s/5 s |
| `AnkiSender.kt` | entrega o cartão e, quando não dá, guarda-o: grava na fila **antes** da tentativa, remove só depois do `addNote` confirmado — ou quando o Anki diz que já tem o cartão, que vira «já está no Anki» —, marca o motivo da falha e reenvia o que ficou; recusa que não é duplicata fica na fila com o texto do add-on |
| `ReleaseNotesWindow.kt` | preview em Markdown das notas do release do GitHub, aberto em Ajustes → Sobre |
| `LicenseWindow.kt` | texto da licença empacotado no jar, lido em Ajustes → Sobre |
| `HelpWindow.kt` | o manual «Explicações e Dicas» como tela da janela principal (`HelpContent(settings, onBackRequested)`), aberto pelo «❓ Ajuda» do cabeçalho: barra com a seta de volta (`backToPreviousIcon`, «◀»; o nome acessível é `btnBackToPrevious`), a referência de botões (`helpButtons()`, ícone à esquerda, nome e o que faz) e uma entrada por função (capturas, hover, busca rápida, dicionários, resultado, Anki, renderizadores, motores e bandeja), em pt/en; a entrada do Anki diz o que instalar e o que precisa estar aberto |
| `UpdateInstaller.kt` | baixa o asset com progresso, troca o AppImage em execução (backup `.old`), entrega o instalador ao shell do sistema (associação de arquivo, `.msi` via `msiexec`) e reinicia |
| `InstanceLock.kt` | trava de arquivo em `~/.yabai_ocr/instance.lock`: uma instância por usuário, antes de D-Bus e bandeja |
| `KdeCapturePermission.kt` | opção «captura silenciosa no KDE»: grava `~/.config/environment.d/yabai-ocr.conf` com `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, porque o KWin só autoriza `ScreenShot2` a um `Exec` fixo em diretório de root |
| `UpdatesNoticeWindow.kt` | aviso único de atualizações: a versão do app (barra com a porcentagem do download, reinicia, abre as notas) e os dicionários com revisão nova, na mesma janela; `UpdateDownloadProgress`/`UpdateInstallerNotice` são compartilhados com a aba Sobre e, em Windows/macOS, o fim do download avisa para continuar no instalador (R60) |
| `DictionaryUpdateMonitor.kt` | checagem periódica dos dicionários no escopo da aplicação (`--minimized` incluso), oferta por revisão remota e chave de «já avisado» |
| `NativeLibraryPath.kt` | aponta `jnativehook.lib.path` para `~/.cache/yabai-ocr/native` no Linux, antes de tocar em `GlobalScreen` (AppImage é somente leitura) |
| `QuickDictWindow.kt` | busca rápida de dicionário em janela própria; «＋ Anki» ao lado de cada palavra (ajuste desligado por padrão), com o aviso no topo da lista |
| `DictionaryManagerWindow.kt` | conteúdo do gerenciador: busca, instalados, catálogo, créditos; a mesma barra de volta (seta «◀») do manual quando aberto como tela (`onBackRequested`); abre na aba pedida (`initialTab`, R59) |
| `DesktopTheme.kt` | paletas clara/escura, `contrastRatio`, aplicação do tema |
| `AppIconGenerator.kt` | ícone em runtime (janela, bandeja) e selo de hover no canto inferior direito |
| `AppScrollbar.kt` | `ScrollableLazyColumn`/`ScrollableColumn`: os containers roláveis com a barra de rolagem que o Compose Desktop não desenha |
| `CaptureNoticeWindow.kt` | aviso flutuante de captura e de hover, com ação opcional ao lado de «OK» (`actionLabel`/`onAction`, R59) |
| `i18n/DesktopStrings.kt` | **todas** as strings pt/en do desktop (nada fixo nas telas) |

### Captura de tela

| arquivo | papel |
| --- | --- |
| `ScreenCapture.kt` | captura primária/região/monitor via `Robot`, tamanho nativo em HiDPI, lista de monitores e preferência (`PRIMARY`/`MOUSE`/`DISPLAY:n`); `CaptureDiagnostics` mede a última captura e diz qual transporte a serviu (`lastCaptureRoute`) (D81) |
| `PlatformServices.kt` | escolhe o backend de captura por plataforma/sessão |
| `ScreenshotMethod.kt` | override de método (X11/Wayland/portal) por variável de ambiente |
| `KWinScreenshotCapture.kt` | caminho principal no KDE Wayland: `org.kde.KWin.ScreenShot2`, FD + QImage cru, fallback para o portal |
| `PortalScreenshotCapture.kt` | portal `Screenshot` do KDE (Wayland) via D-Bus; compatibilidade quando o ScreenShot2 não existe |
| `SegmentPickerWindow.kt` | seletor de tela inteira: desenha as caixas e devolve o trecho escolhido |
| `PickerBoxes.kt` / `PickerReadings.kt` / `PickerWindowPlacement.kt` | geometria das caixas, leituras no picker e posicionamento da janela; o rubi não é desenhado sobre a furigana impressa nem sobre outra peça de texto e encolhe para caber na folga antes de desistir (R57, R58, R64) |
| `RegionSelectionWindow.kt` | recorte à mão com alças (`CropHandle`) |

### Hover

| arquivo | papel |
| --- | --- |
| `HoverOcrManager.kt` | ciclo do hover: captura → OCR (motor escolhido, focado no ponteiro) → dicionário → cartão; o gate avança por poll também nos bridges do compositor (`BRIDGE_PUMP_MILLIS`) e desligar o hover devolve o que ele alocou (D94) e faz um trim de heap 1,5 s depois, para o processo não ficar no pico (D95) |
| `HoverDictionaryCache.kt` | os dicionários abertos do hover (~123 MB de índice), reaproveitados enquanto a pasta não muda e liberados no `release()` quando o hover desliga (D94) |
| `PickerDictionaryLease.kt` | o motor de dicionários que o picker empresta de `DesktopDictLoader.cachedEngine` e devolve quando a janela sai de composição (parágrafo, recorte à mão ou Escape), em vez de ficar aberto até o app sair (D97) |
| `HoverGate.kt` / `HoverDismissGate.kt` | quando disparar (parada do ponteiro) e quando fechar; o repouso é preferência (`hover_rest_millis`, padrão 250 ms, presets 150/250/420 nos Ajustes) e vale para a execução em curso (D82); também a retentativa do mesmo ponto quando a leitura falha por captura/OCR, com pausa e teto (D88) |
| `HoverLookupTracker.kt` | qual leitura ainda vale quando o ponteiro segue andando: o mais novo vence, no máximo duas em voo (D83) |
| `LensPayload.kt` | o que sobe para o Lens: PNG até 400 KB, JPEG q85 acima disso — o upload é o gargalo no Deck (D83) |
| `HoverGeometry.kt` | recorte ao redor do ponteiro, coordenadas locais, HiDPI |
| `HoverTextSelector.kt` | escolhe a linha/palavra sob o ponteiro e monta o hit; junta palavra partida entre linhas/colunas do mesmo bloco (D56) |
| `DictionaryInstallRun.kt` | um install do catálogo e o que ele reporta (instalado/cancelado/falhou/encerrado); o reset do cartão roda em `NonCancellable` |
| `RendererRecovery.kt` | marcador de execução em andamento com o pid, escada de renderizador depois de um crash nativo do Skiko/Direct3D (Direct3D → OpenGL → Software) que só conta como crash com `hs_err_pid<pid>.log` do Skiko, `--renderer=…` e a preferência `skiko_renderer` que os Ajustes escrevem (D79/D80); `softwareSwitchOn`/`rendererAfterSoftwareSwitch` — no Linux o cartão é um interruptor só (D83) |
| `HoverLookup.kt` | consulta do hover limitada ao dicionário escolhido (`dictionaryScope`, `scopedHoverHit`), a decisão da troca de dicionário feita no cartão a partir da leitura guardada (`cachedHoverAnswer`) e o que fazer quando não vem verbete (`japaneseWordAt`, `classifyHoverMiss`: só há cartão se houver palavra japonesa sob o ponteiro, e a caixa dela é o âncora) — sem o «fallback» que respondia de outro dicionário (D77, D78) |
| `PickerScanMemory.kt` | scan do picker que sobrevive ao «Trechos»: entregue uma vez e nunca a partir de uma falha (D76) |
| `TrayGlyphs.kt` | ícones do menu da bandeja desenhados com Java2D, em cor (o Swing não pinta emoji colorido) (D76) |
| `HoverTooltipWindow.kt` | cartão flutuante (tamanho, posição, conteúdo); uma única janela para a sessão inteira, apenas escondida entre palavras (`visible`), porque criar e destruir uma `ComposeWindow` por palavra deixava superfícies nativas no alocador (D98); a posição vem no `WindowState` e é reaplicada quando a janela existe, porque o compositor ignora o `setLocation` de uma janela ainda sendo mapeada; uma mudança de altura para o mesmo ponteiro mantém o canto do cartão e só desliza o mínimo para caber na área útil (`HoverGeometry.workAreaAt`), porque reancorar no ponteiro jogava o cartão para cima quando a lista abria perto da barra de tarefas (D102). Não escreve no `background` do AWT: a transparência no Windows depende de o Skiko escolher o fundo por renderizador (D80). «＋ Anki» fica ao lado de cada palavra, e a altura do cartão conta cada aviso pelas linhas que ele ocupa — o de dicionário sem verbete e a resposta do Anki podem aparecer juntos sem cortar a última definição (recebe `showAnki` de quem o coloca) |
| `WindowActivation.kt` | traz para a tela a janela que o usuário pediu (limpa `ICONIFIED`, pede ativação); no hover nunca é usado |
| `WindowPlacement.kt` | canto gravado de uma janela, aceito só enquanto um monitor atual o contém (D103); monitores atuais em coordenadas AWT |
| `HoverCapability.kt` | o hover é suportado nesta sessão? (X11/Wayland/Win; no Wayland decide entre ponteiro do KWin e do GNOME) |
| `PointerSource.kt` | fonte da posição do ponteiro (AWT, script do KWin ou extensão do GNOME) |
| `KWinHoverBridge.kt` / `KWinIntegration.kt` / `KWinScript.kt` | script KWin + D-Bus que entrega o ponteiro no Wayland; descarrega script obsoleto e reaplica atalhos |
| `GnomeHoverBridge.kt` | instala/ativa a extensão `yabai-ocr@yabai.dev` e detecta se o Shell já a carregou (nome D-Bus próprio); mesmo contrato `CursorMoved` do KWin |
| `KGlobalAccel.kt` | esquece o atalho guardado no KGlobalAccel antes de recarregar o script do KWin |

### OCR do desktop

| arquivo | papel |
| --- | --- |
| `DesktopOcr.kt` | **porta única**: ordem dos motores (`AUTO`/`LENS`/`PP_OCR`), fallback e `DesktopOcrAnswer` |
| `DesktopPPOcr.kt` | motor offline: sessões em cache, conversão `PPOcrOutcome` → `LensResult` mantendo as marcas de furigana impressa (R64); a imagem é copiada para um buffer reaproveitado entre leituras (D99) |
| `PixelBuffer.kt` | o `IntArray` de pixels que o `DesktopPPOcr` reusa enquanto o tamanho da imagem não muda — o recorte do hover é a altura inteira do monitor (~8 MB a 1080p) a cada leitura (D99) |
| `DesktopDictLoader.kt` | abre/caches o `LookupEngine` a partir da pasta de dicionários; `installedTitles`/`dictionaryTitle` leem **só o `meta.json`** (o índice de um dicionário tem dezenas de MB, e a lista de nomes do cartão é pedida a cada composição); `sizeBytes` soma o conteúdo de um `.yabai` (que é pasta) |

### Atalhos, bandeja e sistema

| arquivo | papel |
| --- | --- |
| `GlobalHotkeyManager.kt` | registro dos atalhos globais, recriação ao vivo |
| `HotkeyBackend.kt` / `HotkeyMatcher.kt` / `HotkeyBindings.kt` | backend (JNativeHook/portal/KWin), combinação de teclas e catálogo de ações; no KDE Wayland os atalhos são do script do KWin (KGlobalAccel), que não precisa de app id |
| `PortalGlobalShortcuts.kt` | atalhos globais no Wayland fora do KDE via portal D-Bus (o portal exige app id, que um AppImage aberto do arquivo não tem) |
| `XdgTrigger.kt` | atalhos por `gsettings`/XDG no Linux |
| `SystemTrayManager.kt` / `StatusNotifierTray.kt` / `DbusMenu.kt` / `TrayMenu.kt` / `TrayMenuPlacement.kt` / `TrayMenuInvoker.kt` | bandeja: StatusNotifier + DBusMenu em todo Linux (Plasma e GNOME com AppIndicator), AWT no Windows/macOS e como fallback, motivo da ausência em `lastFailure`, menu Swing fallback e posicionamento; o SNI publica `org.freedesktop.DBus.Properties` próprio (`Get`/`GetAll` com `Variant`, porque o `Get` de `@DBusBoundProperty` não devolve `v`); o ícone segue o hover por `IconPixmap`/`IconName` e `NewIcon` |
| `ExternalBrowser.kt` | abre URL no navegador do usuário: lançadores do desktop (`xdg-open`, `gio open`, `kde-open*`) e `Desktop.browse` como último recurso |
| `YabaiDbusService.kt` | serviço D-Bus próprio (hover, atalhos) |
| `StartupManager.kt` / `Autostart.kt` / `WindowsAutostart.kt` / `LinuxAutostart.kt` / `LinuxDesktopEntry.kt` / `LinuxSystemIntegration.kt` | iniciar com o sistema, integração com o desktop e variantes do ícone de hover no tema |
| `ClipboardMonitor.kt` | observa o clipboard (copiar do cartão) |
| `DesktopPlatform.kt` | detecção de SO/sessão (SteamOS, Wayland, X11) |
| `DesktopAppUpdateChecker.kt` | procura release nova e escolhe o asset do SO |
| `DesktopDictionaryCatalog.kt` / `DesktopDictionaryUpdateChecker.kt` / `DictionaryUpdateMonitor.kt` | catálogo de dicionários, comparação de revisões e o laço que checa sozinho (fora da janela) |

### Testes do desktop

`desktop/src/test/kotlin/dev/yabai/ocr/desktop/` — quase um arquivo por classe de produção. Os que
valem destaque:

- `DesktopOcrOrderTest` — ordem dos motores e conversão do resultado do PP-OCR.
- `DesktopEngineMigrationTest` — preferências antigas (`MANGA_OCR`) caem em `AUTO`.
- `DesktopPPOcrEndToEndTest` — **opt-in** (`-Pppocr`), lê uma página desenhada com os modelos reais.
- `PortalGlobalShortcutsTest`, `StatusNotifierTrayTest`, `DbusMenuTest`, `KWinScreenshotCaptureTest`,
  `KWinScriptTest` — D-Bus/Wayland sem sessão.
- `TrayHoverStatusIconTest` — pixels e geometria do selo de hover no ícone da bandeja.
- `NativeRuntimeModulesTest` — o runtime empacotado declara `jdk.net` e `jdk.security.auth`.
- `KWinScreenshotCaptureTest` — também confere que o único transporte UNIX do classpath suporta FD.

---

## 6. `:app` — Android

`app/src/main/java/dev/yabai/ocr/`

### Telas e entrada

| arquivo | papel |
| --- | --- |
| `MainActivity.kt` | ajustes e permissões em quatro abas (**Geral**, **Leitura**, **Dicionários** e **Sobre**), como no desktop: `TabLayout` fixo e uma página disposta por vez (R56); escolhe motor, baixa modelos, inicia o serviço |
| `StartOcrActivity.kt` | atalho/atalho de tela para iniciar a captura |
| `LookupActivity.kt` | abre a busca do dicionário a partir de um texto |
| `CameraActivity.kt` | a câmera da bolha, tela própria com CameraX: pré-visualização, disparo sem confirmação, a foto parada atrás do indicador enquanto o serviço lê e a entrega do arquivo; sai quando os balões estão na tela; o giro da foto segue a orientação do aparelho, não a tela, do disparo e depois de virar o celular; a pré-visualização e a foto parada ficam no quadro do celular, e é esse mesmo quadro que ela entrega ao serviço para a página do seletor; fecha-se quando o usuário vai para a home ou a lista de apps, mas só com a câmera já rodando, porque o diálogo de permissão também anuncia uma saída; mede a tela pelo display inteiro, a mesma medida da página do seletor, e deixa o disparo e o ✕ presos às bordas do celular — embaixo ao centro e no canto superior direito —, trocando só o lado da janela que mostra cada borda conforme a rotação do display, sem girá-los com a foto (R48p); com a janela virada pelo celular, mede o quadro de novo para a câmera continuar sendo a tela inteira, gira o «Reconhecendo…» com esse quadro e tem no indicador o «Cancelar» que avisa o serviço e devolve a pré-visualização (R46d, R47a, R48a, R48d, R48f, R48g, R48h, R48i, R48j, R48k, R48l, R48m, R48p) |
| `DictionaryActivity.kt` | tela de dicionários (instalar, atualizar, filtrar) |
| `HelpActivity.kt` | manual de uso: a referência de botões (uma linha por botão de `item_help_button.xml`, com o ícone da bolha à esquerda) e depois capturas, câmera, bolha e gestos, motores, dicionários, IA e tradução |
| `YabaiOcrApplication.kt` | `Application`: tema, idioma, agendador de atualizações |
| `AppSettings.kt` | `SharedPreferences` tipadas de **todo** o app |
| `UsageTracker.kt` | quantas chamadas ao Lens por dia (não há cota pública) |

### OCR

| arquivo | papel |
| --- | --- |
| `OcrDispatcher.kt` | escolhe o motor: Lens, ML Kit, PP-OCRv6; `AUTO` percorre a prioridade com fallback |
| `OcrEngine.kt` | contrato (`OcrEngine`) e resultado (`OcrOutcome`, `OcrBlock`, `OcrPiece`) |
| `LensOcrEngine.kt` | Lens sobre `Bitmap` (redimensiona, codifica, chama o core) |
| `MlKitOcrEngine.kt` | ML Kit japonês offline, agrupando linhas com `BlockColumns` e marcando a furigana impressa (`mlKitPieces`, R64) |
| `ImagePreparer.kt` | redimensiona/amplia recortes antes do OCR |

### Captura, serviço e overlays

| arquivo | papel |
| --- | --- |
| `capture/ScreenCaptureSession.kt` | `MediaProjection`: uma captura de tela por sessão |
| `capture/CameraPhoto.kt` | foto da câmera de pé pelo EXIF, lida uma vez e apagada; e a versão reduzida que a tela da câmera mostra sem apagar o arquivo (R48a) |
| `capture/DeviceRotation.kt` | o giro da foto a partir da orientação do aparelho: graus do sensor para `Surface.ROTATION_*` (R48f), e o giro que a página do seletor leva para seguir o quadro do celular (R48g) |
| `capture/CameraControls.kt` | onde o disparo e o ✕ ficam na janela para continuarem presos às bordas do celular quando a tela gira (R48p) |
| `capture/ScreenMetrics.kt` | a medida única da tela — o display inteiro, com as barras — usada pela bolha (R23), pela tela da câmera e pela página do seletor (R48j) |
| `capture/PhotoWait.kt` | o limite da espera pela leitura de uma foto na tela da câmera, e o fallback que devolve o controle (R48c) |
| `capture/PhotoPage.kt` | geometria da foto na página do seletor (encaixe uniforme, retângulos, alocação da página), o tamanho da página no quadro do celular (R48g) e o mesmo quadro aplicado às views da tela da câmera (R48h) (R47a, R48g, R48h) |
| `service/FloatingOcrService.kt` | serviço em primeiro plano: captura, OCR, mostra o resultado; toda leitura corre em `startReading`, que guarda o `Job` que «Cancelar» cancela (R48m); a tela girando prende a bolha de novo (`onConfigurationChanged`, R60) |
| `service/LookupOverlayService.kt` | busca do dicionário sobre outras apps |
| `service/YabaiTileService.kt` | bloco de configurações rápidas |
| `overlay/OverlayController.kt` | ciclo de vida dos overlays: bolha (posição, arrasto e clamp na rotação — R51/R60), menus, seletor e painéis; HOME/recentes fecham resultado, kanji e o picker (R60) |
| `overlay/ResultOverlay.kt` | painel de resultado (texto, tradução, ações, dicionário; a espera pelo reconhecimento é a barra fina compartilhada — R48m, R57) |
| `overlay/LoadingBar.kt` | a barra fina da espera (spinner, rótulo com peso e «Cancelar» vermelho ao lado), usada pelo painel e pela câmera (R57) |
| `overlay/TextPickerOverlay.kt` | seletor de trechos sobre a captura, com a página virada para o quadro do celular (R48g), a barra de ações e a dica acompanhando o giro (R48g, R48h) e «Cancelar» vermelho (R60) |
| `overlay/PickerDecision.kt` / `PickerTargets.kt` / `PickerZoom.kt` / `PickerReadingLayout.kt` / `PickerReading.kt` / `PageTurn.kt` | regra de "sempre mostrar o seletor", alvos de toque, zoom, leituras (sem cobrir a furigana impressa nem o texto vizinho, encolhendo para caber na folga — R57, R58; tamanho do rubi pela largura da coluna, sem variar com o número de kana — R60) e o giro da página para o quadro do celular (R48g) |
| `overlay/RegionOverlay.kt` | recorte à mão |
| `overlay/AppPickerOverlay.kt` | escolha do app de IA |
| `overlay/KanjiOverlay.kt` / `KanjiCardText.kt` | card de kanji (traços, leituras, compostos) |
| `overlay/DefinitionStyle.kt` / `ActionGrid.kt` / `SelectionToolbar.kt` / `FlowLayout.kt` / `Widgets.kt` | estilo do glossário, grade de ações, barra de seleção e widgets base |

### Dicionários, IA e tradução

| arquivo | papel |
| --- | --- |
| `update/AppUpdateChecker.kt` | consulta o último release do GitHub, compara com `BuildConfig.VERSION_NAME` e devolve as notas |
| `update/MarkdownView.kt` | desenha as notas (blocos do `:core`) num `TextView` com negrito, código e links clicáveis |
| `update/ApkInstall.kt` | baixa o APK com `DownloadManager` para a pasta de downloads do app e para por aí (não instala nem pede permissão); `ApkDownloadProgress`/`progress` leem os bytes para a barra da tela (R60) |
| `dict/DictionaryCatalog.kt` | catálogo de dicionários disponíveis |
| `dict/DictionaryDownloader.kt` | download/instalação de ZIP |
| `dict/DictionaryRepository.kt` | dicionários instalados, filtro e ordem |
| `dict/DictionarySearch.kt` | busca (termo, leitura, definição) |
| `dict/DictionaryUpdates.kt` | verificação periódica (WorkManager) e notificação |
| `dict/KanjiDefault.kt` | qual dicionário responde pelo card de kanji |
| `ai/AskAi.kt` | monta a pergunta e abre o app de IA escolhido |
| `translate/OfflineTranslator.kt` | tradução offline com ML Kit |
| `anki/AnkiDroidGateway.kt` | o cliente do provider do AnkiDroid (A4): instalação e permissão, baralho e tipo `Yabai` criados quando faltam, campos casados pelo nome, duplicata pelo primeiro campo antes de criar baralho/tipo, inserção com os cartões movidos para o baralho (a nota é apagada se o cartão não se mover) e remoção para o «Desfazer»; fala `content://com.ichi2.anki.flashcards` direto, sem dependência (o `<queries>` e a permissão estão no manifesto) |

### Recursos

`app/src/main/res/`: `layout/activity_main.xml` (cabeçalho, `TabLayout` e as quatro páginas
`pageGeneral`/`pageReading`/`pageDictionaries`/`pageAbout`) e `activity_help.xml`, `values/` (pt-BR),
`values-en/`, `values-night/colors.xml`, `drawable/` (`ic_scan.png` é o ícone do balão;
`ic_stat_scan.xml` é a variante monocrômica da notificação e do tile), `mipmap-anydpi-v26/`.
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

### Captura no KDE Wayland (ScreenShot2)

```
PlatformServices.capture (monitor escolhido em Ajustes: principal, DISPLAY:n ou sob o ponteiro)
    → KdeWaylandCapture → org.kde.KWin.ScreenShot2 (CaptureArea/CaptureWorkspace)
    → entrada oculta yabai-ocr-screenshot.desktop autoriza o binário em execução
    → FD temporário + QImage RGBX8888/RGBA8888 ou ARGB32 (stride × height)
    → BufferedImage; se o ScreenShot2 falhar, PortalScreenshotCapture (fallback, retentado a cada 10 s)
```

### Bandeja no Plasma (SNI + DBusMenu)

```
Plasma → org.kde.StatusNotifierItem (Activate/SecondaryActivate/ContextMenu)
       → com.canonical.dbusmenu GetLayout/AboutToShow/Event
       → SystemTrayManager monta os itens (atalhos, hover, dicionário)
       → Event(clicked) → mesma ação do menu Swing
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
| captura/picker desktop | `ScreenCapture.kt`, `KWinScreenshotCapture.kt`, `SegmentPickerWindow.kt`, `PickerBoxes.kt` |
| hover | `HoverOcrManager.kt`, `HoverGeometry.kt`, `HoverTextSelector.kt`, `HoverLookup.kt` |
| atalhos | `HotkeyBindings.kt`, `HotkeyMatcher.kt`, `GlobalHotkeyManager.kt`; a captura das teclas fica em `SettingsWindow.kt` (`ShortcutsTab`) |
| abas de Ajustes / onde um cartão mora | `SettingsWindow.kt` (`SettingsTab`, `SettingsTabRow`, `TabColumn`) |
| idioma / manual | `SettingsWindow.kt` (`LanguageMenu`) e `HelpWindow.kt` (`HelpContent`), abertos pela janela de `Main.kt` |
| bandeja | `SystemTrayManager.kt`, `StatusNotifierTray.kt`, `DbusMenu.kt`, `TrayMenu*.kt` |
| dicionários (busca/importação) | `core/dict/*`, depois `app/dict/*` ou `desktop/DictionaryManagerWindow.kt` |
| furigana | `core/dict/Furigana.kt`, `core/lens/FuriganaMode.kt`, `overlay/PickerReadingLayout.kt` |
| textos de interface | `desktop/i18n/DesktopStrings.kt`; `app/src/main/res/values*/strings.xml` |
| tema | `desktop/DesktopTheme.kt`; `app/src/main/res/values*/themes.xml` |
| atualização do app | `desktop/DesktopAppUpdateChecker.kt`, `UpdateInstaller.kt`, `UpdatesNoticeWindow.kt`; `app/update/AppUpdateChecker.kt`, `ApkInstall.kt` |
| atualização dos dicionários | `desktop/DictionaryUpdateMonitor.kt` (`dictionaryAnnouncement`: quando avisar, quando calar e quando limpar o tooltip da bandeja), `DesktopDictionaryUpdateChecker.kt` (a mesma checagem do aviso e do botão «Verificar Atualizações»), `DesktopDictionaryCatalog.kt` (`catalogEntryForDictionaryFile`), `UpdatesNoticeWindow.kt`, `DictionaryManagerWindow.kt` (`downloadAndInstallDictionary`); `app/dict/DictionaryUpdates.kt` |

---

## 9. Convenções e armadilhas

- **Texto reconhecido é dado.** Não corrigir, completar ou "melhorar" caractere reconhecido.
- **Toda string de UI existe em todos os idiomas disponíveis** (hoje pt-BR e en), e o idioma é
  escolhido pelo usuário nos Ajustes; comentários de código e nomes de API em inglês.
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
| [README.md](README.md) / [README.pt-BR.md](README.pt-BR.md) | quem quer instalar e usar |
| **ARCHITECTURE.md** | quem vai mexer no código |
| [ROADMAP.md](docs/ROADMAP.md) | histórico técnico: causa raiz, correção, testes, evidência |
| [LANGUAGES.md](LANGUAGES.md) | i18n: chaves, onde vivem, o que falta traduzir |
| [LINUX.md](docs/LINUX.md) / [STEAMOS.md](docs/STEAMOS.md) | Linux e Steam Deck |
| [AGENTS.md](AGENTS.md) | regras de trabalho no repositório (agentes e pessoas) |
| [.github/agents/yabai.md](.github/agents/yabai.md) / [.github/skills](.github/skills/) | persona do engenheiro e skills do projeto, no formato do GitHub Copilot |
| [CONTRIBUTING.md](CONTRIBUTING.md) / [CONTRIBUTING.en.md](CONTRIBUTING.en.md) | como compilar, testar e enviar uma mudança |
| [ACCESSIBILITY_SERVICE_GUIDE.md](ACCESSIBILITY_SERVICE_GUIDE.md) | serviço de acessibilidade |
