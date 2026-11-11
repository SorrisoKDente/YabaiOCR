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
    val btnTestHoverCursor: String get() = if (isEnglish) "Test at current cursor" else "Testar no cursor atual"

    // Engine
    val engineAuto: String get() = if (isEnglish) "Automatic (Use Priority Order)" else "Automático (Usa Ordem de Prioridades)"
    val engineMangaOcr: String get() = if (isEnglish) "Manga-OCR Only (offline)" else "Somente Manga-OCR (offline)"
    val engineLens: String get() = if (isEnglish) "Google Lens Only (online)" else "Somente Google Lens (online)"
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
    val langEnUsDesc: String get() = if (isEnglish) "Interface in English." else "Interface in English."
    val hotkeyActiveStatusLabel: String get() = if (isEnglish) "Active global shortcuts (JNativeHook)." else "Atalhos globais ativos (JNativeHook)."
    val btnDisable: String get() = if (isEnglish) "Disable" else "Desativar"
    val btnEnable: String get() = if (isEnglish) "Enable" else "Ativar"
    val startOnBootDesc: String get() = if (isEnglish) "Opens the application minimized in the system tray automatically when the operating system starts." else "Abre o aplicativo minimizado na bandeja do sistema automaticamente quando o sistema operacional inicia."
    val closeToTrayDesc: String get() = if (isEnglish) "When closing the main window, the program continues running in the background in the tray to respond to keyboard shortcuts. If disabled, the application will exit completely." else "Ao fechar a janela principal, o programa continua rodando em segundo plano na bandeja para responder aos atalhos de teclado. Se desativado, o aplicativo será encerrado por completo."
    val hoverExplanation: String get() = if (isEnglish) "This mode works in any application: browser, manga reader, PDF, or game. Once enabled, just pause the pointer for a moment over a word; the card with reading and definition appears next to the cursor. Moving the mouse away closes the card." else "O modo funciona em qualquer aplicativo: navegador, leitor de mangá, PDF ou jogo. Depois de ativado, basta deixar o ponteiro parado por um instante sobre a palavra; o cartão com leitura e definição aparece ao lado do cursor. Mover o mouse para fora fecha o cartão."
    fun hoverShortcutsNotice(hover: String, cycle: String): String = if (isEnglish) "Shortcut to toggle: $hover • Cycle dictionary: $cycle" else "Atalho para ligar/desligar: $hover • Alternar dicionário: $cycle"
    val engineSubtitle: String get() = if (isEnglish) "Choose how Japanese text is recognized from the image." else "Escolha como o texto em japonês será reconhecido da imagem."
    val engineAutoDesc: String get() = if (isEnglish) "Tries engines in priority order (Google Lens, offline Manga-OCR)." else "Tenta motores em ordem de prioridade (Google Lens, Manga-OCR offline)."
    val engineMangaOcrDesc: String get() = if (isEnglish) "Uses the offline AI engine specialized in vertical text and manga." else "Usa o motor de inteligência artificial offline especializado em texto vertical e mangás."
    val engineLensDesc: String get() = if (isEnglish) "Uses the online Google Lens engine. Requires active internet connection." else "Usa o motor online do Google Lens. Exige conexão ativa com a internet."
    val enginePriorityDesc: String get() = if (isEnglish) "Define the preference order of recognition engines." else "Defina a ordem de preferência dos motores de reconhecimento."
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
}
