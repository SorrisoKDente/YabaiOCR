package dev.yabai.ocr.desktop.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.yabai.ocr.desktop.DesktopLanguageChoice
import dev.yabai.ocr.desktop.DesktopSettings
import java.util.Locale

object DesktopStrings {
    var currentChoice by mutableStateOf(DesktopLanguageChoice.SYSTEM)
        private set

    fun updateLanguage(choice: DesktopLanguageChoice) {
        currentChoice = choice
    }

    val isEnglish: Boolean
        get() = when (currentChoice) {
            DesktopLanguageChoice.EN_US -> true
            DesktopLanguageChoice.PT_BR -> false
            DesktopLanguageChoice.SYSTEM -> Locale.getDefault().language.equals("en", ignoreCase = true)
        }

    fun init(settings: DesktopSettings) {
        updateLanguage(settings.appLanguage)
    }

    // Common / General
    val appTitle: String get() = if (isEnglish) "Yabai OCR Desktop" else "Yabai OCR Desktop"
    val settingsWindowTitle: String get() = if (isEnglish) "Yabai OCR - Settings & Options" else "Yabai OCR - Ajustes e Configurações"
    val dictManagerTitle: String get() = if (isEnglish) "Dictionary Manager" else "Gerenciador de Dicionários"
    val resultTitle: String get() = if (isEnglish) "Recognized Text" else "Texto reconhecido"
    val quickDictTitle: String get() = if (isEnglish) "Quick Dictionary Search" else "Busca Rápida de Dicionário"
    val close: String get() = if (isEnglish) "Close" else "Fechar"
    val cancel: String get() = if (isEnglish) "Cancel" else "Cancelar"
    val confirm: String get() = if (isEnglish) "Confirm" else "Confirmar"
    val remove: String get() = if (isEnglish) "Remove" else "Remover"
    val active: String get() = if (isEnglish) "Active" else "Ativo"
    val disabled: String get() = if (isEnglish) "Disabled" else "Desativado"
    val available: String get() = if (isEnglish) "Available" else "Disponível"

    // Tray Menu
    val trayFullScreen: String get() = if (isEnglish) "🖥️ Full Screen Capture (OCR)" else "🖥️ Capturar Tela Inteira (OCR)"
    val trayRegion: String get() = if (isEnglish) "✂️ Select Region (OCR)" else "✂️ Capturar Área (OCR)"
    val trayQuickDict: String get() = if (isEnglish) "🔍 Quick Dictionary Search" else "🔍 Busca Rápida de Dicionário"
    val traySettings: String get() = if (isEnglish) "⚙️ Settings & Options" else "⚙️ Ajustes e Configurações"
    val trayManageDicts: String get() = if (isEnglish) "📖 Manage Dictionaries" else "📖 Gerenciar Dicionários"
    fun trayHover(active: Boolean): String = if (isEnglish) {
        if (active) "💬 Hover Mode [Active]" else "💬 Hover Mode [Inactive]"
    } else {
        if (active) "💬 Modo Hover [Ativo]" else "💬 Modo Hover [Inativo]"
    }
    val trayExit: String get() = if (isEnglish) "✕ Exit Yabai OCR" else "✕ Sair do Yabai OCR"

    // Settings Window - Sections
    val sectionAppearance: String get() = if (isEnglish) "Appearance & Theme" else "Aparência e Tema"
    val sectionLanguage: String get() = if (isEnglish) "Interface Language" else "Idioma da Interface"
    val sectionHotkeys: String get() = if (isEnglish) "Global Keyboard Shortcuts" else "Atalhos Globais de Teclado"
    val sectionLinuxIntegration: String get() = if (isEnglish) "Linux System Integration" else "Integração com o Linux"
    val sectionStartup: String get() = if (isEnglish) "Startup & Window Behaviour" else "Inicialização e Comportamento da Janela"
    val sectionHover: String get() = if (isEnglish) "Hover Mode — Real-time Reading (Yomitan Style)" else "Modo Hover — leitura em tempo real (Estilo Yomitan)"
    val trayUnavailableNoWatcher: String get() = if (isEnglish) "Tray unavailable: this session has no StatusNotifier host. On GNOME (Ubuntu) install the AppIndicator extension; Plasma shows the icon normally. Until then use the global shortcuts and this window." else "Bandeja indisponível: esta sessão não tem host StatusNotifier. No GNOME (Ubuntu) instale a extensão AppIndicator; no Plasma o ícone aparece normalmente. Até lá, use os atalhos globais e esta janela."
    val trayUnavailableDesktop: String get() = if (isEnglish) "Tray unavailable in this session (no StatusNotifier and no AWT tray). Use the global shortcuts and this window." else "Bandeja indisponível nesta sessão (sem StatusNotifier e sem bandeja AWT). Use os atalhos globais e esta janela."
    val trayAddFailed: String get() = if (isEnglish) "Could not add the icon to the tray. Use the global shortcuts and this window." else "Não foi possível adicionar o ícone à bandeja. Use os atalhos globais e esta janela."
    val sectionCapture: String get() = if (isEnglish) "Screen Capture — Which Monitor" else "Captura de Tela — Qual Monitor"
    val captureMonitorDesc: String get() = if (isEnglish) "Monitor used by the full-screen capture and by the hand-drawn crop." else "Monitor usado pela captura de tela inteira e pelo recorte à mão."
    val captureMonitorPrimary: String get() = if (isEnglish) "Primary monitor" else "Monitor principal"
    val captureMonitorMouse: String get() = if (isEnglish) "Monitor under the pointer" else "Monitor onde está o ponteiro"
    fun captureMonitorDisplay(label: String, width: Int, height: Int): String = "$label ($width×$height)"
    val captureMonitorHint: String get() = if (isEnglish) "Hover always follows the pointer's monitor. On Wayland the selection window opens on the screen the app is on, because the compositor ignores absolute placement." else "O hover sempre segue o monitor do ponteiro. No Wayland a janela de seleção abre na tela em que o app está, porque o compositor ignora o posicionamento absoluto."
    val sectionRenderer: String get() = if (isEnglish) "Graphics Renderer" else "Renderizador gráfico"
    val rendererSubtitle: String get() = if (isEnglish) {
        "How the app's windows are drawn."
    } else {
        "Como as janelas do app são desenhadas."
    }
    val rendererAutomatic: String get() = if (isEnglish) "Automatic" else "Automático"
    val rendererAutomaticDesc: String get() = if (isEnglish) {
        "The system's default (Direct3D on Windows). Fastest, and the one that crashes on some drivers."
    } else {
        "O padrão do sistema (Direct3D no Windows). Mais rápido, e o que cai em alguns drivers."
    }
    val rendererOpenGl: String get() = "OpenGL"
    val rendererOpenGlDesc: String get() = if (isEnglish) {
        "Uses the graphics card by another path; avoids Direct3D."
    } else {
        "Usa a placa de vídeo por outro caminho; evita o Direct3D."
    }
    val rendererSoftware: String get() = "Software"
    val rendererSoftwareDesc: String get() = if (isEnglish) {
        "Draws on the CPU. Slower, and the most stable."
    } else {
        "Desenha na CPU. Mais lento, e o mais estável."
    }
    val rendererRestartNote: String get() = if (isEnglish) {
        "The change applies the next time the app starts (also available as --renderer=automatic|opengl|software)."
    } else {
        "A mudança vale na próxima abertura do app (também dá para escolher com --renderer=automatic|opengl|software)."
    }

    /** Shown under the switch on Linux, where «Automatic» and «OpenGL» are the same thing. */
    val rendererLinuxNote: String get() = if (isEnglish) {
        "On Linux there is no Direct3D: the app draws with OpenGL (graphics card) by default."
    } else {
        "No Linux não existe Direct3D: o app desenha com OpenGL (placa de vídeo) por padrão."
    }

    /** The one renderer choice the Linux card offers; on Windows/macOS the three radios stay. */
    val rendererSoftwareToggle: String get() = if (isEnglish) {
        "Software rendering"
    } else {
        "Renderização por software"
    }

