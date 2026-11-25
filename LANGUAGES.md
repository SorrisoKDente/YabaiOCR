# LANGUAGES.md — Plano e Guia de Localização da Interface (UI)

Este documento estabelece o plano detalhado e o guia técnico de implementação para a internacionalização e localização da interface do **Yabai OCR**, tomando o **Inglês (en-US)** como primeiro idioma adicional além do **Português (pt-BR)** nativo.

---

## 1. Separação de Conceitos (Fundamental)

Antes de qualquer implementação, é indispensável manter separados os dois eixos de idioma do aplicativo:

1. **Idioma do OCR, Dicionário e Leitura (Núcleo do Produto):**
   - **Fixo em Japonês (`ja`)**. O aplicativo é um leitor de japonês com OCR.
   - O reconhecimento (Google Lens, PP-OCRv6 e ML Kit) opera em modo japonês.
   - A segmentação de texto, busca de verbetes, conjugações (deinflector), furigana e kanji dependem dos dicionários de japonês (JMdict, Jitendex, KANJIDIC, etc.).
   - *Traduzir a interface do app para inglês não altera o idioma das mídias lidas nem o funcionamento dos dicionários de japonês.*

2. **Idioma da Interface do Usuário (UI):**
   - O idioma em que menus, botões, títulos de janelas, mensagens de erro, caixas de diálogo, notificações e textos de configuração são exibidos.
   - É o escopo exclusivo deste documento.

---

## 2. Configuração de Idioma da UI (`AppLanguageChoice`)

Para permitir que o usuário escolha o idioma da interface ou respeite o sistema operacional, será adicionada a seguinte configuração em ambos os módulos (`:app` e `:desktop`):

```kotlin
enum class AppLanguageChoice {
    SYSTEM, // Detecta o idioma do SO (padrão)
    PT_BR,  // Força Português (Brasil)
    EN_US,  // Força Inglês
}
```

- **Chave de preferência:** `app_language` em `AppSettings` (Android) e `settings.properties` (`app_language` no Desktop).
- **Idioma Padrão:** `SYSTEM` — se o sistema estiver em inglês (ou qualquer idioma diferente de pt-BR), a UI adota inglês; se estiver em português, adota pt-BR.
- **Independência:** O idioma da UI é configurado separadamente do idioma de destino da tradução de frases (`translateTarget`: `pt` ou `en`).

---

## 3. Módulo Android (`:app`) — Mapeamento e Implementação

O Android já utiliza o padrão idiomático de arquivos de recursos (`res/values/strings.xml`).

### 3.1. Criação do Arquivo de Recursos em Inglês
- **Caminho:** `app/src/main/res/values-en/strings.xml`
- **Conteúdo:** Tradução de todas as chaves declaradas em `app/src/main/res/values/strings.xml` (~290 chaves).

#### Mapeamento por Categoria de Recursos:
1. **Cabeçalho e Permissões (`1. Permissões` / `1. Permissions`):**
   - `hero_title` ("Yabai OCR")
   - `hero_subtitle` ("Read Japanese in any app...")
   - `permission_overlay`, `permission_notifications`, `permission_grant`, `permission_remove`, diálogos de confirmação de remoção de permissão.
2. **Atalhos Rápidos e Tema (`2. Atalhos` / `3. Tema`):**
   - `section_shortcuts` ("2. Quick Shortcuts"), `section_theme` ("3. App Theme"), `theme_dark` ("Dark"), `theme_light` ("Light"), `theme_system` ("System").
3. **Motores de Reconhecimento (`4. Motor` / `4. OCR Engine`):**
       - `section_engine` ("4. Recognition Engine"), `engine_auto`, `engine_pp_ocr`, `engine_lens`, `engine_mlkit`, `engine_pp_ocr_desc`, `pp_ocr_download`/`pp_ocr_downloading`/`pp_ocr_downloaded`/`pp_ocr_not_installed`/`pp_ocr_download_failed` (modelos do motor offline), descrições e as setas de prioridade (só os glifos `↑`/`↓`, com `dictionary_up`/`dictionary_down` como descrição para leitores de tela).
