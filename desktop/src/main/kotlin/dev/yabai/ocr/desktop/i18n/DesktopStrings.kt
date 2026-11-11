package dev.yabai.ocr.desktop.i18n

import dev.yabai.ocr.desktop.DesktopLanguageChoice
import dev.yabai.ocr.desktop.DesktopSettings
import java.util.Locale

object DesktopStrings {
    private var currentChoice: DesktopLanguageChoice = DesktopLanguageChoice.SYSTEM

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
}