    val rendererSoftwareToggleDesc: String get() = if (isEnglish) {
        "On: the app draws on the CPU, which is slower but works when the graphics card does not " +
            "draw the windows. Off: it draws with the graphics card (the default)."
    } else {
        "Ligado: o app desenha na CPU, que é mais lento mas funciona quando a placa de vídeo não " +
            "desenha as janelas. Desligado: desenha com a placa de vídeo (o padrão)."
    }

    fun rendererAfterCrash(level: String): String = if (isEnglish) {
        "The previous run was closed by a native crash of the graphics renderer (Skiko), so this run uses $level. Pick Automatic here when you want to go back."
    } else {
        "A execução anterior foi fechada por um crash nativo do renderizador gráfico (Skiko), então esta execução usa $level. Escolha Automático aqui quando quiser voltar."
    }

    fun rendererDescription(level: dev.yabai.ocr.desktop.SkikoRenderer): String = when (level) {
        dev.yabai.ocr.desktop.SkikoRenderer.AUTOMATIC -> rendererAutomaticDesc
        dev.yabai.ocr.desktop.SkikoRenderer.OPENGL -> rendererOpenGlDesc
        dev.yabai.ocr.desktop.SkikoRenderer.SOFTWARE -> rendererSoftwareDesc
    }
    fun rendererName(level: dev.yabai.ocr.desktop.SkikoRenderer): String = when (level) {
        dev.yabai.ocr.desktop.SkikoRenderer.AUTOMATIC -> rendererAutomatic
        dev.yabai.ocr.desktop.SkikoRenderer.OPENGL -> rendererOpenGl
        dev.yabai.ocr.desktop.SkikoRenderer.SOFTWARE -> rendererSoftware
    }
    val sectionEngine: String get() = if (isEnglish) "Recognition Engine" else "Motor de reconhecimento"
    val sectionBehaviour: String get() = if (isEnglish) "OCR & Result Behaviour" else "Comportamento do OCR e resultado"
    val sectionDicts: String get() = if (isEnglish) "Japanese Dictionaries & Data Folder" else "Dicionários de Japonês e Pasta de Dados"
    val sectionTranslation: String get() = if (isEnglish) "Sentence Translation" else "Tradução de Frases"
    val sectionAi: String get() = if (isEnglish) "Ask an AI" else "Perguntar a uma IA"
    val sectionAbout: String get() = if (isEnglish) "About Yabai OCR & App Updates" else "Sobre o Yabai OCR e Atualizações do Aplicativo"

    // Language Section
    val langSystem: String get() = if (isEnglish) "System Default" else "Padrão do Sistema"
    val langPtBr: String get() = if (isEnglish) "Portuguese (Brasil)" else "Português (Brasil)"
    val langEnUs: String get() = if (isEnglish) "English" else "Inglês"

    // Appearance & Theme
    val themeDark: String get() = if (isEnglish) "Dark Theme 🌙" else "Tema Escuro 🌙"
    val themeLight: String get() = if (isEnglish) "Light Theme ☀️" else "Tema Claro ☀️"

    // Hotkeys
    val hkFullScreen: String get() = if (isEnglish) "Full Screen Capture Shortcut (OCR):" else "Atalho da Captura da Tela Inteira (OCR):"
    val hkRegion: String get() = if (isEnglish) "Region Selection Shortcut (OCR):" else "Atalho do Recorte de Área (OCR):"
    val hkQuickDict: String get() = if (isEnglish) "Quick Dictionary Search Shortcut:" else "Atalho de Busca Rápida de Dicionário:"
    val hkHoverToggle: String get() = if (isEnglish) "Toggle Hover Mode Shortcut:" else "Atalho de Ligar/Desligar Modo Hover:"
    val hkHoverCycle: String get() = if (isEnglish) "Cycle Hover Dictionary Shortcut:" else "Atalho para Alternar Dicionário no Hover:"
    val btnCapture: String get() = if (isEnglish) "Capture" else "Capturar"
    val btnDefault: String get() = if (isEnglish) "Default" else "Padrão"
    val btnPressKeys: String get() = if (isEnglish) "Press keys..." else "Aguardando..."

    // Startup
    val startOnBoot: String get() = if (isEnglish) "Start minimized in tray on system boot" else "Iniciar na bandeja ao ligar o computador"
    val closeToTray: String get() = if (isEnglish) "Keep running in tray when closing window" else "Manter na bandeja ao fechar a janela"

    // Hover
    val hoverActive: String get() = if (isEnglish) "🟢 Active — hover over Japanese text." else "🟢 Ativo — pare o mouse sobre um texto japonês."
    val hoverInactive: String get() = if (isEnglish) "⚪ Inactive — enable to translate on hover." else "⚪ Inativo — ative para traduzir só passando o mouse."
    val hoverDefaultDict: String get() = if (isEnglish) "Default Hover Dictionary:" else "Dicionário Padrão do Modo Hover:"
    val hoverAllDicts: String get() = if (isEnglish) "🌐 All Dictionaries (Default)" else "🌐 Todos os Dicionários (Padrão)"

    // Engine
    val engineAuto: String get() = if (isEnglish) "Automatic (Use Priority Order)" else "Automático (Usa Ordem de Prioridades)"
    val enginePpOcr: String get() = if (isEnglish) "PP-OCRv6 Only (offline)" else "Somente PP-OCRv6 (offline)"
    val engineLens: String get() = if (isEnglish) "Google Lens Only (online)" else "Somente Google Lens (online)"
    val ppOcrDownload: String get() = if (isEnglish) "Download PP-OCRv6 models (~23 MB)" else "Baixar modelos do PP-OCRv6 (~23 MB)"
    fun ppOcrDownloading(percent: Int): String = if (isEnglish) "Downloading… $percent%" else "Baixando… $percent%"
    val ppOcrInstalled: String get() = if (isEnglish) "Models installed — offline reading ready." else "Modelos instalados — leitura offline pronta."
    val ppOcrNotInstalled: String get() = if (isEnglish) "Models not installed — download them to read offline." else "Modelos não instalados — baixe para ler offline."
    val ppOcrDownloadFailed: String get() = if (isEnglish) "Download failed: " else "Falha ao baixar: "
    val ppOcrMissingModels: String get() = if (isEnglish) "PP-OCRv6 models not installed. Download them in Settings." else "Modelos do PP-OCRv6 não instalados. Baixe nos Ajustes."
    val ocrNoText: String get() = if (isEnglish) "no text found" else "nenhum texto encontrado"
    val ocrNoEngine: String get() = if (isEnglish) "No engine could read the image — " else "Nenhum motor conseguiu ler a imagem — "
    val enginePriorityTitle: String get() = if (isEnglish) "Engine Fallback Priority Order:" else "Prioridade dos Motores no modo Automático / Fallback:"
    val btnMoveUp: String get() = if (isEnglish) "▲ Up" else "▲ Subir"
    val btnMoveDown: String get() = if (isEnglish) "▼ Down" else "▼ Descer"

    // Behaviour
    val autoCopy: String get() = if (isEnglish) "Copy automatically after recognition" else "Copiar automaticamente ao reconhecer"
    val showSegments: String get() = if (isEnglish) "List text line by line" else "Listar o texto linha por linha"
    val upscaleSmall: String get() = if (isEnglish) "Upscale small image crops" else "Ampliar recortes pequenos"
    val showImage: String get() = if (isEnglish) "Show captured image next to text" else "Mostrar imagem capturada ao lado do texto"
    val furiganaParentheses: String get() = if (isEnglish) "Keep original printed furigana in parentheses" else "Manter furigana original entre parênteses"
    val showFurigana: String get() = if (isEnglish) "Generate dictionary furigana in panel" else "Gerar furigana do dicionário no painel"
    val pickerReadingTitle: String get() = if (isEnglish) "Kanji readings on page picker:" else "Leituras dos kanjis no seletor da página:"
    val pickerReadingOff: String get() = if (isEnglish) "Disabled" else "Desativado"
    val pickerReadingRuby: String get() = if (isEnglish) "Ruby above text" else "Rubi sobre o texto"
    val pickerReadingLabel: String get() = if (isEnglish) "Legible side label" else "Faixa indicativa legível"