4. **Comportamento (`5. Comportamento` / `5. Behaviour`):**
   - `section_behaviour` ("5. Behaviour"), `setting_auto_copy`, `setting_show_segments`, `setting_pick_text`, `setting_bubble_on_edge`, `setting_upscale`, `setting_show_image`, `setting_furigana`, `setting_furigana_readings`, `setting_picker_reading`, `picker_reading_ruby`, `picker_reading_label`.
5. **Dicionários e Tradução (`6. Dicionários` / `7. Tradução`):**
   - `section_dictionary` ("6. Dictionaries"), `section_translation` ("7. Translation"), `dictionary_manage_button`, `translate_target_pt`, `translate_target_en`.
6. **Perguntar à IA (`8. IA` / `8. Ask AI`):**
   - `section_ai` ("8. Ask an AI"), `setting_ai_button`, `setting_ai_prompt`, `setting_ai_app`, `ai_note`.
7. **Overlay e Painéis Flutuantes:**
   - Ícones da bolha (`overlay_full_screen`, `overlay_select_area`, `overlay_camera`, `overlay_kanji`, `overlay_dictionary`, `overlay_settings`, `overlay_close_service`), a recusa da permissão da câmera (`camera_permission_denied`), a nova foto a partir dos trechos ou do resultado (`camera_retake`) e a tela da câmera (`camera_shutter`, `camera_reading`, `camera_close`) e o «Cancelar» da espera pelo reconhecimento (`recognition_cancel`).
   - Forma antiga do kanji, mostrada no painel quando a busca segue um redirecionamento: `dictionary_old_form`.
   - Painel de resultado (`result_title`, `result_retry_offline`, `result_copy_original`, `result_copy_translation`, `result_repick`, `result_image`, `result_ask_ai`, `result_translation_label`, `result_dictionary_label`, `result_kanji_label`).
   - Tela de leitura de kanji (`kanji_hint`, `kanji_on`, `kanji_kun`, `kanji_parts`, `kanji_dictionary_missing`).
8. **Notificação de Serviço Foreground:**
   - `notification_channel_name`, `notification_title`, `notification_text`, `notification_pause`, `notification_resume`, `notification_stop`.
9. **Gerenciador de Dicionários e Atualizações:**
   - `dictionary_title`, `dictionary_installed_section`, `dictionary_available_section`, `dictionary_import_section`, mensagens de progresso de download e conversão, verificador de atualizações, «Reinstalar» (`dictionary_reinstall`), créditos e licenças.
10. **Tela de Ajuda (`HelpActivity`):**
    - `help_title` ("Help & Tips"), `menu_help` ("Help"), `help_buttons_*` (a primeira seção: um resumo por botão da bolha, com o ícone do botão à esquerda), `help_tip_area_*`, `help_tip_camera_*` (a câmera, «Cancelar» e nova foto), `help_tip_bubble_*` (gestos da bolha, «Ler kanji» e o «Cancelar» de qualquer leitura), `help_tip_kanji_*`, `help_tip_engines_*` (inclui o PP-OCRv6; não há mais manga-ocr), `help_tip_behaviours_*`, `help_tip_dicts_*`, `help_tip_ai_*`, `help_tip_translation_*`.

### 3.2. Extração de Strings Hardcoded no Kotlin (`:app`)
As seguintes strings escritas diretamente no código Kotlin devem ser extraídas para `strings.xml`:
- `DictionaryActivity.kt`:
  - `"Créditos e Licenças"` → `@string/dictionary_legal_credits_header`
  - `"outro download em andamento"` → `@string/dictionary_error_busy`
  - `"não foi possível abrir o arquivo"` → `@string/dictionary_error_open_file`
- `DictionaryRepository.kt`:
  - `"Montando índice…"` → `@string/dictionary_building_index`
- `DictionaryUpdates.kt`:
  - `"o catálogo não informou a revisão"` → `@string/dictionary_update_err_no_revision`
  - `"o catálogo não informou de onde baixar"` → `@string/dictionary_update_err_no_url`
  - `"o servidor não informou uma versão comparável"` → `@string/dictionary_update_err_no_comparable_version`
