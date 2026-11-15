# Portabilidade para Linux e Wayland — plano de implementação

**Status:** **P1 (base Linux), P2 (captura Wayland), P3 (atalhos Wayland), P4 (hover Wayland
pelo KWin) e P5 (polimento Linux) implementadas em 08/11/2026**; P6 pendente. Nenhuma validação em
host Linux/Steam Deck real foi feita ainda — o empacotamento Linux precisa ser gerado em Linux, a
captura por portal precisa de uma sessão Wayland de verdade, o diálogo de atalhos do portal só existe
no KDE, o script KWin/D-Bus não roda neste host Windows e a bandeja StatusNotifier/`keepAbove` só
podem ser vistos numa sessão KDE real.
**Módulo afetado:** `:desktop` (e apenas ele). `:core` só muda se for para extrair algo comprovadamente
independente de SO; `:app` (Android) não é tocado.
**Alvo primário:** **Steam Deck / SteamOS em Desktop Mode (KDE Plasma 6 + Wayland nativo)**.
**Alvo secundário:** Linux desktop com Wayland (KDE Plasma) e Linux com X11.
**Não muda:** Windows continua sendo a plataforma de referência; Android continua intacto.
**Data desta revisão:** 08/11/2026.

**Atualização D44 (12/11/2026):** no KDE Wayland a captura passou a usar
`org.kde.KWin.ScreenShot2` como caminho principal (o portal `Screenshot` ficou como fallback) e o
menu da bandeja passou a ser DBusMenu desenhado pelo Plasma (o `ContextMenu`/Swing ficou como
fallback). Onde as seções históricas deste plano dizem “portal `Screenshot`” ou “menu Swing” como
caminho principal, vale esta atualização. A validação visual continua dependendo de um host KDE real.

**Atualização D57/D58 (14/11/2026):** no **GNOME Wayland** (Ubuntu 24.04/GNOME 46) o hover
passou a existir por uma extensão do Shell que o app instala e ativa sozinho — é a única rota, porque
`global.get_pointer()` só existe dentro do compositor. A extensão fala o **mesmo contrato D-Bus** do
script KWin (`dev.yabai.Ocr.Hover.CursorMoved`) e publica o nome `dev.yabai.GnomePointer`, que o app
usa para distinguir “instalada” de “carregada”: o GNOME só lê extensões no início da sessão, então a
primeira ativação pede sair e entrar uma vez. A captura nesse ambiente usa o `ScreenCast` do JDK, e o
piso real do `dbusScreencast` é **17.0.13** (backport, JDK-8335485), não 21 — o AppImage embarca
17.0.21 e antes era recusado pelo app. Ver D57/D58 no [ROADMAP](ROADMAP.md).

**Atualização D62 (15/11/2026):** no GNOME a captura deixou de usar o `ScreenCast` do JDK e passou a
ser o portal `org.freedesktop.portal.Screenshot` com `interactive=false` — uma chamada one-shot por
leitura (128 ms medidos), sem sessão ScreenCast, sem diálogo e sem o indicador de compartilhamento do
Shell que aparecia a cada hover. O `ScreenCast` continua apenas nos Wayland que não são KDE nem
GNOME. Ver D62 no [ROADMAP](ROADMAP.md).

**O que a P1 entregou:** `DesktopPlatform`/`PlatformServices`, autostart XDG com escape correto de
`Exec`, ícone PNG Linux, `YABAI_HOME`, build condicional `Deb`/`AppImage` + `packagePortableTarGz`, e
27 testes novos. As oito mutações da P1 foram provadas.

**O que a P2 entregou (histórico; no KDE Wayland a captura foi substituída pelo portal `Screenshot`
na D37 e depois pelo `ScreenShot2` na D44):** `ScreenshotMethod` (`awt.robot.screenshotMethod=dbusScreencast` só em Linux
Wayland KDE), backend `ScreencastScreenCapture` com `CaptureAvailability`
(`READY`/`NEEDS_PERMISSION`/`DENIED`/`UNSUPPORTED`), `ScreenCaptureException`, pickers em
`WindowPlacement.Fullscreen` no Wayland e `CaptureNoticeWindow` para falha de captura. 15 testes novos
(suíte desktop em **138**); as sete mutações da P2 foram provadas.

**O que a P3 entregou:** `HotkeyBackend`/`HotkeyService` (seleção JNativeHook × portal, status na UI),
`toXdgTrigger` (conversão para o formato XDG, recusando combinação inválida), `HotkeyBindings` (ids
estáveis e descrições pt-BR) e `PortalGlobalShortcuts` + `DbusPortalShortcutsTransport`
(`CreateSession`/`BindShortcuts`/`Activated` via `dbus-java` 5.1.1). 25 testes novos (suíte desktop em
**169**); as sete mutações da P3 foram provadas.

Hover do Wayland continua sendo P4. O fallback KWin dos atalhos também fica para a P4: ele depende do
serviço D-Bus e do script KWin que a P4 instala. Até lá, se o portal não responder, a P3 mostra
“atalhos indisponíveis” e mantém todas as ações no menu da bandeja.

**O que a P4 entregou (naquela fase a captura do KDE era o portal `Screenshot`, ver D37; a D44
trocou o caminho principal pelo `ScreenShot2`):** `HoverCapability`
(AWT × KWin × indisponível, com mensagem e captura screencast só no KWin),
`PointerSource`/`KwinPointerSource` (posição do script, com dedup),
`YabaiDbusService` (`dev.yabai.Ocr` com `Hover.CursorMoved` e `Shortcuts.ShortcutPressed`),
`KWinHoverBridge` (instala e carrega o script em `~/.local/share/kwin/scripts/yabai-ocr` via
`org.kde.KWin` `/Scripting`), `KWinIntegration` (D-Bus primeiro, script depois, rollback e status
visível), `KWinScript` (metadata, script principal e bloco de atalhos de fallback),
`HoverOcrManager` sem `MouseInfo`/poll no KWin e com captura screencast (no KDE, portal `Screenshot`
naquela fase; hoje `ScreenShot2` com fallback para o portal), e o wiring em `Main.kt`.
29 testes novos (suíte desktop em **198**); as oito mutações da P4 foram provadas.

**O que a P5 entregou:** menu de bandeja nativo (`java.awt.PopupMenu`) no Linux e menu customizado
mantido no Windows (`TrayMenuKind`/`TrayMenuTest`); `SystemTrayManager.setup(): Boolean` com fallback
de `--minimized` para janela visível; `closeToTray` só esconde com bandeja ativa; rótulo
`Modo Hover [Ativo/Inativo]` atualizado ao vivo no menu nativo; `keepAbove`/`skipTaskbar` do tooltip
pelo script KWin; “Integrar ao sistema” (`LinuxSystemIntegration`) gravando `.desktop` + PNG em
`$XDG_DATA_HOME` (nunca em `/usr`) e cartão “Integração com o Linux” nos Ajustes; update checker
escolhendo o asset do SO (`.AppImage` no Linux, `.exe`/`.msi` no Windows, `.dmg` no macOS) com fallback
para a página do release. 16 testes novos (suíte desktop em **214**); as cinco mutações da P5 foram
provadas.

Com a P4, o fallback KWin dos atalhos do §5.2 passa a existir: quando o portal termina em
`UNAVAILABLE`, o script registra as mesmas combinações e chama `ShortcutPressed` no app.

> Esta revisão **substitui** a versão anterior do documento, que tratava Wayland como “melhor esforço”
> e descrevia captura, atalhos e hover como indisponíveis. O requisito agora é **paridade funcional no
> Wayland do KDE Plasma**, que é o que o Steam Deck usa por padrão no Desktop Mode.

---

## 0. Contrato de não-regressão

Esta é a regra que manda em todas as decisões abaixo.

1. **Windows continua igual.** Nenhuma alteração pode remover, esconder ou degradar menu da bandeja,
   atalhos globais, captura de tela, hover, autostart pelo registro ou empacotamento `.exe`/`.msi`.
   Todo código existente de Windows é preservado atrás de uma interface, não reescrito.
2. **Android não é tocado.** Nada em `app/` e nada em `core/` deve mudar por causa desta tarefa. A
   suíte do app (**411 testes**) e a do core (**194**, com pulos só nas flags opcionais) continuam sendo o contrato.
3. **A diferença de sistema fica atrás de uma camada de plataforma**, escolhida uma única vez no
   início do processo. Não espalhar `if (isWayland)` por vinte arquivos.
4. **Nenhum teste existente pode ser enfraquecido** para passar no Linux. `StartupManagerTest`
   continua pulando fora do Windows e ganha um equivalente Linux com injeção de diretório — nunca
   mexe no `~/.config` real.
5. **Toda mudança de comportamento** (inclusive mensagem nova de “aguardando autorização”, “portal
   indisponível” ou “bandeja ausente”) exige teste de regressão não vazio e prova de mutação.
6. **Nenhum E2E pode deixar arquivo do usuário alterado.** Usar `YABAI_HOME` apontando para pasta
   temporária e, para `~/.config/autostart`, `XDG_CONFIG_HOME` temporário. Ainda assim, fazer backup
   de `~/.yabai_ocr/settings.properties` e restaurar com SHA-256 conferido.
7. **Wayland não é “quase suportado”.** Se um recurso não puder ser entregue na sessão do usuário, ele
   aparece como indisponível com motivo e alternativa — nunca como tela preta, exceção não tratada ou
   atalho que “não faz nada”.
8. **O empacotamento Linux roda em Linux.** Não gerar `.deb`/AppImage no Windows nem `.exe` no Linux.

---

## 1. Resumo executivo

O aplicativo já é, em boa parte, portátil “de graça”:

- `:desktop` é Kotlin/JVM + Compose Desktop; `compose.desktop.currentOs` resolve os binários nativos.
- `:core` é Kotlin/JVM puro (OCR, dicionários, tradução, geometria); não conhece Windows.
- A pasta de dados já é `~/.yabai_ocr/...`, que existe e funciona no Linux (inclusive no SteamOS, em
  `$HOME`, que é gravável e persiste entre atualizações).
- A maior parte da UI Compose não muda.

O que muda é a **integração com o ambiente gráfico**. No Wayland, por design, nenhum aplicativo lê a
tela, o teclado global ou a posição do ponteiro sem um portal autorizado pelo usuário ou uma API do
compositor. A estratégia é usar as duas únicas rotas que existem no KDE Plasma do Steam Deck:

| área | Windows | Linux X11 | Linux Wayland (KDE/SteamOS) | rota |
| --- | --- | --- | --- | --- |
| abrir app / UI Compose | ✅ | ✅ | ✅ | GTK3 + Skia (já funciona) |
| OCR, dicionários, tradução | ✅ | ✅ | ✅ | `:core` puro |
| captura de tela inteira | ✅ Robot | ✅ Robot | ✅ **`KWin.ScreenShot2`** (portal `Screenshot` como fallback) | QImage cru/FD + recorte; sem diálogo no caminho direto |
| recorte à mão | ✅ | ✅ | ✅ | captura antes + overlay fullscreen |
| hover (ponteiro + recorte) | ✅ | ✅ | ✅ **KWin script → D-Bus do app** | `workspace.cursorPos` |
| atalhos globais | ✅ JNativeHook | ✅ JNativeHook | ✅ **`GlobalShortcuts` portal** (fallback KWin) | xdg-desktop-portal |
| bandeja | ✅ AWT | ✅ AWT | ✅ **SNI (`StatusNotifierItem`)** | `org.kde.StatusNotifierWatcher` |
| menu da bandeja | ✅ custom | ✅ nativo/custom | ✅ **DBusMenu (`com.canonical.dbusmenu`)** (`ContextMenu`/Swing como fallback) | SNI, sem XEmbed |
| autostart | registro | XDG | ✅ XDG | `~/.config/autostart` |
| overlay always-on-top | ✅ | ✅/⚠️ | ✅/⚠️ fullscreen ou regra KWin | `WindowPlacement.Fullscreen` |
| tooltip sem foco | ✅ | ✅ | ✅/⚠️ regra KWin `keepAbove` | script auxiliar |
| clipboard | ✅ | ✅ | ⚠️ só com foco | não usado hoje |
| empacotamento | Exe/Msi | `.deb`/AppImage | **AppImage** (Steam Deck) | Compose `TargetFormat` |

**Decisão de escopo:** Wayland é **cidadão de primeira classe** no KDE Plasma. O alvo de aceite é o
Steam Deck em Desktop Mode; o Game Mode (Gamescope) fica documentado como melhor esforço, porque não
roda KWin scripting e não tem bandeja.

---

## 2. Fatos técnicos que sustentam o plano

Estes não são detalhes internos do Yabai OCR; são as APIs que o plano usa. Confirmados na
documentação do projeto e no código do OpenJDK/KDE.

### 2.1 Captura: `KWin.ScreenShot2` no KDE, portal `Screenshot` no GNOME, Robot/ScreenCast nos outros

- **KDE Wayland (Steam Deck):** o caminho principal é `org.kde.KWin.ScreenShot2`
  (`CaptureWorkspace` e `CaptureArea`), que entrega um QImage cru por file descriptor sem abrir o
  diálogo do portal. O app converte `RGBX8888`/`RGBA8888` e `ARGB32`/`ARGB32_Premultiplied` (formato
  6, o que o Plasma 6 devolve de fato) respeitando `stride` e o tamanho nativo. O KWin só autoriza
  quem tem `X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2` numa entrada `.desktop` cujo
  `Exec` seja o próprio `/proc/<pid>/exe` **e que viva em diretório privilegiado**
  (`/usr/share/applications`). A entrada oculta `yabai-ocr-screenshot.desktop` fica em
  `~/.local/share/applications` e por isso **não** autoriza: num AppImage o exe é
  `/tmp/.mount_*/…` e nenhuma entrada fixa casa (D61). A saída no AppImage é
  `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1` em `~/.config/environment.d/` (Ajustes → «Captura
  silenciosa no KDE»), que vale a partir do login seguinte. Se a interface não existir, a chamada for
  recusada ou o formato for desconhecido, ele cai para o portal `Screenshot`

  Em telas múltiplas, os Ajustes escolhem o monitor da captura inteira e do recorte (`PRIMARY`,
  `MOUSE` ou `DISPLAY:<índice>`): no KWin o principal continua saindo do workspace recortado e os
  demais saem de `CaptureArea` com o retângulo do monitor; no portal a área é recortada da imagem do
  workspace. O hover não segue essa preferência — ele sempre usa o monitor do ponteiro. No Wayland a
  janela de seleção abre em tela cheia na tela em que o compositor a colocou, porque posicionamento
  absoluto é ignorado
  (`interactive=false`), que devolve um PNG do workspace e pode apresentar o diálogo do KDE. O recorte
  do monitor/região é feito pelo app nos dois caminhos.