    // Dictionaries
    val dictionaryLookup: String get() = if (isEnglish) "Query installed dictionaries" else "Consultar dicionários instalados"
    val defaultDictFilter: String get() = if (isEnglish) "Default Dictionary Filter:" else "Dicionário Padrão de Filtro:"
    val dictFolderLocation: String get() = if (isEnglish) "Dictionary Folder Location:" else "Localização da Pasta de Dicionários:"
    val btnChangeFolder: String get() = if (isEnglish) "Change Folder..." else "Alterar Pasta..."
    val btnRestoreDefaultFolder: String get() = if (isEnglish) "Restore Default" else "Restaurar Padrão"
    val btnManageDicts: String get() = if (isEnglish) "Manage" else "Gerenciar"
    val keepLastSearch: String get() = if (isEnglish) "Keep last dictionary query on reopen" else "Manter última busca no dicionário"
    val stripSearchReadings: String get() = if (isEnglish) "Ignore readings in parentheses during search" else "Ignorar leituras entre parênteses na consulta"
    val dictUpdateInterval: String get() = if (isEnglish) "Automatic dictionary update check:" else "Verificação de atualizações dos dicionários:"
    val updateOff: String get() = if (isEnglish) "Disabled" else "Desativado"
    val updateDaily: String get() = if (isEnglish) "Daily" else "Diariamente"
    val updateWeekly: String get() = if (isEnglish) "Weekly" else "Semanalmente"
    val updateMonthly: String get() = if (isEnglish) "Monthly" else "Mensalmente"

    // Translation
    val translateSentence: String get() = if (isEnglish) "Translate recognized sentence" else "Traduzir a frase reconhecida"
    val translateTarget: String get() = if (isEnglish) "Translation target language:" else "Idioma de destino da tradução:"

    // AI
    val showAiButton: String get() = if (isEnglish) "Show 'Ask AI' button in result panel" else "Exibir o botão «Perguntar à IA» no painel de resultado"
    val aiProvider: String get() = if (isEnglish) "AI Provider / Service:" else "Provedor / Serviço de IA:"
    val aiPrompt: String get() = if (isEnglish) "Initial AI Prompt:" else "Prompt / Pergunta inicial enviada à IA:"

    // About & Updates
    val installedVersion: String get() = if (isEnglish) "Installed Version:" else "Versão Instalada:"
    val btnCheckUpdates: String get() = if (isEnglish) "🔄 Check for Updates" else "🔄 Verificar Atualizações"
    val updateAvailable: String get() = if (isEnglish) "Update Available" else "Atualização Disponível"
    val btnDownloadUpdate: String get() = if (isEnglish) "⬇️ Download Update" else "⬇️ Baixar Atualização"

    // Dict Manager Window
    val searchHeadwords: String get() = if (isEnglish) "🔤 Headword" else "🔤 Palavra"
    val searchDefinitions: String get() = if (isEnglish) "🔄 Definition" else "🔄 Tradução"
    val allDictionaries: String get() = if (isEnglish) "🌐 All Dictionaries" else "🌐 Todos os Dicionários"
    val searchPlaceholderHeadword: String get() = if (isEnglish) "Search word, kanji, or reading…" else "Pesquisar palavra, kanji ou leitura…"
    val searchPlaceholderDefinition: String get() = if (isEnglish) "Search by meaning (e.g., sword)…" else "Pesquisar pelo sentido da tradução (ex.: espada)"
    val btnChooseZip: String get() = if (isEnglish) "Choose .zip file" else "Escolher arquivo .zip"
    val localDictsHeader: String get() = if (isEnglish) "Local Dictionaries & Updates" else "Dicionários Locais e Atualizações"
    val recommendedDictsHeader: String get() = if (isEnglish) "Recommended Dictionaries" else "Dicionários Recomendados"
    val legalCreditsToggleClosed: String get() = if (isEnglish) "▸ View legal credits and dictionary licenses" else "▸ Ver créditos legais e licenças dos dicionários"
    val legalCreditsToggleOpen: String get() = if (isEnglish) "▾ Hide legal credits and dictionary licenses" else "▾ Ocultar créditos legais e licenças dos dicionários"

    // Result Window
    val btnCopyOriginal: String get() = if (isEnglish) "Copy Original" else "Copiar original"
    val btnCopyTranslation: String get() = if (isEnglish) "Copy Translation" else "Copiar tradução"
    val btnAskAi: String get() = if (isEnglish) "Ask AI" else "Perguntar à IA"
    val btnImage: String get() = if (isEnglish) "Image" else "Imagem"
    val btnManualCrop: String get() = if (isEnglish) "Manual Crop" else "Recortar à mão"
    val btnTryOffline: String get() = if (isEnglish) "Try Offline" else "Tentar offline"

    // Hover Tooltip
    val noMatchFound: String get() = if (isEnglish) "No dictionary match found" else "Sem verbete encontrado"

    val heroSubtitle: String get() = if (isEnglish) "Read Japanese in any PC application — browser, manga readers, games, or PDFs. Capture screen area and get instant readings, dictionaries, and translation." else "Leia japonês em qualquer aplicativo no seu PC — navegador, leitores de mangá, jogos ou PDFs. Capture a área da tela e obtenha leituras, dicionários e tradução instantânea."
    val chooseThemeDesc: String get() = if (isEnglish) "Choose the display theme of the application:" else "Escolha o tema de exibição do aplicativo:"
    val darkThemeDesc: String get() = if (isEnglish) "Ideal for night reading and high contrast on dark screens." else "Ideal para leitura noturna e contraste alto em telas escuras."
    val lightThemeDesc: String get() = if (isEnglish) "Ideal for bright environments and light background documents." else "Ideal para ambientes iluminados e documentos em fundo claro."
    val chooseLanguageDesc: String get() = if (isEnglish) "Choose the display language for the application interface:" else "Escolha o idioma de exibição da interface do aplicativo:"
    val langSystemDesc: String get() = if (isEnglish) "Operating system default." else "Padrão do sistema operacional."
    val langPtBrDesc: String get() = if (isEnglish) "Interface in Portuguese (Brazil)." else "Interface em Português (Brasil)."
    val langEnUsDesc: String get() = if (isEnglish) "Interface in English." else "Interface em Inglês."
    val hotkeyActiveStatusLabel: String get() = if (isEnglish) "Active global shortcuts (JNativeHook)." else "Atalhos globais ativos (JNativeHook)."
    val btnDisable: String get() = if (isEnglish) "Disable" else "Desativar"
    val btnEnable: String get() = if (isEnglish) "Enable" else "Ativar"
    val startOnBootDesc: String get() = if (isEnglish) "Opens the application minimized in the system tray automatically when the operating system starts." else "Abre o aplicativo minimizado na bandeja do sistema automaticamente quando o sistema operacional inicia."
    val closeToTrayDesc: String get() = if (isEnglish) "When closing the main window, the program continues running in the background in the tray to respond to keyboard shortcuts. If disabled, the application will exit completely." else "Ao fechar a janela principal, o programa continua rodando em segundo plano na bandeja para responder aos atalhos de teclado. Se desativado, o aplicativo será encerrado por completo."
    val hoverExplanation: String get() = if (isEnglish) "This mode works in any application: browser, manga reader, PDF, or game. Once enabled, just pause the pointer for a moment over a word; the card with reading and definition appears next to the cursor. Moving the mouse away closes the card." else "O modo funciona em qualquer aplicativo: navegador, leitor de mangá, PDF ou jogo. Depois de ativado, basta deixar o ponteiro parado por um instante sobre a palavra; o cartão com leitura e definição aparece ao lado do cursor. Mover o mouse para fora fecha o cartão."
    fun hoverShortcutsNotice(hover: String, cycle: String): String = if (isEnglish) "Shortcut to toggle: $hover • Cycle dictionary: $cycle" else "Atalho para ligar/desligar: $hover • Alternar dicionário: $cycle"
    val engineSubtitle: String get() = if (isEnglish) "Choose how Japanese text is recognized from the image." else "Escolha como o texto em japonês será reconhecido da imagem."
    val engineAutoDesc: String get() = if (isEnglish) "Tries engines in priority order (Google Lens, offline PP-OCRv6)." else "Tenta motores em ordem de prioridade (Google Lens, PP-OCRv6 offline)."
    val enginePpOcrDesc: String get() = if (isEnglish) "Offline engine for manga: balloon detection plus fast line reading. Detects vertical and horizontal text." else "Motor offline para mangá: detecta os balões e lê as linhas rápido. Reconhece texto vertical e horizontal."
    val engineLensDesc: String get() = if (isEnglish) "Uses the online Google Lens engine. Requires active internet connection." else "Usa o motor online do Google Lens. Exige conexão ativa com a internet."
    val enginePriorityDesc: String get() = if (isEnglish) "Define the preference order of recognition engines." else "Defina a ordem de preferência dos motores de reconhecimento."