- `ResultOverlay.kt`:
  - A tabela de tradução de conjugações do Yomichan (linhas 1927–1962: `"polite"` → `"polido"`, `"past"` → `"passado"`) já é originalmente escrita em termos em inglês pelo Yomichan. Quando a UI do app estiver em inglês (`EN_US`), a conversão para português deve ser ignorada, exibindo diretamente o termo em inglês retornado pelo deinflector.

---

## 4. Módulo Desktop (`:desktop`) — Mapeamento e Arquitetura

No módulo Desktop, os textos da interface estão gravados diretamente no código Kotlin das janelas Compose Desktop e Swing.

### 4.1. Arquitetura de Localização no Desktop (`DesktopI18n`)
Para localizar o Desktop de forma sustentável sem acoplamento nem dispersão de strings, será criado um objeto gerenciador de localização no Desktop:

- **Local:** `desktop/src/main/kotlin/dev/yabai/ocr/desktop/i18n/DesktopStrings.kt`
- **Mecanismo:**
  ```kotlin
  object DesktopStrings {
      var language: AppLanguageChoice = AppLanguageChoice.SYSTEM

      val title: String get() = when (effectiveLanguage) {
          AppLanguageChoice.EN_US -> "Yabai OCR - Settings"
          else -> "Yabai OCR - Ajustes e Configurações"
      }
      // ... propriedades delegadas para cada tela
  }
  ```

### 4.2. Mapeamento Completo de Telas e Textos do Desktop

#### 1. `SettingsWindow.kt` (Janela de Ajustes e Configurações)
- **Título da Janela:** `"Yabai OCR - Ajustes e Configurações"` → `"Yabai OCR - Settings"`
- **Aparência e Tema:**
  - `"Aparência e Tema"` → `"Appearance & Theme"`
  - `"Tema Escuro 🌙"` → `"Dark Theme 🌙"`
  - `"Tema Claro ☀️"` → `"Light Theme ☀️"`
- **Atalhos Globais de Teclado:**
  - `"Atalhos Globais de Teclado"` → `"Global Keyboard Shortcuts"`
  - `"Atalho da Captura da Tela Inteira (OCR):"` → `"Full Screen Capture Shortcut (OCR):"`
  - `"Atalho do Recorte de Área (OCR):"` → `"Region Selection Shortcut (OCR):"`
  - `"Atalho de Busca Rápida de Dicionário:"` → `"Quick Dictionary Search Shortcut:"`
  - `"Atalho de Ligar/Desligar Modo Hover:"` → `"Toggle Hover Mode Shortcut:"`
  - `"Atalho para Alternar Dicionário no Hover:"` → `"Cycle Hover Dictionary Shortcut:"`
  - Botões: `"Capturar"` → `"Capture"`, `"Padrão"` → `"Default"`, `"Aguardando..."` → `"Press keys..."`
- **Inicialização e Comportamento da Janela:**
  - `"Inicialização e Comportamento da Janela"` → `"Startup & Window Behaviour"`
  - `"Iniciar na bandeja ao ligar o computador"` → `"Start minimized in tray on system boot"`
  - `"Manter na bandeja ao fechar a janela"` → `"Keep running in tray when closing window"`
- **Modo Hover:**
  - `"Modo Hover — leitura em tempo real (Estilo Yomitan)"` → `"Hover Mode — Real-time Reading (Yomitan Style)"`
  - Status: `"🟢 Ativo — pare o mouse sobre um texto japonês."` → `"🟢 Active — hover over Japanese text."`
  - `"⚪ Inativo — ative para traduzir só passando o mouse."` → `"⚪ Inactive — enable to translate on hover."`
  - `"Dicionário Padrão do Modo Hover:"` → `"Default Hover Dictionary:"`
  - Botão: `"Testar no cursor atual"` → `"Test at current cursor"`