- **Por que não usar `Robot`/ScreenCast no KDE:** o JDK 21.0.13 valida o *restore token* como UUID
  (`g_uuid_string_is_valid` em `screencast_portal.c`), mas o token do KDE é base64
  (`O0ci...=`) e nunca é enviado. Toda captura abre uma sessão nova e volta a pedir a tela
  ([JDK-8396405](https://bugs.openjdk.org/browse/JDK-8396405)). O caminho AWT continua correto em
  X11/Windows e em Wayland não-KDE, onde o token do portal pode ser UUID.
- **GNOME Wayland:** `PortalScreenshotCapture` (`org.freedesktop.portal.Screenshot` com
  `interactive=false`/`modal=false`), o mesmo caminho one-shot usado no fallback do KDE. Não usa
  `Robot`: o JDK abre uma sessão ScreenCast por captura no GNOME, porque o *restore token* não é
  reaproveitado, e o Shell mostra o indicador de compartilhamento a cada hover — medido à parte, o
  PNG do portal sai em **128 ms** (1280×800 aqui), sem diálogo.
- **Outros Wayland:** `ScreencastScreenCapture` mantém o caminho do JDK 21
  (`org.freedesktop.portal.ScreenCast` + PipeWire, JDK-8280982). A propriedade
  **`awt.robot.screenshotMethod`** continua sendo definida antes do AWT:
  - `x11` — caminho X11;
  - `dbusScreencast` — padrão em Wayland (GNOME < 47);
  - `dbusRemoteDesktop` — GNOME ≥ 47 (usa o portal RemoteDesktop).
- Nos caminhos do JDK, o *restore token* fica em `~/.java/robot/screencast-tokens.properties` (ou
  `.awt/robot/...`). No KDE ele não é usado; o app não lê, copia nem registra token.
- O runtime empacotado é JDK 21+; compilar para bytecode 17 continua valendo.
- `Robot.createMultiResolutionScreenCapture` pode devolver uma única variante no ScreenCast; o código
  cai em `createScreenCapture` quando não há variante nativa.

### 2.2 Atalhos globais: portal `GlobalShortcuts`

- `org.freedesktop.portal.GlobalShortcuts`, implementado pelo KDE desde o Plasma 5.27 e presente no
  Plasma 6.4/6.7 do SteamOS. É o mecanismo padrão para atalhos globais no Wayland.
- Fluxo: criar sessão → `BindShortcuts` com uma lista `(id, {description, preferred_trigger})` → o
  compositor mostra/gerencia a atribuição → o app escuta `Activated`/`Deactivated`.
- `preferred_trigger` usa a especificação XDG (xkbcommon): `CTRL+SHIFT+O`, `ALT+F1`, `LOGO+Return`.
  Modificadores: `CTRL`, `ALT`, `SHIFT`, `NUM`, `LOGO`; tecla = nome de keysym sem `XKB_KEY_`.
- O portal identifica o aplicativo pelo **app id**, que vem do arquivo `.desktop` instalado. Isso
  significa que o AppImage precisa instalar (ou o usuário instalar) um `yabai-ocr.desktop` para os
  atalhos e a captura terem identidade estável.
- Se o portal não existir ou o usuário negar, o app não pode fingir que registrou o atalho.

### 2.3 Ponteiro global: KWin script + D-Bus

- `MouseInfo.getPointerInfo()` continua **sem posição global no Wayland**. JNativeHook também não
  recebe eventos fora da janela. Não há como “consertar” isso no cliente.
- O KWin expõe `workspace.cursorPos` e o sinal `workspace.cursorPosChanged` para scripts. Um script
  pequeno pode ler a posição e entregá-la ao app via `callDBus`.
- O app precisa **expor um serviço D-Bus** para receber a posição. A biblioteca escolhida é
  `dbus-java` (transporte unixsocket em Java puro, sem `libdbus` nativa), com o nome
  `dev.yabai.Ocr` e o método `CursorMoved(x, y)`.
- O script é instalado em `~/.local/share/kwin/scripts/yabai-ocr/` (metadata.json + main.js) e
  carregado em runtime via `org.kde.KWin.Scripting.loadScript`/`start`. Não exige reiniciar o KWin.
- O script também serve para **regras de janela** (manter o tooltip acima e fora da barra de tarefas)
  e como **fallback de atalhos** (`registerShortcut`) se o portal não estiver disponível.
- Limitação: no GNOME não existe API equivalente fora do Shell. O app cobre isso com a extensão
  `yabai-ocr@yabai.dev` (D58), que entrega a mesma mensagem D-Bus; a extensão só é lida no início da
  sessão, então a primeira ativação pede um log out. Em compositores sem script nem extensão (Sway,
  Gamescope) o hover continua indisponível, com mensagem clara e sem `MouseInfo`.

### 2.4 SteamOS

- SteamOS 3.8 usa **KDE Plasma 6.4.3**; SteamOS 3.9 usa **6.7.3**. Ambos iniciam o Desktop Mode em
  **Wayland** por padrão. O projeto deve suportar as duas versões.
- O sistema de arquivos raiz é imutável. Instalação de usuário vai em `$HOME`:
  `~/Applications` (AppImage), `~/.local/share/applications` (`.desktop`),
  `~/.local/share/icons` (ícone), `~/.config/autostart` (início automático) e `~/.yabai_ocr` (dados).
- O **Game Mode** roda sobre Gamescope, sem KWin scripting e sem bandeja. Não é o alvo do suporte
  completo; o Desktop Mode é.
- Atualizações do SteamOS trocam a versão do Plasma. No caminho direto a captura não depende da
  permissão do portal; se o fallback `Screenshot` for usado, a permissão pode ser revogada e o app
  precisa reautorizar sem crash. O script KWin precisa ser recarregado se a pasta do usuário
  permanecer (ela permanece).

---

## 3. Arquitetura da camada de plataforma

Não transformar `:desktop` em Kotlin Multiplatform: ele é um módulo JVM único e a maior parte do
código é comum. A solução é **interfaces pequenas, escolhidas em runtime**, com as implementações
Windows mantendo exatamente o código atual.

### 3.1 `DesktopPlatform.kt` (novo)

Responsabilidades:

- detectar família de SO (`WINDOWS`, `LINUX`, `MACOS`, `OTHER`);
- detectar sessão gráfica (`X11`, `WAYLAND`, `UNKNOWN`);
- detectar desktop (`KDE`, `GNOME`, `OTHER`) e se é SteamOS;
- expor capacidades resolvidas uma vez: `capture`, `hotkeys`, `hover`, `tray`;
- centralizar o que hoje está espalhado como `System.getProperty("os.name").lowercase()`.

Proposta de API pura e testável (nada de ler ambiente direto no teste):

```kotlin
enum class OsFamily { WINDOWS, LINUX, MACOS, OTHER }
enum class SessionType { X11, WAYLAND, UNKNOWN }
enum class DesktopFamily { KDE, GNOME, OTHER, UNKNOWN }

internal fun detectOsFamily(osName: String): OsFamily
internal fun detectSessionType(env: Map<String, String>): SessionType
internal fun detectDesktopFamily(env: Map<String, String>): DesktopFamily
internal fun isSteamOs(env: Map<String, String>, osRelease: String?): Boolean

object DesktopPlatform {
    val family: OsFamily = detectOsFamily(System.getProperty("os.name").orEmpty())
    val session: SessionType = detectSessionType(System.getenv())
    val desktop: DesktopFamily = detectDesktopFamily(System.getenv())
}
```

Detecção de sessão (não confiar só em `XDG_SESSION_TYPE`):

```kotlin
fun detectSessionType(env: Map<String, String>): SessionType {
    val session = env["XDG_SESSION_TYPE"]?.lowercase()
    if (session == "wayland") return SessionType.WAYLAND
    if (session == "x11") return SessionType.X11
    if (!env["WAYLAND_DISPLAY"].isNullOrBlank()) return SessionType.WAYLAND
    if (!env["DISPLAY"].isNullOrBlank()) return SessionType.X11
    return SessionType.UNKNOWN
}
```

> XWayland deixa `DISPLAY` **e** `WAYLAND_DISPLAY` definidos. A ordem prioriza `XDG_SESSION_TYPE` e,
> na falta dele, `WAYLAND_DISPLAY`. Para captura/atalhos, tratar como Wayland é o lado seguro: o
> backend Wayland funciona; o X11 não veria janelas nativas.

### 3.2 Interfaces de backend

```kotlin
interface Autostart {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}

interface ScreenCaptureBackend {
    fun availability(): CaptureAvailability   // READY, NEEDS_PERMISSION, DENIED, UNSUPPORTED
    fun screenBounds(): Rectangle
    fun captureScreen(): BufferedImage
    fun captureRegion(rect: Rectangle): BufferedImage
}

interface PointerLocationProvider {
    val available: Boolean
    fun start(onMove: (Point) -> Unit)
    fun stop()
}

interface HotkeyBackend {
    fun start(): Boolean
    fun stop()
    fun updateHotkeys(bindings: List<HotkeyBinding>): Boolean
}
```

Implementações:

| interface | Windows | Linux X11 | Linux Wayland (KDE) |
| --- | --- | --- | --- |
| `Autostart` | `WindowsAutostart` (registro atual) | `LinuxAutostart` (XDG) | `LinuxAutostart` (XDG) |
| `ScreenCaptureBackend` | `RobotScreenCapture` (atual) | `RobotScreenCapture` (atual) | `RobotScreencastCapture` (JDK 21) |
| `PointerLocationProvider` | `AwtPointerProvider` (`MouseInfo`) | `AwtPointerProvider` | `KwinPointerSource` (D-Bus/KWin) |
| `HotkeyBackend` | `JNativeHookHotkeys` | `JNativeHookHotkeys` | `PortalGlobalShortcuts` → `KWinScript` |

`PlatformServices` resolve tudo uma vez e é injetado em `Main.kt`. Nenhum componente de UI pergunta o
SO diretamente.

---

## 4. Captura de tela no Wayland

### 4.1 Estratégia

`PlatformServices` escolhe três backends:

- **Windows / Linux X11:** `RobotScreenCapture`, exatamente como antes.
- **KDE Wayland:** `KWinScreenshotCapture`, que tenta `org.kde.KWin.ScreenShot2`
  (`CaptureWorkspace`/`CaptureArea`) e converte o QImage cru devolvido pelo FD; em falha, cai para
  `PortalScreenshotCapture`, que fala `org.freedesktop.portal.Screenshot` com
  `interactive=false`, lê o PNG devolvido e recorta o monitor principal/região. O caminho direto não
  usa `X-KDE-DBUS-Restricted-Interfaces`; o portal é o fallback compatível.
- **GNOME Wayland:** `PortalScreenshotCapture` (portal `Screenshot`, one-shot).
- **Outros Wayland:** `ScreencastScreenCapture` (JDK 21 ScreenCast), com
  `awt.robot.screenshotMethod=dbusScreencast` definido antes do AWT.

```kotlin
// Main.kt, antes de qualquer uso de AWT/Toolkit
if (DesktopPlatform.isLinux && DesktopPlatform.isWayland) {
    System.setProperty("awt.robot.screenshotMethod", "dbusScreencast")
}
```

Regras:

1. A propriedade precisa ser definida **antes** da primeira inicialização do Toolkit/AWT. Colocar no
   topo de `main`, antes de `application { }`.
2. `KdeWaylandCapture` prefere o `ScreenShot2`; se a interface não existir ou a chamada falhar, usa
   `PortalScreenshotCapture`. O portal devolve o workspace inteiro, e o recorte usa a escala entre
   `virtualScreenBounds()` e o PNG.
3. Nada de `x11` explícito em Wayland: forçar X11 capturaria só o XWayland (janela do próprio app ou
   preto).
4. O `ScreenShot2` é a rota direta do KWin; o portal fica como fallback para versões/ambientes em que
   a interface não esteja disponível.

### 4.2 Autorização: caminho direto sem diálogo, portal como fallback

- No KDE Wayland, o caminho principal (`org.kde.KWin.ScreenShot2`) captura sem apresentar o diálogo
  do portal. É a rota usada para tela inteira, recorte e hover quando a interface existe.
- O portal `org.freedesktop.portal.Screenshot` com `interactive=false` **não é silencioso no KDE**:
  ele pode criar/apresentar um `QuickDialog` a cada chamada. Por isso ele é apenas fallback, não a
  rota normal. A permissão do portal é ligada à identidade/caminho do app; mover ou renomear o
  AppImage pode exigir nova autorização.
- A chamada pode bloquear enquanto o diálogo está aberto; o fluxo atual captura na thread da ação.
  Se isso incomodar no fallback, mover a captura para `Dispatchers.IO` numa próxima tarefa.
- No KDE, o token legado do JDK em `~/.java/robot/screencast-tokens.properties` não é usado e pode ser
  apagado. Nos outros Wayland ele continua sendo do JDK; o app **não** deve ler, copiar nem registrar.
- `availability()` deve distinguir:
  - `READY` — `ScreenShot2` disponível, portal `Screenshot` presente ou token existente no caminho
    ScreenCast;
  - `NEEDS_PERMISSION` — ainda não há token (caminho ScreenCast);
  - `DENIED` — o portal recusou ou o usuário fechou o diálogo;
  - `UNSUPPORTED` — sem portal (`xdg-desktop-portal-kde` ausente) ou JDK < 21.
- Negação não pode derrubar o app. `SecurityException`, timeout e retorno sem pixels viram
  `ScreenCaptureException`, nunca `printStackTrace` e janela vazia.
- Se o fallback for negado, o usuário pode reautorizar no diálogo do sistema/configurações de
  privacidade do KDE; a UI mostra o motivo.

### 4.3 Escala, HiDPI e tamanho nativo

- Manter `nativeCaptureSize(bounds, scaleX, scaleY)` e seus testes. A regra de ouro do projeto
  continua: pedir o tamanho nativo (`bounds × escala`) e desenhar 1:1, sem reamostrar.
- No caminho screencast, verificar experimentalmente se `GraphicsConfiguration.defaultTransform` e as
  variantes de resolução se comportam como no X11. Se só houver uma variante, aceitar a imagem como
  vier e **não** converter dp duas vezes.
- Steam Deck: 1280×800, escala 1.0 no Desktop Mode. O caso crítico é KDE com escala 125%/150% em
  monitor externo; teste obrigatório.
- Se a imagem vier menor/maior que o esperado, a correção é no mapeamento de coordenadas, nunca um
  “resize estético” que borre glifos.

### 4.4 Captura para hover

O hover pede `CaptureArea` ao `ScreenShot2`, então captura só a região ao redor do ponteiro, sem
diálogo. No fallback pelo portal `Screenshot`, a chamada devolve o workspace inteiro e o app recorta;
nesse caminho o KDE pode apresentar o `QuickDialog` a cada captura.

No GNOME Wayland o hover sempre passa pelo portal `Screenshot` (D62): uma chamada por leitura
devolve o workspace inteiro, o app recorta a região do ponteiro e apaga o PNG. Não há sessão
ScreenCast, então não aparece o indicador de compartilhamento do Shell a cada movimento parado.

- A mesma rota atende seletor de tela inteira, recorte e hover; não há sessão ScreenCast aberta.
- Medir no Steam Deck: latência de uma captura e custo de CPU durante o hover. Se o caminho direto
  falhar e o fallback for caro, a otimização precisa ser medida (cache de frame ≤ 150 ms) e não pode
  introduzir texto desatualizado perceptível.
- O cursor **não** aparece nos pixels; a posição vem do KWin (§6).

---

## 5. Atalhos globais no Wayland

### 5.1 Backend principal: `org.freedesktop.portal.GlobalShortcuts`

Novo `PortalGlobalShortcuts.kt`, usando `dbus-java` (mesma biblioteca do serviço de hover).

Fluxo:

1. Abrir conexão de sessão; verificar `org.freedesktop.portal.Desktop` e a interface
   `org.freedesktop.portal.GlobalShortcuts`.
2. `CreateSession` com `handle_token`/`session_handle_token`; aguardar `Response`.
3. `BindShortcuts(session, shortcuts, parent_window = "", options)` com um `id` estável por ação:
   - `capture-fullscreen`, `capture-region`, `quick-dict`, `hover-toggle`, `dict-cycle`.
   - `description` em pt-BR (o diálogo do sistema mostra esse texto).
   - `preferred_trigger` convertido do atalho salvo para o formato XDG.
4. Escutar `Activated(session, shortcut_id, timestamp, options)` e despachar para as mesmas ações do
   menu/`HotkeyMatcher`.
5. Manter a sessão viva enquanto o app existir. Se o portal reiniciar, recriar e re-bindar; não
   derrubar o app.

Conversão de atalho (função pura testável `toXdgTrigger`):

| configuração hoje | XDG |
| --- | --- |
| `Ctrl + Shift + O` | `CTRL+SHIFT+O` |
| `Alt + F1` | `ALT+F1` |
| `Super + Space` | `LOGO+space` |
| `Ctrl + Shift + Insert` | `CTRL+SHIFT+Insert` |

- A tecla usa o nome de keysym (`O`, `Return`, `space`, `F1`, `Insert`), sem prefixo `XKB_KEY_`.
- O parser deve recusar combinação vazia/ambígua e devolver `null`; nesse caso o atalho não é enviado
  ao portal e a UI mostra “combinação não suportada no Wayland”.
- O portal pode deixar o usuário trocar a combinação. Depois de `BindShortcuts`, usar
  `ListShortcuts` para exibir a combinação efetiva nos Ajustes; **não sobrescrever** a escolha do
  usuário com a preferência antiga a cada início.

### 5.2 Fallback KDE: `registerShortcut` no KWin script

Se o portal não existir (KDE antigo, `xdg-desktop-portal-kde` quebrado) ou o usuário negar, o script
KWin do §6 registra os mesmos atalhos:

```js
registerShortcut("Yabai Ocr Fullscreen", "Yabai OCR: capturar tela inteira",
                 "Ctrl+Shift+O", () => notifyShortcut("capture-fullscreen"));
```

`notifyShortcut(id)` chama o D-Bus `dev.yabai.Ocr.Shortcuts.ShortcutPressed(id)` no app. Vantagens:
funciona no Plasma 6.4/6.7 sem portal. Desvantagens: as combinações ficam sob responsabilidade do
script (o usuário as edita em Atalhos do Sistema, não nos Ajustes do app) e exigem o script ativo.

Ordem de escolha: **portal primeiro**, KWin como fallback, indisponível por último. A UI informa qual
backend está ativo.

> **Estado na P4:** o portal está implementado (`PortalGlobalShortcuts`, `HotkeyBackend`,
> `HotkeyService`) e o fallback KWin está implementado em `KWinScript`/`KWinIntegration`. O script só
> recebe o bloco `registerShortcut` quando o portal terminou em `UNAVAILABLE`; com o portal ativo, o
> script serve apenas ao hover. Falha dos dois caminhos mantém as ações no menu da bandeja e o motivo
> na tela de Ajustes.

### 5.3 X11 e Windows

- X11 continua com JNativeHook 2.2.2, exatamente como hoje.
- Windows continua com JNativeHook, sem nenhuma chamada a portal ou D-Bus.
- No Wayland, **não iniciar** JNativeHook: ele falha e polui o log. A escolha é feita por
  `PlatformServices`.
- `HotkeyMatcher` já aceita `meta`/`win`/`super`/`cmd`; no Wayland o parse continua útil para
  converter a configuração em `preferred_trigger`. Não alterar a semântica dele.

### 5.4 Captura de atalho nos Ajustes

`SettingsWindow` continua capturando `KeyEvent` com a janela em foco (isso funciona no Wayland).
Depois de capturar:

- Windows/X11: salva e chama `updateHotkeys` como hoje.
- Wayland: salva, converte para XDG e re-binda no portal. Se o portal recusar, mostrar aviso e manter o
  valor salvo para uma próxima tentativa.

---

## 6. Hover no Wayland

### 6.1 Componentes

1. **`KWinHoverBridge.kt` (novo)** — instala/carrega/descarrega o script KWin; expõe
   `available`, `start(onMove)`, `stop()`. Só é criado quando `session == WAYLAND && desktop == KDE`.
2. **`YabaiDbusService.kt` (novo)** — sobe `dev.yabai.Ocr` na sessão D-Bus, exporta
   `dev.yabai.Ocr.Hover.CursorMoved(x, y)` e `dev.yabai.Ocr.Shortcuts.ShortcutPressed(id)`.
3. **Script KWin** (`package/linux/kwin/yabai-ocr/`):
   - `metadata.json` com `X-Plasma-API: javascript` e `X-Plasma-MainScript: code/main.js`;
   - `code/main.js` lê `workspace.cursorPos` no sinal `cursorPosChanged`, limita a ~10 Hz e chama
     `callDBus(... CursorMoved ...)`;
   - opcionalmente aplica `keepAbove`/`skipTaskbar` a janelas com título marcado (tooltip);
   - registra os atalhos de fallback (§5.2).

Esboço do script:

```js
let last = 0;
function pushCursor() {
    const now = Date.now();
    if (now - last < 100) return;          // 10 Hz é mais que suficiente para o hover
    last = now;
    const p = workspace.cursorPos;
    callDBus("dev.yabai.Ocr", "/dev/yabai/Ocr/Hover",
             "dev.yabai.Ocr.Hover", "CursorMoved", p.x, p.y);
}
workspace.cursorPosChanged.connect(pushCursor);
```

### 6.2 Instalação e ciclo de vida

- Na primeira vez que o hover é ligado (ou quando o app inicia com hover salvo como ligado):
  - copiar o script para `~/.local/share/kwin/scripts/yabai-ocr/` se não existir ou se a versão mudou;
  - chamar `org.kde.KWin.Scripting.loadScript("<...>/code/main.js", "yabai-ocr")`;
  - chamar `start()`; verificar `isScriptLoaded("yabai-ocr")`.
- Não reiniciar o KWin. Não pedir root. Não escrever em `/usr`.
- Ao desligar o hover: parar de processar no app e, opcionalmente, `unloadScript("yabai-ocr")`.
  Manter o script carregado é aceitável (custo desprezível), mas ele deve ficar ocioso se o hover
  estiver desligado.
- Se o script não carregar: hover `UNSUPPORTED` com motivo “não foi possível ativar o bridge do KWin”.
  Não iniciar o poll de `MouseInfo`, que no Wayland não funciona.

> **Estado na P4:** implementado em `KWinHoverBridge` (instalação + `loadScript`/`start`/
> `isScriptLoaded`/`unloadScript`), `YabaiDbusService` (bus name e dois objetos) e `KWinIntegration`
> (ordem D-Bus → script, rollback e status). O script está em
> `desktop/src/main/resources/kwin/yabai-ocr/` e é copiado para `~/.local/share/kwin/scripts/yabai-ocr/`
> só quando o conteúdo muda. O tooltip ainda depende de `alwaysOnTop` como melhor esforço: o
> `keepAbove` pelo script ficou para o polimento (P5).

### 6.3 Mapeamento de coordenadas e recorte

- `workspace.cursorPos` devolve coordenadas globais lógicas do KWin. O AWT no Wayland também trabalha
  em unidades lógicas; no Steam Deck (1.0) a igualdade é direta.
- Com escala fracionária/monitor externo, validar se é preciso arredondar ou aplicar a transformação
  do dispositivo. A conversão deve ser uma função pura `toCaptureRect(cursor, bounds, scale)` com
  teste de mesa, não aritmética espalhada.
- Reutilizar `HoverGeometry`/`HoverGate`/`HoverDismissGate` como estão. Só a fonte do ponteiro e a
  origem da imagem mudam.
- Deduplicar posições repetidas e ignorar movimento fora dos monitores. O script já limita a taxa; o
  app também deve limitar o trabalho por captura.
- O tooltip continua com `focusable = false`; no KDE, o script marca a janela como `keepAbove` e
  `skipTaskbar` para compensar a ausência de `alwaysOnTop` confiável no Wayland.

### 6.4 Limites

- **GNOME:** coberto pela extensão do Shell (D58). O app grava
  `$XDG_DATA_HOME/gnome-shell/extensions/yabai-ocr@yabai.dev/` (`metadata.json` + `extension.js`) e
  ativa gravando `org.gnome.shell enabled-extensions` via `gsettings` (com a lista atual lida antes,
  para não perder as extensões do usuário). `gnome-extensions enable` não serve aqui: ele consulta a
  Shell em execução, que só conhece o que encontrou no início da sessão. Por isso a janela de shell
  atual não carrega a extensão e o estado fica “instalada, precisa reiniciar a sessão” até o próximo
  login.
- Sway, Gamescope e outros compositores: sem script nem extensão, não há API de ponteiro global.
  Hover indisponível, com mensagem e sem tentativa de `MouseInfo`.
- SteamOS Game Mode: não roda o script do KWin; hover indisponível.
- Futuro: metadado de cursor do PipeWire (`spa_meta_cursor`). Fora desta entrega.

---

## 7. Janelas, pickers e tooltip no Wayland

O compositor decide posição, foco e empilhamento. O código atual define `setLocation`, `alwaysOnTop` e
tamanho em bounds AWT; no Wayland isso pode ser ignorado. As correções são comportamentais, não
estéticas.

### 7.1 Regra geral

- **Seletor de tela inteira e recorte à mão:** capturar primeiro, depois abrir a janela em
  `WindowPlacement.Fullscreen` (não posicionar em `x/y`). Fullscreen é suportado pelo `xdg_toplevel` e
  garante que o overlay cubra o monitor sem depender de `alwaysOnTop`.
- **Não** converter bounds para `.dp` de novo. Manter a regra 1:1 e `FilterQuality.None`.
- **Foco de teclado:** fullscreen deve receber foco para `Escape`. Testar no KDE; se o compositor
  negar foco, oferecer botão «Cancelar» visível como saída.
- **Multi-monitor:** o comportamento atual captura o monitor principal. Manter isso no Wayland e
  documentar; não prometer captura do monitor sob o ponteiro nesta fase.
- **Resultado:** continua uma janela normal. `alwaysOnTop` vira melhor esforço; não bloquear o fluxo
  se o compositor ignorar.

### 7.2 `RegionSelectionWindow.kt` e `SegmentPickerWindow.kt`

- Adicionar um caminho `WindowPlacement.Fullscreen` quando `PlatformServices.session == WAYLAND`.
- Manter geometria/recorte intactos; a imagem já foi capturada antes de abrir.
- Validar arrasto, `Escape`, clique fora e o recorte 1:1 no KDE Wayland.
- Se a janela fullscreen abrir no monitor errado, não “corrigir” com posição absoluta; usar a API de
  fullscreen do dispositivo ativo e documentar a limitação.

### 7.3 `HoverTooltipWindow.kt`

- Continua `transparent = true`, `focusable = false`, fundo transparente.
- Verificar suporte a `PERPIXEL_TRANSLUCENT`; se ausente, usar fundo opaco arredondado (teste puro
  `useTranslucentBackground(supported)`).
- No KDE Wayland, o script auxiliar aplica `keepAbove` e `skipTaskbar` à janela marcada. Fora do KDE,
  o tooltip pode ficar atrás; documentar.
- Não roubar foco do aplicativo em uso.

### 7.4 `Main.kt`

- Definir `awt.robot.screenshotMethod` antes de qualquer AWT.
- Subir bandeja antes de decidir `showMainWindow`; se `--minimized` e a bandeja falhar, abrir a janela
  principal.
- Refletir o estado de captura/atalhos/hover na UI e no menu da bandeja.
- Passar `PlatformServices` aos componentes; não instanciar `Robot`/`SystemTray` direto.
- Tolerar falha de portal/D-Bus sem encerrar o app.

---

## 8. Bandeja e autostart

### 8.1 Bandeja

- KDE Plasma (Steam Deck) suporta StatusNotifier/AppIndicator nativamente. No **KDE Wayland** o app
  publica um `org.kde.StatusNotifierItem` próprio; o `TrayIcon` AWT (XEmbed) fica só para X11 e
  Windows.
- **Menu no Wayland:** o app publica `com.canonical.dbusmenu` no caminho `SNI_MENU_PATH`; o Plasma
  pede o layout, desenha o menu e o fecha nativamente ao clicar fora. `ContextMenu(x,y)` e o
  `JPopupMenu` continuam como fallback para hosts sem DBusMenu. Não depender do `PopupMenu` nativo do
  AWT nem de `MouseInfo` no Wayland. Windows e X11 mantêm o caminho anterior.
- `SystemTrayManager.setup()` deve devolver `Boolean` (se o ícone foi adicionado). Se `--minimized` e
  falhou, mostrar a janela principal.
- `closeToTray` sem bandeja não pode esconder o app para sempre: encerrar ou avisar.
- O Game Mode não tem bandeja; o app aberto por lá deve mostrar a janela.

**Implementado na P5:** `TrayMenu.kt` escolhe `NATIVE` no Linux X11 e `CUSTOM` no Windows;
`SystemTrayManager.setup()` devolve se o ícone entrou e `Main.kt` abre a janela quando `--minimized`
falhou; `closeToTray` também exige `trayReady`; `updateHover(active)` mantém o rótulo do menu
sincronizado. O script KWin marca a janela `Yabai OCR - Hover` com `keepAbove` e `skipTaskbar`,
porque `alwaysOnTop` é só melhor esforço no Wayland.

**Correção D35 (09/11/2026):** o teste real no Steam Deck mostrou que o KDE Wayland não entrega o
popup nativo do AWT quando o clique direito chega ao ícone. O menu customizado foi ancorado em
`MouseInfo` quando existe ponteiro global e no canto inferior direito da área útil quando não existe.

**Correção D36 (09/11/2026):** o clique direito continuava sem menu porque o `TrayIcon` AWT é XEmbed
e o proxy do Plasma no Wayland não entrega o evento de botão ao processo Java. O app passou a
publicar um StatusNotifierItem nativo (`StatusNotifierTray.kt`), registrado em
`org.kde.StatusNotifierWatcher`; `Activate` faz a captura de tela inteira, `SecondaryActivate` o
recorte e o DBusMenu desenha o menu no Plasma (`ContextMenu(x,y)` fica como fallback). O runtime também passou a incluir
`jdk.security.auth` (SASL EXTERNAL usa `UnixSystem`); sem ele o D-Bus morria antes de publicar
`dev.yabai.Ocr` e antes de falar com o portal.

### 8.2 Autostart XDG

- `StartupManager` vira fachada; `WindowsAutostart` mantém o registro atual; `LinuxAutostart` grava
  `$XDG_CONFIG_HOME/autostart/yabai-ocr.desktop` (padrão `~/.config/autostart`).
- Conteúdo:

```ini
[Desktop Entry]
Type=Application
Name=Yabai OCR
Comment=Leitor de japonês com OCR
Exec="/caminho/com espaço/Yabai_OCR.AppImage" --minimized
Icon=yabai-ocr
Terminal=false
Categories=Utility;
X-GNOME-Autostart-enabled=true
```

- `Exec` **não é shell**: caminho com espaço exige aspas e escape de `\`, `"`, `` ` `` e `$`. Função
  pura testável (`desktopEntryExec(path, args)`).
- Descoberta do executável, nesta ordem:
  1. `APPIMAGE` (AppImage em execução);
  2. `jpackage.app-path` (launcher empacotado);
  3. `ProcessHandle.current().info().command()`, ignorando `java`/`javaw`;
  4. `~/.local/bin/yabai-ocr`, `/usr/bin/yabai-ocr`, `/opt/yabai-ocr/bin/Yabai OCR`;
  5. `~/Applications/*.AppImage` e `~/.local/opt/...` (instalação do usuário).
- Se nada for encontrado, **não gravar um `.desktop` quebrado**; registrar o motivo e tentar de novo
  na próxima abertura.
- Desligar apaga o arquivo (não apenas `Hidden=true`).
- `isEnabled()` verifica o arquivo e, quando possível, se o `Exec` aponta para algo existente.

---

## 9. Steam Deck / SteamOS: o alvo principal

### 9.1 Instalação recomendada

> Requisitos, dependências e passo a passo detalhado no SteamOS: [STEAMOS.md](STEAMOS.md).

1. Baixar `Yabai_OCR-<versao>-x86_64.AppImage` e colocar em `~/Applications/`.
2. `chmod +x` no arquivo.
3. Abrir uma vez; o app oferece “Integrar ao sistema” (cartão “Integração com o Linux”), que copia:
   - `yabai-ocr.desktop` para `$XDG_DATA_HOME/applications/` (padrão `~/.local/share/applications/`);
   - `yabai-ocr.png` para `$XDG_DATA_HOME/icons/hicolor/256x256/apps/`;
   - (opcional) atalho na área de trabalho.
4. O `.desktop` usa `Exec=<caminho real do AppImage>` e `Icon=yabai-ocr`.
5. Autostart é criado pelo toggle dos Ajustes em `~/.config/autostart/`.
6. Se o release publicar um AppImage, o botão “Baixar Atualização” abre o asset direto; sem asset
   correspondente ao SO, abre a página do release.

Por que AppImage:

- o sistema raiz é imutável; nada de `apt install` ou escrever em `/usr`;
- o runtime Java vai embutido (JDK 21 com `jdk.net` e o caminho ScreenCast para Wayland não-KDE);
- atualizar é substituir o arquivo, mantendo `~/.yabai_ocr`;
- preserva acesso ao D-Bus e ao KWin scripting, diferente de um Flatpak sandbox.

`.deb` continua sendo produzido para outras distros; Flatpak fica como opcional e **não** é o caminho
recomendado para hover (o sandbox exige `--talk-name=org.kde.KWin` e permissões extras).

### 9.2 Desktop Mode x Game Mode

| recurso | Desktop Mode (Plasma/Wayland) | Game Mode (Gamescope) |
| --- | --- | --- |
| UI, OCR, dicionários, tradução | ✅ | ✅ (janela normal) |
| captura (ScreenShot2/portal) | ✅ | ⚠️ depende do portal da sessão Gamescope |
| atalhos globais | ✅ portal/KWin | ❌ sem KWin |
| hover | ✅ KWin bridge | ❌ |
| bandeja | ✅ Plasma | ❌ |
| autostart | ✅ XDG | ⚠️ iniciar junto com o Steam |

**Critério:** paridade completa no **Desktop Mode**. Game Mode é melhor esforço e deve ser documentado
como tal, sem prometer hover/atalhos.

### 9.3 Particularidades do SteamOS

- **Atualizações:** Plasma pode mudar de 6.4 para 6.7. Testar os dois; não depender de API exclusiva de
  uma versão.
- **Permissão de captura:** no KDE o caminho direto é `ScreenShot2`; se ele falhar e o portal
  `Screenshot` for usado, vale a permissão do portal, não o token do JDK. Se ela for revogada numa
  atualização, o app pede de novo sem crash. O token legado do JDK
  (`~/.java/robot/screencast-tokens.properties`) não é usado nesse caminho.
- **Script KWin:** fica em `$HOME`, então sobrevive; recarregar após atualização do Plasma se o
  `loadScript` falhar. Editar um atalho regrava e recarrega o script; se o KWin recusar, o script
  anterior é restaurado.
- **Sono/retomada:** no KDE, o `ScreenShot2` e o portal são chamadas por captura; não há sessão
  contínua para cair. Nos outros Wayland, a sessão ScreenCast pode cair; tentar de novo na próxima
  captura e, se falhar, pedir reautorização.
- **Tela:** 1280×800, escala 1.0. Testar também dock com monitor externo e escala 125%/150%.
- **Controles:** o Steam Input não interfere em atalhos de teclado. Não mapear botões do controle nesta
  fase.
- **Fonte japonesa:** SteamOS já traz Noto CJK; ainda assim, validar que a UI não mostra caixas vazias.

---

## 10. Alterações por arquivo

### 10.1 Arquivos existentes

| arquivo | hoje | mudança | risco |
| --- | --- | --- | --- |
| `desktop/build.gradle.kts` | Exe/Msi, Inno, ZIP, Java 17 | adicionar `Deb`/`AppImage` condicionais ao host, `package/linux`, `dbus-java`, runtime JDK 21 no Linux e `jvmArgs` UTF-8 | médio |
| `StartupManager.kt` | registro Windows | fachada + `WindowsAutostart`/`LinuxAutostart` | baixo |
| `AppIconGenerator.kt` | `.ico` | escolher PNG no Linux; manter `.ico` no Windows | baixo |
| `SystemTrayManager.kt` | `JPopupMenu` + `MouseInfo`; `setup(): Unit` | `PopupMenu` nativo no Linux; `setup(): Boolean`; fallback | médio |
| `GlobalHotkeyManager.kt` | JNativeHook direto | preservado atrás de `JNativeHookHotkeys`; portal no Wayland | alto |
| `ScreenCapture.kt` | `Robot` multi-resolução | `ScreenCaptureBackend`; `KWin.ScreenShot2` com fallback para o portal no KDE Wayland, screencast nos outros | alto |
| `HoverOcrManager.kt` | `MouseInfo` + crop | `PointerSource` + `ScreenShot2` (fallback portal) no KDE; sem poll no Wayland | alto |
| `RegionSelectionWindow.kt` | posição/bounds + `alwaysOnTop` | fullscreen no Wayland | médio |
| `SegmentPickerWindow.kt` | idem | fullscreen no Wayland | médio |
| `HoverTooltipWindow.kt` | `alwaysOnTop`, transparente | regra KWin; fallback opaco | médio |
| `Main.kt` | ordem bandeja/atalhos/update | `screenshotMethod` antes do AWT; `PlatformServices`; fallback | médio |
| `SettingsWindow.kt` | textos Windows | cartão “Integração Linux”; estado de captura/atalhos/hover; sem apagar preferências | baixo |
| `DesktopAppUpdateChecker.kt` | URL genérica | escolher asset por SO (opcional, mas recomendado) | baixo |
| `DesktopSettings.kt` | `~/.yabai_ocr` | `YABAI_HOME` para testes/E2E | baixo |
| `ClipboardMonitor.kt` | não usado | documentar limitação; não é pré-requisito | nenhum |

### 10.2 Arquivos novos

| arquivo | papel |
| --- | --- |
| `DesktopPlatform.kt` | detecção de SO/sessão/desktop + capacidades |
| `PlatformServices.kt` | composição única dos backends |
| `LinuxAutostart.kt` | `.desktop` de autostart e escape de `Exec` |
| `LinuxDesktopEntry.kt` | geração do `.desktop` e integração do AppImage |
| `PortalGlobalShortcuts.kt` | sessão/bind/activated do portal (implementado na P3) |
| `HotkeyBackend.kt` | interface de atalhos, seleção e status (implementado na P3) |
| `HotkeyBindings.kt` | ids estáveis, descrições e despacho (implementado na P3) |
| `XdgTrigger.kt` | conversão do atalho para o formato XDG (implementado na P3) |
| `KWinScript.kt` | geração/instalação do script e conversão de atalho (implementado na P4) |
| `KWinHoverBridge.kt` | instala/carrega script e fala com `org.kde.KWin` (implementado na P4) |
| `YabaiDbusService.kt` | serviço `dev.yabai.Ocr` (hover + atalhos; implementado na P4) |
| `KWinIntegration.kt` | ordem D-Bus → script, rollback e status (implementado na P4) |
| `HoverCapability.kt` | seleção AWT × KWin × indisponível (implementado na P4) |
| `PointerSource.kt` | `AwtPointerSource` e `BridgePointerSource` com dedup (implementado na P4; o nome `BridgePointerSource` veio com a D58) |
| `desktop/src/main/resources/kwin/yabai-ocr/` | `metadata.json` e `code/main.js` (implementado na P4) |
| `LinuxCapabilities.kt` | probe de portal, KWin e bandeja (com timeout) |
| `desktop/package/linux/` | `.desktop`, ícones, script KWin |
| `desktop/src/main/resources/ic_scan_linux.png` | ícone 256/512 |
| `desktop/src/main/resources/ic_scan_linux_tray.png` | ícone 32 (opcional) |

### 10.3 `desktop/build.gradle.kts` (detalhe)

```kotlin
val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
val isLinuxHost = System.getProperty("os.name").lowercase().contains("linux")

nativeDistributions {
    appResourcesRootDir.set(project.file(if (isWindowsHost) "package/windows" else "package/linux"))
    targetFormats(
        *if (isWindowsHost) arrayOf(TargetFormat.Exe, TargetFormat.Msi)
        else arrayOf(TargetFormat.Deb, TargetFormat.AppImage)
    )
    // Nome do pacote: minúsculo e sem espaço no Linux; "Yabai OCR" no Windows.
    packageName = if (isWindowsHost) "Yabai OCR" else "yabai-ocr"
    // Windows: manter exatamente o bloco atual (iconFile, upgradeUuid, shortcut, menu)
    if (isLinuxHost) {
        linux {
            iconFile.set(project.file("src/main/resources/ic_scan_linux.png"))
            appCategory = "Utility"
            menuGroup = "Utility"
            debMaintainer = "SorrisoKDente <TODO: e-mail>"
            shortcut = true
        }
    }
}
```

- `appResourcesRootDir("package/windows")` não deve vazar para o Linux.
- Tarefas Inno/ZIP ficam dentro de `if (isWindowsHost)`.
- Adicionar `implementation("com.github.hypfvieh:dbus-java-core:5.1.1")` e o transporte
  `dbus-java-transport-junixsocket:5.1.1` (versões fixas; não usar `latest`). O transporte nativo do
  JDK (JEP 380) não passa `SCM_RIGHTS`; sem o junixsocket o FD do `org.kde.KWin.ScreenShot2` não
  chega ao KWin e o hover cai no portal com diálogo.
- Garantir que o runtime empacotado inclua `java.desktop`, `jdk.net`, `jdk.security.auth` e as
  bibliotecas nativas do `junixsocket`; o ScreenCast do JDK só é usado em Wayland não-KDE.
  Testar `:desktop:createDistributable` no JDK 21 e rodar o launcher em Wayland **antes** de publicar.
- `jvmArgs += "-Dfile.encoding=UTF-8"` para não depender do locale do SteamOS. Não é necessário
  `--add-opens`: o junixsocket lê o descritor pelo helper nativo, não por reflexão em
  `java.io.FileDescriptor.fd`.

### 10.4 `DesktopSettings.kt`

- Manter `~/.yabai_ocr` (não migrar para `XDG_DATA_HOME` agora: criaria dois lugares de configuração).
- Adicionar override `YABAI_HOME` para testes/E2E. Sem a variável, comportamento idêntico ao atual.
- Teste: `YABAI_HOME=/tmp/x` → `/tmp/x/dicts`; sem a variável → termina em `.yabai_ocr/dicts`.

### 10.5 `SettingsWindow.kt`

Novo cartão “Integração Linux”, visível só no Linux:

- **Sessão:** “Wayland (KDE Plasma 6.7.3)” ou “X11”.
- **Captura:** “Pronta”, “Aguardando autorização”, “Negada” ou “Sem portal”; botões «Autorizar
  captura» e «Esquecer permissão».
- **Atalhos:** “Portal do sistema”, “KWin” ou “Indisponível”.
- **Hover:** “Ativo”, “Desligado” ou “Indisponível: <motivo>”.
- **Bandeja:** “Ativa” ou “Indisponível nesta sessão”.
- **Sistema:** botão «Integrar ao menu de aplicativos» (AppImage).
- Textos de autostart: “Abrir minimizado ao entrar na sessão” no Linux; “Iniciar com o Windows”
  permanece no Windows.
- Nenhum campo de atalho é apagado ao detectar que o backend mudou.

---

## 11. Empacotamento e release

### 11.1 Formatos

| formato | tarefa | alvo | host |
| --- | --- | --- | --- |
| AppImage | `:desktop:packageLinuxAppImage` | Steam Deck, qualquer x86_64 | Linux + JDK 21 + `appimagetool` |
| `.deb` | `:desktop:packageDeb` | Debian/Ubuntu/Mint/Pop!_OS | Linux + JDK 21 |
| `.tar.gz` | `:desktop:packagePortableTarGz` | quem não quer instalar | Linux |
| `.rpm` (opcional) | `:desktop:packageRpm` | Fedora/RHEL | Linux + `rpmbuild` |

O runtime Java vai embutido; não exigir Java no sistema. O alvo `TargetFormat.AppImage` do Compose
1.7 produz uma pasta (`--type app-image`), não o AppImage de arquivo único; por isso o projeto tem a
tarefa `packageLinuxAppImage`, que monta o AppDir e chama o `appimagetool`.

### 11.2 Dependências de sistema (`.deb`)

- `libgtk-3-0`, `libgl1`, `libglx0`, `libegl1`, `libfontconfig1`, `libfreetype6`;
- `libx11-6`, `libxext6`, `libxrender1`, `libxi6`, `libxtst6`, `libxrandr2`, `libxinerama1`,
  `libxcursor1` (X11/XWayland e JNativeHook);
- `libayatana-appindicator3-1` (bandeja em ambientes que precisam);
- `xdg-desktop-portal` e `xdg-desktop-portal-kde` (captura e atalhos no Wayland);
- recomendados: `fonts-noto-cjk`, `fonts-noto-color-emoji`.

No AppImage, declarar no README/manifesto que o host precisa de portal e fontes; o AppImage não instala
dependências de sistema.

### 11.3 Release

Publicar:

- `Yabai_OCR-<versao>-x86_64.AppImage` (principal para Steam Deck);
- `Yabai_OCR_<versao>_amd64.deb`;
- `Yabai_OCR_<versao>_linux.tar.gz`;
- SHA-256 de cada um.

Manter os assets Windows intactos. O corpo da release deve ter uma tabela “qual arquivo para qual
sistema”. Se o update checker passar a escolher asset, a função deve ser pura e testada por
`(os, arch, assets)`, e o Windows continua recebendo `.exe`/`.msi`.

---

## 12. Configuração, dados e segurança

- Dados do app: `~/.yabai_ocr` (settings, dicionários, índices). Não muda.
- Autostart: `~/.config/autostart/yabai-ocr.desktop`.
- Integração: `~/.local/share/applications/yabai-ocr.desktop` e
  `~/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png`.
- Script KWin: `~/.local/share/kwin/scripts/yabai-ocr/`.
- Token legado do ScreenCast: `~/.java/robot/screencast-tokens.properties` (do JDK; não é usado no KDE Wayland).
- Captura do KDE: `ScreenShot2` no caminho direto; quando o fallback `Screenshot` é usado, a permissão
  é gerenciada pelo portal e o app não escreve no banco.
- Nada de root, nada em `/usr`, nada em `/etc`.
- E2E: `YABAI_HOME` e `XDG_CONFIG_HOME` temporários; backup e restauração de
  `~/.yabai_ocr/settings.properties` com SHA-256.
- Não logar token, coordenadas de tela com conteúdo sensível nem o texto reconhecido em log de
  depuração além do que já existe.

---

## 13. Testes e prova de mutação

### 13.1 Suítes que precisam continuar verdes

| suíte | hoje | regra |
| --- | --- | --- |
| `:core:test` | 177 (pulados com as flags opcionais) | não pode cair; nenhum teste Linux novo aqui |
| `:desktop:test` | 330 (1 pulado no host KDE; 3 no sandbox) | continua verde; `StartupManagerTest` e o smoke do watcher continuam pulando fora do ambiente |
| `:app:testDebugUnitTest` | 411 | não pode mudar; nenhuma alteração em `app/` |

### 13.2 Testes novos (JVM puros, sem abrir janela nem portal)

| teste | o que prova |
| --- | --- |
| `DesktopPlatformTest` | SO, sessão (`XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`, `DISPLAY`, vazio), desktop KDE/GNOME, SteamOS |
| `LinuxAutostartTest` | cria/apaga `.desktop` em pasta temporária; `--minimized`; `Icon`; `XDG_CONFIG_HOME`; executável ausente não grava arquivo quebrado |
| `DesktopEntryExecTest` | escape de espaço, `"`, `\`, `$`, backtick no campo `Exec` |
| `LinuxDesktopEntryTest` | `.desktop` do AppImage aponta para `$APPIMAGE`/caminho real; ícone e categoria corretos |
| `TrayMenuTest` | Linux usa `PopupMenu` nativo em qualquer sessão; Windows mantém o menu customizado; `--minimized` sem bandeja abre a janela |
| `LinuxSystemIntegrationTest` | grava `.desktop` + PNG em `$XDG_DATA_HOME`; executável vazio/ausente ou ícone ausente não deixam atalho quebrado |
| `IconResourceTest` | `ic_scan_linux.png` existe e abre; `.ico` do Windows continua |
| `ScreenCaptureBackendTest` | Windows/X11 → Robot; KDE Wayland → `ScreenShot2` (portal como fallback); outros Wayland → screencast; mensagens distintas para cada `CaptureAvailability`; nunca `x11` no Wayland |
| `KWinScreenshotCaptureTest` | conversão raw `RGBX8888`/`RGBA8888` com `stride`, backend KDE escolhido, falha do direto cai no portal e não repete a tentativa |
| `DbusMenuTest` | layout `(ia{sv}av)`, itens/estado e eventos `clicked`/`aboutToShow` |
| `ScreenshotMethodTest` | `dbusScreencast` definido só em Linux Wayland KDE, antes do AWT; GNOME e X11 intactos |
| `ScreencastCaptureTest` | caminho não-KDE: JDK < 21 → `UNSUPPORTED`; sem token → `NEEDS_PERMISSION`; com token → `READY`; `SecurityException` → `DENIED` lembrado; sucesso limpa a falha |
| `PortalScreenshotCaptureTest` | recorte do monitor, escala nativa, offset de monitor negativo, negação, portal ausente e remoção do PNG devolvido |
| `TrayMenuInvokerTest` | invólucro do menu fica focável e recebe foco; perda de foco fecha uma vez; menu fechado ignora evento atrasado |
| `PickerPlacementTest` | Wayland → `WindowPlacement.Fullscreen` + `PlatformDefault`; Windows/X11 → bounds exatos |
| `HoverCapabilityTest` | KDE Wayland → KWin; GNOME/Game Mode → indisponível; X11/Windows → AWT; screencast só no KWin |
| `KwinPointerSourceTest` | sem evento → `null`; posição repetida descartada; ponto novo atualiza a fonte |
| `KWinScriptTest` | script contém `cursorPosChanged`/`callDBus`; metadata Plasma 6; bloco de fallback com `registerShortcut`/`ShortcutPressed`; escape de descrição; instalação idempotente; conversão `Ctrl+Shift+O`/`Meta+Space`/`PgUp` |
| `YabaiDbusServiceTest` | start idempotente, dois sinks exportados, falha fecha o transporte e permite novo start |
| `KWinHoverBridgeTest` | instala, carrega e confirma `isScriptLoaded`; KWin ausente não escreve; falha de load/start descarrega e fecha |
| `KWinIntegrationTest` | D-Bus falha antes de tocar no KWin; falha do KWin desfaz o D-Bus; sucesso sobe e derruba os dois |
| `HoverCoordinateTest` | ponteiro + bounds + escala → retângulo 640×360 correto, preso à tela |
| `XdgTriggerTest` | `Ctrl+Shift+O` → `CTRL+SHIFT+O`; `Super+Space` → `LOGO+space`; ordem dos modificadores estável; sem modificador, tecla desconhecida e string malformada → `null` |
| `HotkeyBindingsTest` | ids estáveis (`capture-fullscreen`…), descrições pt-BR, despacho de cada id para a ação certa e descarte de `preferred_trigger` inválido |
| `HotkeyBackendSelectionTest` | Windows/X11 → JNativeHook; Wayland → portal; falha de start degrada para indisponível; edição re-binda e expõe falha |
| `PortalGlobalShortcutsTest` | sessão criada uma vez, bind com os cinco triggers, `Activated` chega na ação certa, portal ausente/negado não finge bind, edição re-binda numa sessão nova e fecha a antiga (inclusive em falha) |
| `TranslucentBackgroundTest` | sem `PERPIXEL_TRANSLUCENT` → opaco; com → transparente |
| `DesktopSettingsTest` | `YABAI_HOME` sobrescreve; sem a variável, comportamento antigo |
| `UpdateAssetTest` | escolhe AppImage no Linux, `.exe` antes de `.msi` no Windows e `.dmg` no macOS; liberação sem asset do SO volta para a página do release; extensão sem diferenciar maiúsculas |

> Testes que leem `System.getenv()`/`System.getProperty()` recebem `Map`/valor por parâmetro. Nada de
> `setProperty("os.name")` global em teste paralelo.

### 13.3 Mutações obrigatórias

Cada linha precisa ser reintroduzida, ver o teste nomeado falhar e restaurar para verde.

| mutação | teste que deve acusar |
| --- | --- |
| `detectOsFamily` só reconhece `win` | `DesktopPlatformTest` |
| `detectSessionType` ignora `WAYLAND_DISPLAY` | `DesktopPlatformTest` |
| `detectDesktopFamily` não reconhece `KDE` | `DesktopPlatformTest` |
| `LinuxAutostart` grava `Exec` sem aspas | `DesktopEntryExecTest` / `LinuxAutostartTest` |
| `setEnabled(false)` só marca `Hidden=true` | `LinuxAutostartTest` |
| `Main` ignora falha da bandeja com `--minimized` | `TrayMenuTest` |
| Linux continua com `JPopupMenu` + `MouseInfo` | `TrayMenuTest` |
| `AppIconGenerator` continua usando `.ico` no Linux | `IconResourceTest` |
| Wayland chama `x11` ou não define `screenshotMethod` | `ScreenshotMethodTest` / `ScreenCaptureBackendTest` |
| `screenshotMethod` definido sem exigir KDE (GNOME contaminado) | `ScreenshotMethodTest` |
| Wayland continua no backend Robot | `ScreenCaptureBackendTest` |
| KDE volta a usar o ScreenCast do JDK | `ScreenCaptureBackendTest` |
| recorte do portal ignora o offset do monitor | `PortalScreenshotCaptureTest` |
| invólucro do menu deixa de ser focável | `TrayMenuInvokerTest` |
| JDK antigo deixa de ser `UNSUPPORTED` | `ScreencastCaptureTest` |
| token de restore ignorado (sempre `NEEDS_PERMISSION`) | `ScreencastCaptureTest` |
| negação do portal não é lembrada | `ScreencastCaptureTest` |
| captura bem-sucedida não limpa a falha anterior | `ScreencastCaptureTest` |
| picker abre `Floating` no Wayland | `PickerPlacementTest` |
| Wayland trata negação como sucesso/tela preta | `ScreencastCaptureTest` / `ScreenCaptureBackendTest` |
| Wayland ainda inicia o poll do hover | `HoverCapabilityTest` |
| recorte do hover ignora a escala | `HoverCoordinateTest` |
| conversão XDG usa `Ctrl+Shift+O` literal | `XdgTriggerTest` |
| atalho sem modificador ou com tecla desconhecida é enviado ao portal | `XdgTriggerTest` / `HotkeyBindingsTest` |
| Wayland continua no JNativeHook | `HotkeyBackendSelectionTest` |
| falha de start não degrada o status | `HotkeyBackendSelectionTest` |
| falha de update não aparece na UI | `HotkeyBackendSelectionTest` |
| pedido vazio abre sessão no portal | `PortalGlobalShortcutsTest` |
| portal ausente é ignorado e o bind é fingido | `PortalGlobalShortcutsTest` |
| Wayland sem KWin é tratado como KWin | `HoverCapabilityTest` |
| screencast usado também no Windows/X11 | `HoverCapabilityTest` |
| posição repetida do ponteiro não é deduplicada | `KwinPointerSourceTest` |
| atalho sem modificador é registrado no KWin | `KWinScriptTest` |
| script KWin não é reescrito quando o atalho muda | `KWinHoverBridgeTest` |
| edição reutiliza a sessão antiga do portal em vez de criar outra | `PortalGlobalShortcutsTest` |
| rebind recusado deixa a sessão antiga viva | `PortalGlobalShortcutsTest` |
| edição depois de um start falho não tenta subir o portal de novo | `HotkeyBackendSelectionTest` |
| KDE Wayland volta a usar o screencast do JDK | `ScreenCaptureBackendTest` |
| conversão raw troca R e B (ou ignora `stride`) | `KWinScreenshotCaptureTest` |
| falha do `ScreenShot2` não cai no portal | `KWinScreenshotCaptureTest` |
| SNI anuncia um caminho de menu errado | `StatusNotifierTrayTest` |
| DBusMenu serializa o layout vazio | `StatusNotifierDbusSmokeTest` |
| `GetLayout` devolve só o struct, sem `u revision` | `DbusMenuTest` / `StatusNotifierDbusSmokeTest` |
| DBusMenu perde os filhos do nó raiz | `DbusMenuTest` |
| `KWinIntegration` ignora o resultado do bridge | `KWinIntegrationTest` |
| transporte UNIX sem suporte a FD no classpath | `KWinScreenshotCaptureTest` |
| falha do D-Bus não fecha o transporte | `YabaiDbusServiceTest` |
| script é dado como carregado sem `isScriptLoaded` | `KWinHoverBridgeTest` |
| KWin sobe sem o serviço D-Bus | `KWinIntegrationTest` |
| tooltip usa transparente sem suporte | `TranslucentBackgroundTest` |
| inventário Linux escreve atalho sem executável válido ou sem ícone | `LinuxSystemIntegrationTest` |
| update checker manda o Linux para o `.exe` (ou o Windows para o AppImage) | `UpdateAssetTest` |
| script KWin deixa de aplicar `keepAbove`/`skipTaskbar` no tooltip | `KWinScriptTest` |
| `DesktopSettings` ignora `YABAI_HOME` | `DesktopSettingsTest` |
| resolver aceita o primeiro candidato mesmo inexistente | `resolver skips a missing appimage…` |
| build Linux não adiciona `.deb`/`.tar.gz`/AppImage | tarefa de CI que falha se `packageDeb`, `packagePortableTarGz` ou `packageLinuxAppImage` não existir |

### 13.4 E2E no Steam Deck (Desktop Mode)

Executar em **SteamOS 3.8/Plasma 6.4.3** e **3.9/Plasma 6.7.3**, com `echo $XDG_SESSION_TYPE` =
`wayland` e `$XDG_CURRENT_DESKTOP` contendo `KDE`.

Checklist:

1. AppImage em `~/Applications`, `chmod +x`, abre com `--minimized`; bandeja aparece; nenhuma janela
   indesejada.
2. “Integrar ao sistema” cria `.desktop` e ícone; o app aparece no menu e abre pelo launcher.
3. Captura de tela inteira sai correta **sem** diálogo do portal (caminho `ScreenShot2`).
4. Se o `ScreenShot2` for forçado a falhar, o fallback `Screenshot` pode mostrar o `QuickDialog` do
   KDE; a captura ainda sai correta e o app não trava. O token legado do JDK não precisa existir.
5. `Ctrl+Shift+O` (ou a combinação escolhida no portal) captura a tela inteira; clicar num parágrafo
   abre o resultado com o texto certo.
6. `Ctrl+Shift+A` permite arrastar e recortar; overlay fullscreen cobre a tela; recorte 1:1.
7. Hover liga, o ponteiro chega pelo script KWin, o tooltip aparece acima das janelas e fecha ao sair;
   o hover não abre o diálogo do portal.
8. Atalhos aparecem/configuram no diálogo do sistema; mudar a combinação nos Ajustes **sem
   reiniciar** aplica o novo atalho e desativa o antigo.
9. O menu da bandeja abre pelo Plasma e fecha **sempre** ao clicar fora; os itens executam as mesmas
   ações do menu Swing.
10. Fechar com bandeja ativa mantém o app; desligar a bandeja (ou simular) não deixa o app invisível.
11. Autostart cria `~/.config/autostart/yabai-ocr.desktop` com o caminho real do AppImage; desligar
    apaga.
12. Suspender/retomar: a próxima captura funciona; se pedir autorização de novo, não há crash.
13. Escala 100% e 125%/150% com monitor externo: captura e recorte sem borrão/deslocamento.
14. Fonte japonesa renderiza (sem caixas vazias); emoji dos rótulos aparecem.
15. Nenhuma janela preta/travada; nenhum processo `java` residual após fechar; `settings.properties`
    restaurado se o E2E tocou no perfil real.

Evidência: capturas de tela, versão do SteamOS/Plasma, `XDG_SESSION_TYPE`, saída de
`busctl --user list | grep -E 'portal|kwin'`, tempos medidos (primeira captura, captura seguinte,
hover, índice de tradução) e o log do app.

### 13.5 E2E em Linux desktop

- KDE Wayland (mesmo checklist, sem SteamOS).
- GNOME Wayland: app, captura, atalhos; hover indisponível com mensagem.
- X11: checklist de paridade (atalhos JNativeHook, hover `MouseInfo`, bandeja, recorte).
- `.deb` e AppImage: instalar e repetir o básico.

### 13.6 CI

Job Linux com JDK 21 (não 17) para empacotar, mantendo o job Windows intacto:

```yaml
linux:
  runs-on: ubuntu-24.04
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { distribution: temurin, java-version: '21' }
    - run: ./gradlew :core:test :desktop:test --console=plain
    - run: sudo apt-get update && sudo apt-get install -y fakeroot libfuse2
    - run: ./gradlew :desktop:packageDeb :desktop:packagePortableTarGz --console=plain
    # AppImage de arquivo único (opcional; requer appimagetool no PATH):
    # - run: ./gradlew :desktop:packageLinuxAppImage --console=plain
    - uses: actions/upload-artifact@v4
      with: { name: linux-packages, path: desktop/build/distributions/** }
```

Adicionar verificação de que `git diff -- app core` está vazio durante a implementação Linux.

---

## 14. Fases de execução

| fase | entrega | critério de saída |
| --- | --- | --- |
| **P0 — inventário no Deck** | rodar o app atual em Desktop Mode; registrar o que abre/falha; medir captura, bandeja, atalhos | relatório com versões, logs e capturas; nenhum código de produção |
| **P1 — base Linux** | `DesktopPlatform`, `PlatformServices`, autostart XDG, ícone PNG, build AppImage/`.deb`, `YABAI_HOME`, integração `.desktop` | `:desktop:test` verde nos dois SOs; AppImage abre no Deck |
| **P2 — captura Wayland** | `screenshotMethod`, `ScreencastScreenCapture`, estados de permissão, pickers fullscreen | capturas fullscreen/recorte corretas no Deck; no KDE, `ScreenShot2` como caminho principal (D44) e portal como fallback |
| **P3 — atalhos Wayland** | `PortalGlobalShortcuts`, conversão XDG, estado na UI, fallback KWin (P4) | atalhos disparam no Deck; diálogo do portal funciona |
| **P4 — hover Wayland** | `YabaiDbusService`, `KWinHoverBridge`, script, regra de tooltip | hover lê a palavra sob o cursor; tooltip acima e sem foco |
| **P5 — polimento Linux** | menu de bandeja nativo, fallback, textos, integração, update checker | checklist Desktop Mode completo |
| **P6 — regressão e release** | E2E X11/Windows/Android, docs, assets, checksums | Windows intacto, Android 411, core 177; release publicada |

P1–P5 não tocam `:core`/`:app`. Se algum passo exigir mudança neles, parar e reavaliar o contrato.

P5 concluída em 08/11/2026: suíte desktop em **214 testes**, cinco mutações provadas e
`:desktop:createDistributable` verde. Em 09/11/2026 o primeiro teste no Steam Deck fechou a lacuna:
o runtime passou a incluir `jdk.net` (D-Bus/portal/hotkeys), o portal passou a ler `session_handle`
e `Activated` como object path, o menu Wayland virou customizado e o empacotamento ganhou
`packageLinuxAppImage`; a suíte foi para **224 testes**. O segundo E2E mostrou que o AWT/XEmbed não
entrega o clique no Plasma Wayland e que o SASL também precisa de `jdk.security.auth`; a bandeja
virou SNI e o runtime ganhou o módulo, levando a suíte a **234 testes**. Em 10/11/2026 a captura do
KDE passou a usar o portal `Screenshot` e o menu da bandeja passou a receber foco, fechando ao clicar
fora; a suíte fechou em **249 testes**. Em 12/11/2026 a D44 mostrou que o portal `Screenshot` não é
silencioso no KDE, trocou o caminho principal para `KWin.ScreenShot2`, publicou o menu como DBusMenu,
fez o rebind dos atalhos numa sessão nova e recarregou o fallback KWin ao editar; a suíte fechou em
**287 testes**. Em 13/11/2026 a D45 achou a autorização do `ScreenShot2` (entrada `.desktop` com
`X-KDE-DBUS-Restricted-Interfaces`), o formato `ARGB32` e o atalho preso no KGlobalAccel, e a suíte
fechou em **296 testes**. Em 13/11/2026 a D46 trocou o `Desktop.browse` do botão de IA por lançadores do
desktop, tirou o botão «Dicionário» do resultado e deixou escolher o monitor da captura; a suíte
fechou em **316 testes**. Ainda em 13/11/2026 a D47 passou a tentar StatusNotifier em todo Linux (o
GNOME do Ubuntu não desenha ícone XEmbed), mostrou o motivo nos Ajustes quando não há host, corrigiu
a versão do build e a thread da checagem de atualizações, e destravou os botões achatados; a suíte
fechou em **330 testes**. A validação visual de bandeja, hover, captura e atalhos no Desktop Mode
segue como checklist abaixo.

**Bandeja no GNOME (Ubuntu).** O app publica `org.kde.StatusNotifierItem`; o shell do GNOME só
desenha esse ícone com uma extensão AppIndicator (a `gnome-shell-extension-appindicator` do Ubuntu, ou
AppIndicator and KStatusNotifierItem Support no extensions.gnome.org). Sem ela não existe host para
desenhar ícone algum: os Ajustes passam a avisar «Bandeja indisponível…» na seção de inicialização e
o app continua usável pela janela e pelos atalhos globais (no Wayland o `--minimized` abre a janela
quando a bandeja falha).

---

## 15. Critérios de aceite

### 15.1 Steam Deck / KDE Wayland (alvo principal)

- [ ] AppImage abre no Desktop Mode, em Plasma 6.4.3 e 6.7.3.
- [ ] Captura de tela inteira, recorte à mão e hover funcionam pelo `ScreenShot2` sem diálogo; se o caminho direto falhar, o fallback `Screenshot` funciona e informa o motivo.
- [ ] Hover funciona com o ponteiro vindo do KWin; tooltip aparece acima e não rouba foco.
- [ ] Atalhos globais funcionam pelo portal; combinação pode ser alterada e é refletida na UI.
- [ ] Bandeja StatusNotifierItem e menu DBusMenu funcionam e fecham ao clicar fora; ausência de bandeja não deixa o app invisível.
- [ ] Autostart XDG cria/apaga o `.desktop` correto.
- [ ] `.desktop` e ícone ficam em `$HOME`; nada exige root ou escrita em `/usr`.
- [ ] Suspender/retomar e atualização simulada do Plasma não geram crash.
- [ ] Escala 100% e 125%/150% sem borrão/deslocamento.
- [ ] Nenhum processo residual e nenhum arquivo do usuário alterado após o E2E.
- [ ] Abrir o AppImage com o app já na bandeja não cria uma segunda instância: a nova cópia avisa e sai, e hover/atalhos continuam do processo original.
- [ ] No KDE Wayland os cinco atalhos globais vêm do script do KWin (Ajustes → Atalhos diz «Atalhos globais pelo KWin (KGlobalAccel)») e funcionam abrindo o AppImage direto do arquivo, sem o portal pedir app id.
- [ ] O cartão do hover aparece ao lado do ponteiro (não no canto superior esquerdo) e se reposiciona perto das bordas da tela em vez de sair cortado.
- [ ] Com «Captura silenciosa no KDE» ligado e a sessão reiniciada, o hover não abre o diálogo do portal (`KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1` em `~/.config/environment.d/yabai-ocr.conf`).
- [ ] Abrir a Busca Rápida, o Gerenciador de Dicionários ou o Resultado do recorte por atalho/bandeja traz a janela para a frente, em vez de deixá-la só na barra de tarefas.

### 15.2 Linux Wayland genérico

- [ ] KDE: paridade com o Steam Deck.
- [ ] GNOME: app, captura e atalhos; hover indisponível com motivo claro.
- [ ] Nenhuma tela preta, exceção não tratada ou atalho silencioso.

### 15.3 Linux X11

- [ ] Atalhos JNativeHook, hover `MouseInfo`, bandeja e recorte funcionam como no Windows.
- [ ] `.deb`/AppImage instalam e abrem.
- [ ] Captura em 100/125/150/200% sem borrão.

### 15.4 Windows (não-regressão)

- [ ] `:desktop:test` continua verde (330 hoje).
- [ ] `packageExe`, `packageMsi` e `packageInnoSetup` continuam funcionando.
- [ ] Autostart pelo registro cria/remove exatamente a mesma chave.
- [ ] Bandeja, atalhos, captura, hover, recorte e tooltip inalterados.
- [ ] Nenhum `if (isLinux)` altera o caminho Windows.

### 15.5 Android e core (não-regressão)

- [ ] `:app:testDebugUnitTest` continua com 411 testes verdes.
- [ ] `git diff -- app/` vazio.
- [ ] `:core:test` continua com 177 (pulados só com as flags opcionais).
- [ ] Nenhuma dependência Android nova.

---

## 16. Riscos e mitigação

| risco | impacto | mitigação |
| --- | --- | --- |
| JDK 21 não incluído no runtime empacotado | alto | build Linux sempre com JDK 21; testar `createDistributable` no Deck antes da release |
| `jlink` omite `jdk.net`/nativos do `dbus-java` | alto | `NativeRuntimeModulesTest`; testar captura e hover no runtime empacotado |
| portal nega/expira permissão | alto | estados `NEEDS_PERMISSION`/`DENIED`; reautorizar no diálogo/configurações; no KDE não há token do JDK |
| `ScreenShot2` indisponível e fallback do `Screenshot` caro no hover | médio | medir latência no Deck; cache curto só se comprovado; reduzir taxa |
| script KWin não carrega (versão/atualização) | alto | verificar `isScriptLoaded`; reinstalar; fallback de mensagem |
| portal GlobalShortcuts indisponível | médio | fallback KWin; senão, declarar indisponível |
| app id do AppImage não bate com o `.desktop` | médio | instalar `.desktop` com nome estável `yabai-ocr.desktop`; validar no portal |
| `alwaysOnTop` ignorado no Wayland | médio | fullscreen nos pickers; regra KWin no tooltip; não depender de posição |
| multi-monitor/escala fracionária | médio | função pura de mapeamento; E2E 125/150%; documentar monitor principal |
| bandeja ausente (Game Mode/GNOME) | médio | fallback de janela obrigatório |
| fontes CJK/emoji ausentes | baixo | recomendar `fonts-noto-cjk`/emoji; aviso na UI |
| AppImage/FUSE | baixo | documentar `--appimage-extract-and-run`; testar |
| atualização do SteamOS muda Plasma | médio | testar 6.4 e 6.7; não usar API exclusiva; recarregar script/token |
| Flatpak limita KWin/D-Bus | médio | não recomendar Flatpak para hover; documentar permissões |

---

## 17. Decisões pendentes do dono do projeto

1. **Backend de atalhos:** portal primeiro com fallback KWin (recomendado) ou apenas KWin no Steam Deck?
2. **Distribuição:** AppImage como principal e `.deb` como secundário (recomendado)? `.rpm` é
   obrigatório?
3. **Nome do pacote/app id:** `yabai-ocr` (minúsculo) é aceitável? O nome visível continua “Yabai OCR”.
4. **Licença e mantenedor:** confirmar licença para `rpmLicenseType` e e-mail do `debMaintainer`.
5. **Telemetria de estado:** mostrar o cartão “Integração Linux” sempre ou só quando algo está
   indisponível?
6. **Game Mode:** documentar como “melhor esforço” (recomendado) ou tentar suporte a atalhos via
   Steam Input?

---

## 18. Ordem de leitura para quem for implementar

1. `LINUX.md` §2 — entender as APIs que o plano usa (portal `Screenshot`/`ScreenCast`, `GlobalShortcuts`, KWin script).
2. `desktop/build.gradle.kts` — conseguir gerar AppImage e rodar no Deck com JDK 21.
3. `DesktopPlatform.kt` + `PlatformServices.kt` — a base de decisão.
4. `LinuxAutostart.kt` + `LinuxDesktopEntry.kt` + ícone — o menor conjunto visível e testável.
5. `Main.kt` + `SystemTrayManager.kt` — fallback de bandeja (evita app invisível).
6. `ScreenCapture.kt` + pickers fullscreen — captura Wayland.
7. `PortalGlobalShortcuts.kt` — atalhos.
8. `YabaiDbusService.kt` + `KWinHoverBridge.kt` + script — hover.
9. Tooltip/overlays — polimento e regras KWin.
10. E2E Steam Deck + regressão Windows/Android + docs.

Cada passo fecha com: teste novo → mutação → suíte verde → E2E quando visual → README/ROADMAP
atualizados. O `AGENTS.md` continua valendo; este documento não substitui as regras dele.