    // Result window
    val resultWindowTitle: String get() = if (isEnglish) "Yabai OCR - Recognition Result" else "Yabai OCR - Resultado do Reconhecimento"
    val resultFromPicker: String get() = if (isEnglish) "Passage chosen in the screen picker" else "Trecho escolhido no seletor de tela"
    fun resultRecognizedIn(elapsedMs: Long, engine: String): String = if (isEnglish)
        "Recognized in ${elapsedMs}ms • $engine"
    else
        "Reconhecido em ${elapsedMs}ms • $engine"
    val btnShowImage: String get() = if (isEnglish) "🖼️ Image" else "🖼️ Imagem"
    val btnHideImage: String get() = if (isEnglish) "🖼️ Hide" else "🖼️ Ocultar"
    val btnCopyText: String get() = if (isEnglish) "📋 Copy" else "📋 Copiar"
    val btnAskAiCompact: String get() = if (isEnglish) "🤖 AI" else "🤖 IA"
    val checkUpdatesOnStart: String get() = if (isEnglish) "Check for updates when the app starts" else "Verificar atualizações ao abrir o aplicativo"
    val checkUpdatesOnStartDesc: String get() = if (isEnglish) "Looks at the latest GitHub release as soon as the app opens and tells you when there is a new version." else "Consulta o último release do GitHub assim que o app abre e avisa quando há uma versão nova."
    val btnViewReleaseNotes: String get() = if (isEnglish) "📄 Release notes" else "📄 Notas da versão"
    fun releaseNotesTitle(version: String): String = if (isEnglish) "Release notes — $version" else "Notas da versão — $version"
    val releaseNotesEmpty: String get() = if (isEnglish) "This release has no notes." else "Este release não tem notas."
    val updateLinkOpenFailed: String get() = if (isEnglish) "Could not open the browser for the download link." else "Não foi possível abrir o navegador para o link de download."
    val aiLinkOpenFailed: String get() = if (isEnglish) "Could not open the browser. The link was copied to the clipboard." else "Não foi possível abrir o navegador. O link foi copiado para a área de transferência."
    val btnCropAgain: String get() = if (isEnglish) "✂️ New crop" else "✂️ Recortar"
    val btnSnippets: String get() = if (isEnglish) "Snippets" else "Trechos"
    val resultEditLabel: String get() = if (isEnglish) "Recognized text (edit or select):" else "Texto Reconhecido (edite ou selecione):"
    val ocrFailedGeneric: String get() = if (isEnglish) "Could not recognize the image." else "Erro ao reconhecer a imagem."
    val flexionPrefix: String get() = if (isEnglish) "Inflection: " else "Flexão: "
    val autoCopyDesc: String get() = if (isEnglish) "Sends recognized text directly to the system clipboard." else "Envia o texto reconhecido diretamente para a área de transferência do sistema."
    val showSegmentsDesc: String get() = if (isEnglish) "Displays recognized text segments and blocks separately in the panel." else "Exibe os segmentos e blocos de texto reconhecidos separadamente no painel."
    val upscaleSmallDesc: String get() = if (isEnglish) "Automatically resizes small cropped images to improve recognition of small text." else "Redimensiona automaticamente imagens recortadas pequenas para melhorar o reconhecimento de letras miúdas."
    val showImageDesc: String get() = if (isEnglish) "Displays the original image crop in the result panel for visual comparison." else "Exibe o recorte da imagem original no painel de resultado para comparação visual."
    val furiganaParenthesesDesc: String get() = if (isEnglish) "Preserves the reading printed next to kanji in manga in the format 職員（しょくいん）." else "Preserva a leitura que o mangá imprime ao lado do kanji no formato 職員（しょくいん）."
    val showFuriganaDesc: String get() = if (isEnglish) "Displays dictionary readings above or beside recognized kanji." else "Exibe acima ou ao lado dos kanjis reconhecidos as leituras fornecidas pelos dicionários."
    val pickerReadingOffDesc: String get() = if (isEnglish) "Does not display readings over the image on the selection screen." else "Não exibe leituras sobre a imagem na tela de seleção."
    val pickerReadingRubyDesc: String get() = if (isEnglish) "Draws furigana in small font directly above each kanji." else "Desenha o furigana em fonte pequena diretamente acima de cada kanji."
    val pickerReadingLabelDesc: String get() = if (isEnglish) "Displays a highlighted text strip next to speech bubbles." else "Exibe uma faixa de texto destacada ao lado dos balões de fala."
    val dictionaryLookupDesc: String get() = if (isEnglish) "Automatically searches recognized text in enabled dictionaries." else "Busca automaticamente o texto reconhecido nos dicionários ativados."
    val defaultDictFilterDesc: String get() = if (isEnglish) "Select a specific dictionary to limit searches or choose 'All'." else "Selecione um dicionário específico para limitar as buscas ou escolha 'Todos'."
    val dictSummaryNone: String get() = if (isEnglish) "No dictionaries found in current folder" else "Nenhum dicionário encontrado na pasta atual"
    fun dictSummaryCount(count: Int): String = if (isEnglish) "$count dictionary(ies) found in current folder" else "$count dicionário(s) encontrado(s) na pasta atual"
    val dictSummaryNote: String get() = if (isEnglish) ".yabai files or dictionary subfolders." else "Arquivos .yabai ou subpastas de dicionário."
    val keepLastSearchDesc: String get() = if (isEnglish) "When opening the quick search window, fills the field with the last searched term." else "Ao abrir a janela de busca rápida, preenche o campo com o último termo pesquisado."
    val stripSearchReadingsDesc: String get() = if (isEnglish) "Removes kana segments in parentheses when searching (from 「職員（しょくいん）」 searches 「職員」)." else "Remove trechos em kana entre parênteses ao pesquisar (de 「職員（しょくいん）」 busca 「職員」)."
    val translateSentenceDesc: String get() = if (isEnglish) "Automatically translates the complete recognized text using the offline model." else "Traduz automaticamente o texto completo reconhecido usando o modelo offline."
    val showAiButtonDesc: String get() = if (isEnglish) "Provides a shortcut in the OCR result to send the phrase directly to an Artificial Intelligence service in the browser." else "Disponibiliza um atalho no resultado do OCR para enviar a frase diretamente a um serviço de Inteligência Artificial no navegador."
    val aiPromptDesc: String get() = if (isEnglish) "The instruction placed before the cropped Japanese text." else "A instrução colocada antes do texto em japonês recortado."
    val linuxIntegrationDesc: String get() = if (isEnglish) "Creates the application menu shortcut and installs the Yabai OCR icon for your user." else "Cria o atalho no menu de aplicativos e instala o ícone do Yabai OCR para o seu usuário."
    val btnIntegrateSystem: String get() = if (isEnglish) "Integrate to system" else "Integrar ao sistema"
    val aboutSubtitle: String get() = if (isEnglish) "Yabai OCR Desktop — Japanese OCR Reader with Offline Dictionaries" else "Yabai OCR Desktop — Leitor OCR de Japonês com Dicionários Offline"
    val checkingUpdates: String get() = if (isEnglish) "Checking GitHub releases..." else "Verificando lançamentos no GitHub..."
    val updateConnectError: String get() = if (isEnglish) "Could not connect to GitHub to check for updates." else "Não foi possível conectar ao GitHub para verificar atualizações."
    fun upToDateMessage(version: String): String = if (isEnglish) "You are already using the latest version ($version)." else "Você já está usando a versão mais recente ($version)."
    fun updateAvailableMessage(version: String): String = if (isEnglish) "✨ New app version available: v$version" else "✨ Nova versão do aplicativo disponível: v$version"
    val updateGeneralNotice: String get() = if (isEnglish) "A newer version of Yabai OCR has been released on GitHub." else "Uma versão mais recente do Yabai OCR foi lançada no GitHub."