- **Motor de Reconhecimento:**
  - `"Motor de reconhecimento"` → `"Recognition Engine"`
  - `"Automático (Usa Ordem de Prioridades)"` → `"Automatic (Use Priority Order)"`
  - `"Somente PP-OCRv6 (offline)"` → `"PP-OCRv6 Only (offline)"`
  - `"Baixar modelos do PP-OCRv6 (~23 MB)"` → `"Download PP-OCRv6 models (~23 MB)"`
  - `"Somente Google Lens (online)"` → `"Google Lens Only (online)"`
  - Prioridades: `"Prioridade dos Motores no modo Automático / Fallback:"` → `"Engine Fallback Priority Order:"`
  - Botões de reordenação: só os glifos `↑`/`↓` (R46c); o texto de `dictionary_up`/`dictionary_down` («Subir»/«Descer») vira a descrição para leitores de tela.
- **Comportamento do OCR e Resultado:**
  - `"Comportamento do OCR e resultado"` → `"OCR & Result Behaviour"`
  - `"Copiar automaticamente ao reconhecer"` → `"Copy automatically after recognition"`
  - `"Listar o texto linha por linha"` → `"List text line by line"`
  - `"Ampliar recortes pequenos"` → `"Upscale small image crops"`
  - `"Mostrar imagem capturada ao lado do texto"` → `"Show captured image next to text"`
  - `"Manter furigana original entre parênteses"` → `"Keep original printed furigana in parentheses"`
  - `"Gerar furigana do dicionário no painel"` → `"Generate dictionary furigana in panel"`
  - Leituras no seletor: `"Leituras dos kanjis no seletor da página:"` → `"Kanji readings on page picker:"`, `"Desativado"` → `"Disabled"`, `"Rubi sobre o texto"` → `"Ruby above text"`, `"Faixa indicativa legível"` → `"Legible side label"`
- **Dicionários e Pasta de Dados:**
  - `"Dicionários de Japonês e Pasta de Dados"` → `"Japanese Dictionaries & Data Folder"`
  - `"Consultar dicionários instalados"` → `"Lookup installed dictionaries"`
  - `"Dicionário Padrão de Filtro:"` → `"Default Dictionary Filter:"`
  - `"Localização da Pasta de Dicionários:"` → `"Dictionary Folder Location:"`
  - Botões: `"Alterar Pasta..."` → `"Change Folder..."`, `"Restaurar Padrão"` → `"Restore Default"`, `"Gerenciar"` → `"Manage"`
  - `"Manter última busca no dicionário"` → `"Keep last dictionary query"`
  - `"Ignorar leituras entre parênteses na consulta"` → `"Strip parentheses readings in search"`
  - Verificação automática: `"Verificação de atualizações dos dicionários:"` → `"Automatic dictionary update check:"`, `"Desativado"` → `"Disabled"`, `"Diariamente"` → `"Daily"`, `"Semanalmente"` → `"Weekly"`, `"Mensalmente"` → `"Monthly"`
- **Tradução:**
  - `"Tradução de Frases"` → `"Sentence Translation"`
  - `"Traduzir a frase reconhecida"` → `"Translate recognized sentence"`
  - `"Idioma de destino da tradução:"` → `"Translation target language:"`
- **Perguntar a uma IA:**
  - `"Perguntar a uma IA"` → `"Ask an AI"`
  - `"Exibir o botão «Perguntar à IA» no painel de resultado"` → `"Show 'Ask AI' button in result panel"`
  - `"Provedor / Serviço de IA:"` → `"AI Provider / Service:"`
  - `"Prompt / Pergunta inicial enviada à IA:"` → `"Initial AI Prompt:"`
- **Sobre e Atualizações:**
  - `"Sobre o Yabai OCR e Atualizações do Aplicativo"` → `"About Yabai OCR & App Updates"`
  - `"Versão Instalada:"` → `"Installed Version:"`
  - Botões: `"🔄 Verificar Atualizações"` → `"🔄 Check for Updates"`, `"⬇️ Baixar Atualização"` → `"⬇️ Download Update"`