    // One instance at a time
    val alreadyRunningTitle: String get() = if (isEnglish) "Yabai OCR is already running" else "O Yabai OCR já está aberto"
    val alreadyRunningBody: String get() = if (isEnglish)
        "Yabai OCR is already running in this session. Open the settings from the tray icon — a second copy would take over the same D-Bus service and lose hover and the shortcuts."
    else
        "O Yabai OCR já está rodando nesta sessão. Abra os ajustes pelo ícone da bandeja — uma segunda cópia disputaria o mesmo serviço D-Bus e ficaria sem hover e sem atalhos."
    val dbusNameTaken: String get() = if (isEnglish)
        "Another Yabai OCR instance already owns the dev.yabai.Ocr service. Close it (tray → Exit) and open the app again."
    else
        "Outra instância do Yabai OCR já é dona do serviço dev.yabai.Ocr. Feche-a (bandeja → Sair) e abra o app de novo."

    // Supporting the project
    val donationTitle: String get() = if (isEnglish) "Support the project" else "Apoiar o projeto"
    val donationBody: String get() = if (isEnglish)
        "Yabai OCR is free and non-commercial, made by one person to study Japanese. If it helps you, a donation pays for the time it takes — never a requirement to use it."
    else
        "O Yabai OCR é gratuito e sem fins lucrativos, feito por uma pessoa para estudar japonês. Se ele te ajuda, uma doação paga o tempo que ele dá — e nunca é condição para usar."
    val btnKofi: String get() = if (isEnglish) "☕ Ko-fi" else "☕ Ko-fi"
    val btnCopyPix: String get() = if (isEnglish) "📋 Copy Pix key" else "📋 Copiar chave Pix"
    val pixLabel: String get() = if (isEnglish) "Pix (Brazil):" else "Pix (Brasil):"
    val pixCopied: String get() = if (isEnglish) "Pix key copied." else "Chave Pix copiada."
    val btnShowLicense: String get() = if (isEnglish) "📜 License" else "📜 Licença"
    val licenseWindowTitle: String get() = if (isEnglish) "License — Yabai OCR" else "Licença — Yabai OCR"
    val licenseMissing: String get() = if (isEnglish)
        "The license file was not found in this build."
    else
        "O arquivo de licença não foi encontrado neste build."

    // Updating from inside the app
    val btnInstallUpdate: String get() = if (isEnglish) "⬇️ Download and install" else "⬇️ Baixar e instalar"
    fun updateDownloading(percent: Int?): String = when {
        percent == null && isEnglish -> "Downloading update…"
        percent == null -> "Baixando atualização…"
        isEnglish -> "Downloading update… $percent%"
        else -> "Baixando atualização… $percent%"
    }
    val updateDownloadFailed: String get() = if (isEnglish) "Could not download the update. Try again or open the release page." else "Não foi possível baixar a atualização. Tente de novo ou abra a página do release."
    fun updateReadyRestart(version: String): String = if (isEnglish)
        "Version $version downloaded. Restart to use it."
    else
        "Versão $version baixada. Reinicie para usá-la."
    val updateInstalled: String get() = if (isEnglish) "✅ Downloaded" else "✅ Baixada"
    val btnRestartNow: String get() = if (isEnglish) "🔄 Restart now" else "🔄 Reiniciar agora"
    val btnLater: String get() = if (isEnglish) "Not now" else "Agora não"
    val updateRestartFailed: String get() = if (isEnglish) "The update is in place, but the restart failed. Open the app again." else "A atualização está no lugar, mas não foi possível reiniciar. Abra o app de novo."
    fun updateInstallerLaunched(version: String): String = if (isEnglish)
        "The $version installer was downloaded and opened. Finish it and open the app again."
    else
        "O instalador da versão $version foi baixado e aberto. Conclua e abra o app de novo."
    val updateManualFallback: String get() = if (isEnglish) "This build cannot update itself (it is not an AppImage). Opening the release page." else "Esta instalação não atualiza sozinha (não é um AppImage). Abrindo a página do release."

    val btnBackToPrevious: String get() = if (isEnglish) "◀ Back to Previous Menu" else "◀ Voltar ao Menu Anterior"
    val tabQueryDicts: String get() = if (isEnglish) "🔍 Query Dictionaries" else "🔍 Consultar Dicionários"
    val tabInstalledDicts: String get() = if (isEnglish) "📚 Installed Dictionaries" else "📚 Dicionários Instalados"
    val tabDownloadCatalog: String get() = if (isEnglish) "🌐 Download Catalog" else "🌐 Catálogo de Downloads"
    val searchDictsHeader: String get() = if (isEnglish) "Search Dictionaries" else "Pesquisar nos Dicionários"
    val searchPlaceholderExtended: String get() = if (isEnglish) "Search word, kanji, or reading (hiragana/romaji)..." else "Pesquisar palavra, kanji ou leitura (hiragana/romaji)..."
    val btnPaste: String get() = if (isEnglish) "📋 Paste" else "📋 Colar"
    val btnClear: String get() = if (isEnglish) "❌ Clear" else "❌ Apagar"
    val btnImport: String get() = if (isEnglish) "📥 Import" else "📥 Importar"
    val btnFolder: String get() = if (isEnglish) "📁 Folder" else "📁 Pasta"
    val statusInstalled: String get() = if (isEnglish) "Installed" else "Instalado"
    val recommendedDictsSubtitle: String get() = if (isEnglish) "Download and install dictionaries directly within the application. They will be saved and activated in the data folder:" else "Baixe e instale dicionários diretamente no aplicativo sem sair. Eles serão salvos e ativados na pasta de dados:"
    val dictIndexPreparing: String get() = if (isEnglish) "Preparing translation index… (first time only)" else "Preparando o índice de tradução… (só na primeira vez)"
    val quickDictShortTitle: String get() = if (isEnglish) "Quick Search" else "Busca Rápida"
    val searchTypeTranslation: String get() = if (isEnglish) "🔄 Translation" else "🔄 Tradução"
    val searchTypeWord: String get() = if (isEnglish) "🔤 Word" else "🔤 Palavra"
    val allDictsLabel: String get() = if (isEnglish) "🌐 All" else "🌐 Todos"
    val noDictsWarning: String get() = if (isEnglish) "No dictionaries found in data folder.\nAdd .yabai files to query." else "Nenhum dicionário encontrado na pasta de dados.\nAdicione arquivos .yabai para consultar."
    val hotkeyBackendActiveStatus: String get() = if (isEnglish) "Active global shortcuts (JNativeHook)." else "Atalhos globais ativos (JNativeHook)."
    val hoverStatusInactivePrefix: String get() = if (isEnglish) "Status: Hover mode disabled — enable to translate on hover." else "Status: Modo Hover desativado — ative para traduzir passando o mouse."
    val downloadCatalogHeader: String get() = if (isEnglish) "Download Catalog" else "Catálogo de Downloads"
    val installedDictsHeader: String get() = if (isEnglish) "Installed Dictionaries" else "Dicionários Instalados"
    val btnDownloadAndInstall: String get() = if (isEnglish) "⬇️ Download and Install" else "⬇️ Baixar e Instalar no App"
    val verificationStatusHeader: String get() = if (isEnglish) "Verification Status" else "Status da Verificação"
    val updateErrorHeader: String get() = if (isEnglish) "Update Error" else "Erro na Atualização"
    val downloadErrorHeader: String get() = if (isEnglish) "Download Error" else "Erro no Download"
    val sourceLabel: String get() = if (isEnglish) "Source: " else "Fonte: "

    // Hotkey Backend Additional Statuses
    val hotkeyBackendPortalStatus: String get() = if (isEnglish) "Active global shortcuts via system portal." else "Atalhos globais pelo portal do sistema."
    val hotkeyBackendUnavailableStatus: String get() = if (isEnglish) "Global shortcuts unavailable in this session. Use tray menu." else "Atalhos globais indisponíveis nesta sessão. Use o menu da bandeja."
    val hotkeyPortalFailureMessage: String get() = if (isEnglish) "System shortcuts portal did not respond. Use tray menu or configure system." else "O portal de atalhos do sistema não respondeu. Use o menu da bandeja ou configure pelo sistema."
    val hotkeyBackendKwinStatus: String get() = if (isEnglish) "Active global shortcuts through KWin (KGlobalAccel)." else "Atalhos globais pelo KWin (KGlobalAccel)."
    val hotkeyKwinFailureMessage: String get() = if (isEnglish) "KWin did not register the global shortcuts. Use the tray menu." else "O KWin não registrou os atalhos globais. Use o menu da bandeja."

    // Silent capture on KDE Wayland
    val kdeCapturePermissionTitle: String get() = if (isEnglish) "🖼 Silent capture on KDE" else "🖼 Captura silenciosa no KDE"
    val kdeCapturePermissionDesc: String get() = if (isEnglish)
        "KWin only authorizes its screenshot interface to programs installed in a fixed, system-wide path. This AppImage runs from a temporary folder that changes on every start, so KWin refuses it and every hover asks for permission through the portal — that is the delay."
    else
        "O KWin só autoriza a interface de captura a programas instalados em um caminho fixo do sistema. Este AppImage roda de uma pasta temporária que muda a cada abertura, então o KWin recusa e cada hover pede permissão pelo portal — é daí que vem o atraso."
    val kdeCapturePermissionToggle: String get() = if (isEnglish) "Disable KWin's screenshot authorization check" else "Desligar a checagem de autorização de captura do KWin"
    val kdeCapturePermissionHint: String get() = if (isEnglish)
        "Writes ~/.config/environment.d/yabai-ocr.conf with KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1. It applies to the whole session, so any program can capture the screen without asking. No root needed; takes effect after logging out and back in, and stays for every launch."
    else
        "Escreve ~/.config/environment.d/yabai-ocr.conf com KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1. Vale para a sessão inteira, então qualquer programa pode capturar a tela sem pedir. Não precisa de root; passa a valer depois de sair e entrar na sessão, e fica para todas as aberturas."
    val kdeCapturePermissionNeedsRelogin: String get() = if (isEnglish)
        "Enabled. Log out and back in once to make it effective; you do not need to repeat it afterwards."
    else
        "Ativado. Saia e entre na sessão uma vez para valer; depois disso não precisa repetir."
    val kdeCapturePermissionFailed: String get() = if (isEnglish) "Could not write the environment file." else "Não foi possível escrever o arquivo de ambiente."
    val hotkeyJNativeHookFailureMessage: String get() = if (isEnglish) "Could not install global keyboard hook. Use tray menu." else "Não foi possível instalar o gancho global de teclado. Use o menu da bandeja."

    // Dictionary Manager Updates & Progress
    val checkingStatus: String get() = if (isEnglish) "Checking..." else "Verificando..."
    fun checkCompletedMessage(count: Int): String = if (isEnglish) {
        "Check completed. $count update(s) available."
    } else {
        "Verificação concluída. $count ${if (count == 1) "atualização disponível" else "atualizações disponíveis"}."
    }
    val checkUpdatesError: String get() = if (isEnglish) "Error checking for updates." else "Erro ao verificar atualizações."
    val selectDictFileDialogTitle: String get() = if (isEnglish) "Select dictionary file (.yabai or .zip)" else "Selecionar arquivo de dicionário (.yabai ou .zip)"
    val selectDictFileFilterName: String get() = if (isEnglish) "Yabai / Yomitan Dictionaries (.yabai, .zip)" else "Dicionários Yabai / Yomitan (.yabai, .zip)"
    val updateAllDictsError: String get() = if (isEnglish) "Error updating all dictionaries." else "Erro ao atualizar todos os dicionários."
    val btnUpdateAllDicts: String get() = if (isEnglish) "🔄 Update All Available Dictionaries" else "🔄 Atualizar Todos os Dicionários Disponíveis"
    val noDictsInstalled: String get() = if (isEnglish) "No dictionaries currently installed." else "Nenhum dicionário instalado no momento."
    val noDictsInstalledDesc: String get() = if (isEnglish) "You can import existing .yabai or .zip files using the button above, or browse the 'Download Catalog' tab to download recommended dictionaries." else "Você pode importar arquivos .yabai ou .zip existentes usando o botão acima, ou navegar pela aba 'Catálogo de Downloads' para baixar os dicionários recomendados."
    fun revisionCurrentLabel(revision: String): String = if (isEnglish) " • Current: $revision" else " • Atual: $revision"
    fun revisionNewNotice(remoteRev: String): String = if (isEnglish) " • ✨ New: $remoteRev" else " • ✨ Nova: $remoteRev"
    val updateAvailableNotice: String get() = if (isEnglish) " • ✨ Update available" else " • ✨ Atualização disponível"
    fun fileInfoLabel(fileName: String, sizeMb: String, revStr: String, updateText: String): String = if (isEnglish) {
        "File: $fileName • Size: $sizeMb$revStr$updateText"
    } else {
        "Arquivo: $fileName • Tamanho: $sizeMb$revStr$updateText"
    }
    val updatingStatus: String get() = if (isEnglish) "Updating..." else "Atualizando..."
    val btnUpdate: String get() = if (isEnglish) "🔄 Update" else "🔄 Atualizar"
    val updateDictError: String get() = if (isEnglish) "Error updating dictionary." else "Erro ao atualizar o dicionário."
    val catalogInfoConsulting: String get() = if (isEnglish) "Querying catalog information..." else "Consultando informações do catálogo..."
    fun downloadStartNotice(sizeLabel: String): String = if (isEnglish) "Starting download of $sizeLabel..." else "Iniciando download de $sizeLabel..."
    fun downloadingProgress(mbRead: String, mbTotal: String, percent: Int): String = if (isEnglish) "Downloading: $mbRead MB / $mbTotal MB ($percent%)" else "Baixando: $mbRead MB / $mbTotal MB ($percent%)"
    fun downloadingProgressNoTotal(mbRead: String): String = if (isEnglish) "Downloading: $mbRead MB..." else "Baixando: $mbRead MB..."
    val compilingDictNotice: String get() = if (isEnglish) "Compiling and indexing dictionary..." else "Compilando e indexando dicionário..."
    val installSuccessNotice: String get() = if (isEnglish) "Installation completed successfully!" else "Instalação concluída com sucesso!"
    fun downloadUrlNotFound(name: String): String = if (isEnglish) "Download URL not found for $name" else "URL de download não encontrada para $name"
    fun serverHttpResponseError(code: Int): String = if (isEnglish) "Server responded HTTP $code" else "Servidor respondeu HTTP $code"
    val hotkeyBackendGnomeStatus: String get() = if (isEnglish) {
        "Global shortcuts come from the GNOME Shell extension."
    } else {
        "Atalhos globais pela extensão do GNOME Shell."
    }
    val hotkeyGnomeFailureMessage: String get() = if (isEnglish) {
        "The GNOME Shell extension did not register the shortcuts. Log out and back in once so the Shell loads it."
    } else {
        "A extensão do GNOME Shell não registrou os atalhos. Saia e entre uma vez para o Shell carregá-la."
    }
    val emptyDownloadError: String get() = if (isEnglish) "Empty download from server" else "Download vazio do servidor"
    fun portalCaptureFailed(code: Int): String = if (isEnglish) {
        "Screen capture failed at the system portal (code $code). No permission was requested."
    } else {
        "A captura de tela falhou no portal do sistema (código $code). Nenhuma permissão foi pedida."
    }
    val downloadDictError: String get() = if (isEnglish) "Error downloading dictionary." else "Erro ao baixar o dicionário."
    fun dictionaryReleaseEmpty(name: String): String = if (isEnglish) {
        "The newest publication of $name has no entries. Trying the previous one..."
    } else {
        "A publicação mais recente de $name está vazia. Tentando a anterior..."
    }
    fun dictionaryNoUsableRelease(name: String): String = if (isEnglish) {
        "No recent publication of $name has entries right now. Try again later."
    } else {
        "Nenhuma publicação recente de $name tem verbetes agora. Tente de novo mais tarde."
    }
    val noDictsForCredits: String get() = if (isEnglish) "No dictionaries installed to display credits and licenses." else "Nenhum dicionário instalado para exibir créditos e licenças."
    fun creditAuthor(author: String): String = if (isEnglish) "  Author / Source: $author" else "  Autor / Origem: $author"
    fun creditDescription(desc: String): String = if (isEnglish) "  Description: $desc" else "  Descrição: $desc"
    fun creditAttribution(attr: String): String = if (isEnglish) "  Attribution / License: $attr" else "  Atribuição / Licença: $attr"
    fun creditRevision(rev: String): String = if (isEnglish) "  Revision / Version: $rev" else "  Revisão / Versão: $rev"
    fun creditEntries(countStr: String): String = if (isEnglish) "  Entries: $countStr" else "  Entradas: $countStr"
    fun termsCount(count: Int): String = if (isEnglish) "$count terms" else "$count termos"
    fun kanjiCount(count: Int): String = if (isEnglish) "$count kanji" else "$count kanjis"
    val generalLicenseNotice: String get() = if (isEnglish) {
        "General terms: Vocabulary and kanji data are derived from open projects such as JMdict, KANJIDIC, Wiktionary, and Jitendex under Creative Commons (CC BY-SA) licensing."
    } else {
        "Termos gerais: Os dados de vocabulário e kanjis derivam de projetos abertos como JMdict, KANJIDIC, Wikcionário e Jitendex sob licença Creative Commons (CC BY-SA)."
    }