#### 2. `DictionaryManagerWindow.kt` (Gerenciador de Dicionários Desktop)
- **Título da Janela:** `"Gerenciador de Dicionários"` → `"Dictionary Manager"`
- **Cabeçalhos:** `"Pesquisar nos Dicionários"` → `"Search Dictionaries"`, `"Dicionários Locais e Atualizações"` → `"Local Dictionaries & Updates"`, `"Dicionários Recomendados"` → `"Recommended Dictionaries"`
- **Alternadores de busca:** `"🔄 Tradução"` → `"🔄 Definition Search"`, `"🔤 Palavra"` → `"🔤 Headword Search"`
- **Ações:** `"Escolher arquivo .zip"` → `"Choose .zip file"`, `"🔄 Verificar Atualizações"` → `"🔄 Check for Updates"`, `"🔄 Atualizar"` → `"🔄 Update"`, `"Remover"` → `"Remove"`, `"Ativo"` → `"Active"`, `"Desativado"` → `"Disabled"`
- **Créditos legais:** `"▸ Ver créditos legais e licenças dos dicionários"` → `"▸ View legal credits and dictionary licenses"`

#### 3. `ResultWindow.kt` (Painel Flutuante de Resultado Desktop)
- **Título:** `"Texto reconhecido"` → `"Recognized Text"`
- **Botões e Ações:** `"Copiar original"` → `"Copy Original"`, `"Copiar tradução"` → `"Copy Translation"`, `"Perguntar à IA"` → `"Ask AI"`, `"Imagem"` → `"Image"`, `"Recortar à mão"` → `"Manual Crop"`, `"Tentar offline"` → `"Try Offline"`, `"Fechar"` → `"Close"`
- **Avisos de Status:** `"Lens indisponível"` → `"Lens unavailable"`, `"Pesquisando..."` → `"Searching..."`, `"Traduzindo..."` → `"Translating..."`

#### 4. `QuickDictWindow.kt` (Busca Rápida de Dicionário Desktop)
- **Título da Janela:** `"Busca Rápida de Dicionário"` → `"Quick Dictionary Search"`
- **Dicas:** `"Pesquisar palavra, kanji ou leitura…"` → `"Search word, kanji, or reading..."`
- **Modos:** `"🔤 Palavra"` → `"🔤 Headword"`, `"🔄 Tradução"` → `"🔄 Definition"`

#### 5. `TrayMenu.kt` (Menu da Bandeja do Sistema)
- `"🖥️ Capturar Tela Inteira (OCR)"` → `"🖥️ Full Screen Capture (OCR)"`
- `"✂️ Capturar Área (OCR)"` → `"✂️ Select Region (OCR)"`
- `"🔍 Busca Rápida de Dicionário"` → `"🔍 Quick Dictionary Search"`
- `"⚙️ Ajustes e Configurações"` → `"⚙️ Settings & Options"`
- `"📖 Gerenciar Dicionários"` → `"📖 Manage Dictionaries"`
- `"💬 Modo Hover [Ativo/Inativo]"` → `"💬 Hover Mode [Active/Inactive]"`
- `"✕ Sair do Yabai OCR"` → `"✕ Exit Yabai OCR"`

#### 6. `HoverTooltipWindow.kt`, `SegmentPickerWindow.kt`, `RegionSelectionWindow.kt`
- Tooltip do Hover: `"🌐 Todos os Dicionários"` → `"🌐 All Dictionaries"`, `"Sem verbete"` → `"No match found"`
- Telas de Seleção: `"Toque num trecho para copiar ou traduzir"` → `"Click a segment to copy or translate"`, `"Arraste para selecionar a área"` → `"Drag to select a region"`, `"Cancelar"` → `"Cancel"`, `"Reconhecer"` → `"Recognize"`, `"Texto todo"` → `"All text"`.