    // Lifecycle & App Main Strings
    val captureNoticeTitle: String get() = if (isEnglish) "Yabai OCR - Screen capture" else "Yabai OCR - Captura de tela"
    val captureNoticeHeading: String get() = if (isEnglish) "Screen capture unavailable" else "Captura de tela indisponível"
    val hoverNoticeTitle: String get() = "Yabai OCR - Hover"
    val hoverNoticeHeading: String get() = if (isEnglish) "Hover unavailable" else "Hover indisponível"
    val hoverWaylandUnsupported: String get() = if (isEnglish) {
        "Hover follows the system pointer, and this Wayland session does not expose it. " +
            "Only KDE provides it (through a KWin script). Use «Full Screen Capture» or «Capture Area»."
    } else {
        "O hover segue o ponteiro do sistema, e esta sessão Wayland não o expõe. " +
            "Só o KDE o fornece (por script do KWin). Use «Capturar Tela Inteira» ou «Capturar Área»."
    }
    val hoverKwinBridgeUnavailable: String get() = if (isEnglish) "Hover unavailable: KWin bridge is not active." else "Hover indisponível: o bridge do KWin não está ativo."
    val gnomeIntegrationActive: String get() = if (isEnglish) {
        "Hover uses the GNOME Shell global pointer."
    } else {
        "Hover usa o ponteiro global do GNOME Shell."
    }
    val gnomeBridgeInstalled: String get() = if (isEnglish) {
        "Hover bridge installed and enabled. GNOME Shell only reads an extension when it starts: " +
            "log out and back in, then turn hover on again."
    } else {
        "Ponte do hover instalada e ativa. O GNOME Shell só lê a extensão quando inicia: " +
            "saia e entre na sessão, depois ligue o hover de novo."
    }
    fun gnomeBridgeFailed(detail: String?): String = if (isEnglish) {
        if (detail == null) "Could not activate the GNOME hover bridge."
        else "Could not activate the GNOME hover bridge ($detail)."
    } else {
        if (detail == null) "Não foi possível ativar a ponte do hover do GNOME."
        else "Não foi possível ativar a ponte do hover do GNOME ($detail)."
    }
    val kwinIntegrationActive: String get() = if (isEnglish) "Hover and KWin shortcuts active." else "Hover e atalhos do KWin ativos."
    fun kwinDbusFailed(detail: String?): String = if (isEnglish) {
        if (detail == null) "Could not publish the dev.yabai.Ocr D-Bus service."
        else "Could not publish the dev.yabai.Ocr D-Bus service ($detail)."
    } else {
        if (detail == null) "Não foi possível publicar o serviço D-Bus dev.yabai.Ocr."
        else "Não foi possível publicar o serviço D-Bus dev.yabai.Ocr ($detail)."
    }
    fun kwinBridgeFailed(detail: String?): String = if (isEnglish) {
        if (detail == null) "Could not activate the KWin bridge."
        else "Could not activate the KWin bridge ($detail)."
    } else {
        if (detail == null) "Não foi possível ativar o bridge do KWin."
        else "Não foi possível ativar o bridge do KWin ($detail)."
    }
    fun kwinUpdateFailed(detail: String?): String = if (isEnglish) {
        if (detail == null) "Could not update the KWin shortcuts."
        else "Could not update the KWin shortcuts ($detail)."
    } else {
        if (detail == null) "Não foi possível atualizar os atalhos do KWin."
        else "Não foi possível atualizar os atalhos do KWin ($detail)."
    }
    fun kwinScriptWriteFailed(detail: String): String = if (isEnglish) {
        "Could not write the KWin script: $detail"
    } else {
        "Não foi possível gravar o script do KWin: $detail"
    }
    val kwinScriptNotWritten: String get() = if (isEnglish) "The KWin script was not written." else "O script do KWin não foi gravado."
    val kwinCaptureNoDescriptor: String get() = if (isEnglish) {
        "Could not obtain the file descriptor for the KWin capture."
    } else {
        "Não foi possível obter o descritor de arquivo da captura do KWin."
    }
    val kwinCaptureNoMetadata: String get() = if (isEnglish) {
        "KWin did not return the image metadata."
    } else {
        "O KWin não devolveu os metadados da imagem."
    }
    val kwinCaptureTooLarge: String get() = if (isEnglish) {
        "KWin returned an image that is too large."
    } else {
        "O KWin devolveu uma imagem grande demais."
    }
    fun kwinCaptureIncomplete(path: String): String = if (isEnglish) {
        "KWin did not finish writing the capture ($path)."
    } else {
        "O KWin não terminou de escrever a captura ($path)."
    }
    fun kwinCaptureFailed(detail: String): String = if (isEnglish) {
        "Could not capture the screen through KWin: $detail"
    } else {
        "Não foi possível capturar a tela pelo KWin: $detail"
    }
    val kwinShortcutsRejectedRestored: String get() = if (isEnglish) {
        "KWin rejected the new shortcuts; the previous script was restored."
    } else {
        "O KWin recusou os novos atalhos; o script anterior foi restaurado."
    }
    val kwinShortcutsRejectedNotRestored: String get() = if (isEnglish) {
        "KWin rejected the new shortcuts and the previous script could not be restored."
    } else {
        "O KWin recusou os novos atalhos e o script anterior não pôde ser restaurado."
    }
    fun captureScreenError(detail: String): String = if (isEnglish) "Could not capture screen: $detail" else "Não foi possível capturar a tela: $detail"
    val linuxIntegrationAlreadyInstalled: String get() = if (isEnglish) "Shortcut already installed for this user." else "Atalho já instalado para este usuário."
    val linuxIntegrationInstalledSuccess: String get() = if (isEnglish) "Shortcut and icon installed in application menu." else "Atalho e ícone instalados no menu de aplicativos."
    val linuxIntegrationInstalledError: String get() = if (isEnglish) "Could not install shortcut. Run Yabai OCR from AppImage or an installed executable." else "Não foi possível instalar o atalho. Rode o Yabai OCR a partir do AppImage ou de um executável instalado."
    fun appVersionNotice(version: String): String = if (isEnglish) "✨ New version v$version available!" else "✨ Nova versão v$version disponível!"
    fun dictUpdatesCountNotice(count: Int): String = if (isEnglish) "📚 $count dictionary update(s) available" else "📚 $count atualização(ões) de dicionário disponível(is)"