#### 7. `HelpWindow.kt` (Manual de Ajuda Desktop)
- **Botão e título:** `"❓ Ajuda"` → `"❓ Help"`, `"Ajuda — Yabai OCR"` → `"Help — Yabai OCR"`
- **Referência de botões** (`helpButtonsTitle`, `helpBtn*Name/What`): a primeira seção, com o ícone do botão à esquerda, o nome e o que ele faz; `helpButtons()` desenha as linhas.
- **Entradas do manual** (`helpIntro`, `helpCapture*`, `helpRegion*`, `helpHover*`, `helpQuickSearch*`, `helpDictionaries*`, `helpResult*`, `helpRenderers*`, `helpEngines*`, `helpTray*`): as duas capturas, o hover, a busca rápida, os dicionários, a janela de resultado, o **renderizador gráfico** (uma entrada por renderizador, com vantagem e desvantagem) e os **motores de reconhecimento** (um por um, também com os dois lados), e a bandeja. As entradas das capturas, do hover e da busca recebem o atalho como parâmetro (`HelpShortcuts`, lido do `DesktopSettings` na abertura da janela), então o manual mostra o atalho configurado, não um padrão fixo. O teste `HelpManualTest` compara as duas línguas, exige a referência antes das entradas, os atalhos configurados nas duas línguas, os três renderizadores e os motores com `+`/`−`, e lê o aviso de crash do renderizador.
- **Ícone da janela:** `AppIconGenerator.getTrayImage()`, os mesmos pixels da bandeja; os PNGs `ic_scan_linux.png`/`ic_scan_windows.png` são regerados do quadro atual do `.ico` e `TrayGlyphsTest` compara os pixels dos dois.

---

## 5. Roteiro Passo a Passo de Execução

1. **Etapa 1: Módulo Mobile (`:app`)**
   - Criar `app/src/main/res/values-en/strings.xml` com a tradução completa de todas as chaves.
   - Extrair strings hardcoded no Kotlin de `:app` para `strings.xml`.
   - Adicionar o seletor de idioma de UI (`AppLanguageChoice`) nas configurações e aplicar a troca dinâmica de locale (`AppCompatDelegate.setApplicationLocales`).

2. **Etapa 2: Módulo Desktop (`:desktop`)**
   - Criar o objeto de localização `DesktopStrings.kt` unificando todas as propriedades de texto de UI do Desktop.
   - Adicionar a propriedade `appLanguage` em `DesktopSettings`.
   - Substituir os textos literais em português nas janelas Compose Desktop/Swing por referências às propriedades de `DesktopStrings`.

3. **Etapa 3: Validação e Testes**
   - Executar os testes unitários da suíte desktop (`.\gradlew.bat :desktop:test`) e mobile (`.\gradlew.bat :app:testDebugUnitTest`).
   - Validar o layout visual no Android e no Desktop para garantir que os textos em inglês cabem nos botões e painéis sem quebras de linha ou sobreposições.

---

## 6. O que NÃO Deve Ser Traduzido (Invariantes Técnicas)

As seguintes constantes e valores técnicos **não** devem ser alterados nem traduzidos:
- Identificadores de configuração e chaves de preferences (ex.: `"engine"`, `"auto_copy"`, `"global_hotkey"`, `"picker_reading"`).
- Tags BCP-47 de idioma do modelo de OCR ou tradução (ex.: `"ja"`, `"pt"`, `"en"`).
- Identificadores de dicionários e IDs de catálogo (ex.: `"jmdict"`, `"jitendex"`, `"jmnedict"`, `"kanjidic_english"`).
- URLs de serviços, pacotes Android (ex.: `"com.openai.chatgpt"`), esquemas DBus do Linux e comandos de sistema.
- Comentários no código-fonte (que permanecem em inglês como padrão estabelecido no repositório).

### 4.2. Regra atual (12/11/2026)

Toda string de interface existe em **pt-BR e en** de uma vez; o usuário escolhe o idioma nos Ajustes
(`AppLanguageChoice`). No desktop, as duas versões vivem no mesmo `DesktopStrings` (`isEnglish`); no
Android, cada chave existe em `values/strings.xml` (pt-BR) e `values-en/strings.xml`.

Chaves novas do popup de resultado e da busca rápida (D43): `lookupFailedGeneric`,
`lookupFailed(detail)` e `selectedDictHasNoEntry(title)`.

Os documentos públicos também são bilíngues: `README.md`/`README.pt-BR.md`,
`ARCHITECTURE.md`/`ARCHITECTURE.en.md`, `CONTRIBUTING.md`/`CONTRIBUTING.en.md`, com seletor de idioma no
topo de cada arquivo. O `docs/ROADMAP.md` segue só em português (histórico técnico interno).