    // Updates notice (application + dictionaries)
    val updatesTitle: String get() = if (isEnglish) "Updates Available" else "Atualizações Disponíveis"
    fun trayUpdates(count: Int): String = if (isEnglish) {
        if (count > 0) "⬆️ Updates… ($count)" else "⬆️ Updates…"
    } else {
        if (count > 0) "⬆️ Atualizações… ($count)" else "⬆️ Atualizações…"
    }
    fun updatesDictionariesHeader(count: Int): String = if (isEnglish) {
        "📚 $count dictionary update${if (count == 1) "" else "s"}"
    } else {
        "📚 $count ${if (count == 1) "atualização de dicionário" else "atualizações de dicionário"}"
    }
    fun dictionaryUpdateVersions(installed: String, remote: String): String = if (isEnglish) {
        if (installed.isBlank()) "new: $remote" else "$installed → $remote"
    } else {
        if (installed.isBlank()) "nova: $remote" else "$installed → $remote"
    }
    val btnUpdateDictionaries: String get() = if (isEnglish) "🔄 Update dictionaries" else "🔄 Atualizar dicionários"
    val updatesDictInstalledNotice: String get() = if (isEnglish) "Dictionaries updated." else "Dicionários atualizados."
    val updatesDictInstallError: String get() = if (isEnglish) "Could not update the dictionaries." else "Não foi possível atualizar os dicionários."
    val updatesDictBlockedByApp: String get() = if (isEnglish) {
        "The application update restarts the app. Update the dictionaries after it restarts."
    } else {
        "A atualização do aplicativo reinicia o app. Atualize os dicionários depois que ele reiniciar."
    }

    // AI Provider Strings
    fun aiOpensUrl(url: String): String = if (isEnglish) "Opens $url" else "Abre $url"
    val aiCustomTitle: String get() = if (isEnglish) "Custom (URL with {text})" else "Personalizado (URL com {text})"
    val aiCustomDesc: String get() = if (isEnglish) "Manually enter translator or AI address with {text} placeholder." else "Digite manualmente o endereço do tradutor ou IA com o marcador {text}."
    val aiCustomUrlLabel: String get() = if (isEnglish) "Custom URL (e.g., https://mytranslator.com/?q={text})" else "URL Personalizada (ex: https://meutradutor.com/?q={text})"

    // Hover OCR Statuses & Defaults
    val hoverNoDictsInstalled: String get() = if (isEnglish) "No dictionaries installed — open 'Dictionary Manager'." else "Nenhum dicionário instalado — abra «Gerenciar Dicionários»."
    val hoverNoTextNearCursor: String get() = if (isEnglish) "No text recognized near the cursor." else "Nenhum texto reconhecido perto do cursor."

    /** How long the pointer has to rest before the card opens — the debounce in the Ajustes card. */
    val hoverRestTitle: String get() = if (isEnglish) "Wait before the card opens" else "Espera para abrir o card"
    val hoverRestDesc: String get() = if (isEnglish) {
        "How long the pointer has to stay still on the text. Less wait opens the card sooner; more " +
            "wait keeps it from opening while the pointer is just passing over."
    } else {
        "Quanto tempo o ponteiro precisa ficar parado sobre o texto. Menos espera abre o card mais " +
            "rápido; mais espera evita abrir enquanto o ponteiro só está passando por cima."
    }

    /** One choice of [hoverRestTitle]; the faster ones are named for the reader who wants speed. */
    fun hoverRestOption(millis: Long): String = when (millis) {
        150L -> if (isEnglish) "150 ms — Fast" else "150 ms — Rápido"
        250L -> if (isEnglish) "250 ms — Default" else "250 ms — Padrão"
        else -> if (isEnglish) "$millis ms — Calm" else "$millis ms — Calmo"
    }

    /** Name used when the capture backend does not say which transport served the frame. */
    val captureRouteUnknown: String get() = if (isEnglish) "screen capture" else "captura de tela"

    /**
     * Which capture served the last read and what it cost, appended to the hover status.
     *
     * [route] is a transport name (`KWin ScreenShot2`, `portal Screenshot`), not translated.
     */
    fun captureSummary(route: String, width: Int, height: Int, millis: Long): String = if (isEnglish) {
        "capture: $route · ${width}×${height} · $millis ms"
    } else {
        "captura: $route · ${width}×${height} · $millis ms"
    }
    /**
     * The chosen dictionary has no entry here.
     *
     * The card used to answer from another dictionary in this case, with no sign that the choice had
     * been ignored; the user picked a dictionary and has to be told when it cannot answer.
     */
    fun hoverDictionaryMissingEntry(dictionary: String): String = if (isEnglish) {
        "This dictionary has no entry for that word: $dictionary. Choose another one in the list above."
    } else {
        "Este dicionário não tem esse verbete: $dictionary. Escolha outro na lista acima."
    }
    val btnCancelDownload: String get() = if (isEnglish) "✕ Cancel" else "✕ Cancelar"
    val dictDownloadCancelled: String get() = if (isEnglish) "Download cancelled." else "Download cancelado."
    fun dictRemoveFailed(name: String): String = if (isEnglish) {
        "Could not remove $name: a file inside it is still open. Close the app and try again."
    } else {
        "Não foi possível remover $name: algum arquivo dentro dele continua aberto. Feche o app e tente de novo."
    }
    fun hoverNoMatchForText(text: String): String = if (isEnglish) "Text read, but no dictionary match: $text" else "Texto lido, mas sem verbete no dicionário: $text"
    fun hoverOcrFailure(detail: String): String = if (isEnglish) "OCR failure: $detail" else "Falha no OCR: $detail"
    val defaultAiPrompt: String get() = if (isEnglish) "Translate to English and explain the grammar, vocabulary, and nuances of this Japanese sentence:" else "Traduza para português e explique a gramática, vocabulário e nuances desta frase em japonês:"
    val translateTargetPortuguese: String get() = if (isEnglish) "Portuguese (pt)" else "Português (pt)"
    val translateTargetEnglish: String get() = if (isEnglish) "English (en)" else "Inglês (en)"

    // Additional UI Strings
    val selectDictDropdown: String get() = if (isEnglish) "📖 Select Dictionary ▾" else "📖 Selecionar Dicionário ▾"
    val recognizesTextProgress: String get() = if (isEnglish) "Recognizing Japanese text..." else "Reconhecendo texto em japonês..."
    val ocrFailed: String get() = if (isEnglish) "OCR Failed" else "Falha no OCR"
    val entriesAndDictHeader: String get() = if (isEnglish) "Entries & Dictionary" else "Verbetes e Dicionário"
    fun noDictsInFolder(path: String): String = if (isEnglish) "No dictionary installed in folder ($path) to query entries." else "Nenhum dicionário instalado na pasta ($path) para consultar verbetes."
    val noEntriesFoundForSentence: String get() = if (isEnglish) "No dictionary entries found for sentence." else "Nenhum verbete encontrado para a frase no dicionário."
    val couldNotRecognizeFullScreen: String get() = if (isEnglish) "Could not recognize full screen" else "Não foi possível reconhecer a tela inteira"
    val tempViewerTitle: String get() = if (isEnglish) "Yabai OCR - Temporary Viewer (Crop)" else "Yabai OCR - Visualizador Temporário (Recorte)"
    fun selectedCrop(w: Int, h: Int): String = if (isEnglish) "Selected Crop ($w x $h px)" else "Recorte Selecionado ($w x $h px)"
    val regionSelectionWindowTitle: String get() = if (isEnglish) "Yabai OCR - Select Region" else "Yabai OCR - Selecionar Área"
    val btnRecognizeOcr: String get() = if (isEnglish) "Recognize (OCR)" else "Reconhecer (OCR)"
    val selectDictFolderDialogTitle: String get() = if (isEnglish) "Select folder to save dictionaries" else "Selecionar pasta para salvar dicionários"
    val btnDownloadOnGithub: String get() = if (isEnglish) "🌐 Download Update on GitHub" else "🌐 Baixar Atualização no GitHub"

    // Dictionary lookup feedback — a query can find nothing, or the dictionary itself can fail.
    val lookupFailedGeneric: String get() = if (isEnglish) "The dictionary could not be read." else "Não foi possível ler o dicionário."
    fun lookupFailed(detail: String): String = if (isEnglish) "Could not read the dictionary: $detail" else "Não foi possível ler o dicionário: $detail"
    fun selectedDictHasNoEntry(title: String): String = if (isEnglish)
        "The selected dictionary ($title) has no entry for this word. Pick «All dictionaries» to search the others."
    else
        "O dicionário selecionado ($title) não tem verbete para esta palavra. Escolha «Todos os dicionários» para buscar nos outros."
}
