# Yabai OCR — Project structure

[Português](ARCHITECTURE.md) | **English**

Reference document for anyone who needs to **find where to change something**. It describes the three
modules, the role of each file, the main flows and the known pitfalls. For the decision history and
the reason behind each fix, see [ROADMAP.md](docs/ROADMAP.md); for using the app, [README.md](README.md).

- **110+ Kotlin files**, ~50 thousand lines, three Gradle modules.
- `:core` — pure Kotlin/JVM (no Android): OCR, protobuf, dictionaries, Japanese, geometry.
- `:desktop` — Compose Desktop for Windows and Linux (Steam Deck).
- `:app` — Android (`minSdk 26`, `compileSdk/targetSdk 36`), overlays built on Views.

---

## 1. One-page view

```
                 ┌──────────────────────┐
   Android ──────┤  :app (Activities,   │
                 │  Services, Overlays) │
                 └──────────┬───────────┘
                            │
   Desktop ──────┐          ▼
   (Compose)     │   ┌──────────────┐
                 └──►│    :core     │  pure Kotlin/JVM
                     │  OCR · dict  │  no Android dependency
                     │  jp · layout │
                     └──────────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
          Google Lens   ONNX Runtime   ML Kit (app)
          (HTTP)        (PP-OCRv6)     (offline)
```

`:core` is the single source of truth for OCR and dictionaries. Desktop and Android only
**orchestrate**: they capture the screen, call the chosen engine, show the result and query the
dictionary.

---

## 2. Build and commands

| task | what it does |
| --- | --- |
| `.\gradlew.bat :desktop:test` | desktop suite (JVM, no window) |
| `.\gradlew.bat :desktop:run --args="--minimized"` | opens the app in the tray |
| `.\gradlew.bat :desktop:compileKotlin` | quick desktop compile |
| `.\gradlew.bat :core:test` | core suite (pure JVM) |
| `.\gradlew.bat :app:testDebugUnitTest` | Android suite with Robolectric (no device) |
| `.\gradlew.bat :app:assembleDebug` | debug APK (`dev.yabai.ocr.debug`) |
| `.\gradlew.bat :desktop:packagePortableTarGz` | self-contained `.tar.gz` (Linux) |
| `.\gradlew.bat :desktop:packageLinuxAppImage` | `.AppImage` (needs `appimagetool`) |

Test flags that turn on real cases (none of them run by default):

| flag | module | enables |
| --- | --- | --- |
| `-Plive=1` | `:core` | talks to the real Google Lens endpoint |
| `-Pdicts="C:\zips"` | `:core` | real Yomitan ZIPs |
| `-PcompiledDicts="C:\compiled"` | `:core` | already compiled `.yabai` (translation index) |
| `-Pppocr="C:\home"` | `:desktop` | PP-OCR models in `home/ppocr_v6` (end-to-end test) |

Current counts: **`:core` 197 tests**, **`:desktop` 546**, **`:app` 836**. Confirm with the suite
before quoting them.

---

## 3. Directory tree

```
.
├── build.gradle.kts            # shared plugins and versions (version catalog in gradle/)
├── settings.gradle.kts         # includes :core, :desktop and :app
├── gradle/libs.versions.toml   # every dependency and version
├── AGENTS.md                   # working rules for the repository
├── .github/                    # project agents and skills (GitHub Copilot format)
│   ├── agents/yabai.md         # the engineer persona: root cause, mutation, E2E
│   └── skills/<name>/SKILL.md  # skills loaded on demand (+ scripts and references)
├── ARCHITECTURE.md             # this document (Portuguese)
├── ARCHITECTURE.en.md          # this document (English)
├── CONTRIBUTING.md / .en.md    # how to contribute
├── LICENSE                     # PolyForm Noncommercial 1.0.0
├── README.md / README.pt-BR.md  # public overview
├── docs/                       # technical docs, LINUX, STEAMOS and ROADMAP
│   ├── LINUX.md / STEAMOS.md   # Linux and Steam Deck support
│   └── ROADMAP.md              # technical history, item by item (B/F/M/D/I/N/R)
├── LANGUAGES.md                # i18n: keys and where each text lives
├── ACCESSIBILITY_SERVICE_GUIDE.md
└── apk-output/                 # APKs renamed by the build (git-ignored)
```

---

## 4. `:core` — pure Kotlin/JVM

`core/src/main/kotlin/dev/yabai/ocr/core/`

### `lens/` — Google Lens (HTTP + protobuf)

| file | role |
| --- | --- |
| `LensClient.kt` | OkHttp client for the Lens endpoint: sends PNG, receives protobuf, keeps the cookie jar |
| `LensModels.kt` | `EncodedImage`, `PixelBox`, `LensSegment`, `LensBlock`, `LensResult`, `LensException` — the format **every** app layer consumes; `printedReadingsOf` finds printed furigana by geometry on the lines of **any** engine (Lens, PP-OCR, ML Kit — R64) |
| `LensProto.kt` | hand-written protobuf reader (varint, fields, lengths) |
| `LensResultBuilder.kt` | builds `LensResult` from the raw response, in reading order |
| `FuriganaMode.kt` | `REMOVE` / `PARENTHESES`: what to do with the printed reading next to the kanji |
| `RubyGeometry.kt` | ruby geometry (reading above the kanji) for the overlay/picker; the `LensBlock` pieces carry the printed readings so the picker does not double them (R57) |

**Golden rule:** the recognized text is the data. Nothing here "fixes" a recognized character.

### `ppocr/` — offline engine (PP-OCRv6_manga)

| file | role |
| --- | --- |
| `PPOcrSpec.kt` | constants and geometry: longest side 960 (or 3:4 budget on strips), 0.15 threshold, 0.25 box, 1.4 unclip, height 48; `PPOcrLine.printed` and `withPrintedReadings` mark the printed furigana (R64) |
| `PPOcrPreprocess.kt` | detector tensor (ImageNet) and line tensor (rotated left, 48 px, −1..1) |
| `PPOcrPostprocess.kt` | DB map → boxes (connected components, confidence, unclip) and right-to-left reading order |
| `PPOcrEngine.kt` | ONNX sessions (det + rec), greedy CTC; `PPOcrOutcome`/`PPOcrBlock`/`PPOcrLine`; **focused** read with `focus` (only the lines near the point are recognised — D94) |
| `PPOcrFocus.kt` | `linesToRecognize`: which detected lines the recogniser has to read when the question is about a point (hover); without a focus every line is read |
| `PPOcrModelStore.kt` | paths and URLs of the three files (`manga_det.onnx`, `manga_rec.onnx`, `ppocrv6_dict.txt`) |
| `PPOcrModelDownloader.kt` | download with `.tmp`, per-file resume and progress |

ONNX is `compileOnly` in the core: consumers (`:desktop`, `:app`) bring the runtime.

| `DonationInfo.kt` | Pix key and Ko-fi link, shared by both apps |

### `md/` — release-notes Markdown

| file | role |
| --- | --- |
| `Markdown.kt` | minimal parser used by both apps: headings, paragraphs, lists, quotes, code, rules and inline emphasis/links |

### `update/` — version comparison

| file | role |
| --- | --- |
| `VersionCompare.kt` | `compareVersions`/`isNewerVersion`: a missing component counts as zero, `v`/`-debug`/`+build` do not change the version |

### `dict/` — dictionaries and lookup

| file | role |
| --- | --- |
| `CompactDictionary.kt` | opens an installed `.yabai`/folder and exposes entries |
| `DictionaryCompiler.kt` | compiles a Yomitan ZIP → `.yabai` (blocks, keys, reading index) |
| `DictionaryData.kt` | serialized structures of the compiled dictionary |
| `DictionaryQuery.kt` | query normalization (`stripReadings` etc.) |
| `Deinflector.kt` | Japanese deinflection (verbs/adjectives) to reach the dictionary form |
| `LookupEngine.kt` | lookup by term/reading, ranking, `TokenMatch` with inflection reasons; follows an old-form redirect inside the same dictionary (R46e) |
| `Furigana.kt` | reading generated by the dictionary, aligned to the recognized text |
| `GlossaryRenderer.kt` | glossary formatting (tags, lists, examples), and the link target when the entry only points at another (R46e) |
| `Romaji.kt` | kana → romaji (search and display) |
| `TranslationIndex.kt` | side index to search inside **definitions** (`search_by_translation`) |

### `jp/`, `layout/`, `proto/`

| file | role |
| --- | --- |
| `jp/JapaneseText.kt` | Japanese text normalization (space only between two CJK), counts, helpers |
| `layout/BlockColumns.kt` | joins side-by-side columns of the same bubble into one block |
| `layout/ReadingOrder.kt` | sorts boxes into manga reading order |
| `layout/KanjiGrid.kt` | stroke/radical grid for the kanji card |
| `proto/Proto.kt` | generic protobuf reader (`ProtoException`), the base of `LensProto` |

### Core tests

`core/src/test/kotlin/dev/yabai/ocr/core/` — one file per area (`dict/`, `jp/`, `layout/`, `lens/`,
`ppocr/`). `LensLiveTest`, `TranslationIndexRealTest` and the engine end-to-end test only run with the
flags above.

---

## 5. `:desktop` — Compose Desktop (Windows/Linux)

`desktop/src/main/kotlin/dev/yabai/ocr/desktop/`

### Entry point, windows and settings

| file | role |
| --- | --- |
| `Main.kt` | entry point: main window with three screens (`MainScreen`: Settings, Help & Tips, Dictionary Manager), tray, shortcuts, opens picker/crop/result; keeps the Settings tab so coming back from the manual lands on it; the no-dictionary hover notice opens the manager on the installed tab (R59) |
| `DesktopSettings.kt` | `settings.properties` in `~/.yabai_ocr`: shortcuts, engine, theme, dictionaries, AI, translation, Anki (buttons on, deck, note type, address, tags) |
| `SettingsWindow.kt` | the Settings screen: `SettingsTopBar` (title, `LanguageMenu`, «❓ Help»), `SettingsTabRow` and five tabs (`SettingsTab`) with each area's cards — General (appearance, renderer, monitor, startup, Linux, KDE), Shortcuts, Reading (engine + PP-OCR download, hover, result, translation, AI), Dictionaries and About; `TabColumn` is the shared scrollable body |
| `ResultWindow.kt` | result panel: editable text, image, buttons (copy, AI, Anki, crop); «＋ Anki» sends the sentence and every dictionary card has its own, with sent/queued notice and «Undo» |
| `AnkiPanel.kt` | the one path the three panels take to Anki: `sendToAnki` (card → queue → client → notice text, with the resolved deck) and `wordNote` (the per-word card, with an empty sentence because the quick dictionary and the hover have none) |
| `DictionaryMatchText.kt` | the «read form → entry · old form» line the hover and the result draw (R46e) |
| `AnkiConnect.kt` | the Anki add-on spoken over local HTTP (`127.0.0.1:8765`): legacy JSON (`version: 6`) and, when that answers 404/405, the `/api/v1` JSON-RPC (`api.describe`, `decks.*`, `noteTypes.*`, `notes.*`, key in `Authorization: Bearer`), remembering the format that answered; version, deck, note type, duplicate, `addNote` and `deleteNotes`; tells a missing add-on, a closed Anki, a refused key and a duplicate apart instead of returning one error — a duplicate only when `canAddNotesWithErrorDetail` says «duplicate», since the same `false` also covers a card the note type refuses; 1 s/5 s timeouts |
| `AnkiSender.kt` | delivers the card and, when it cannot, keeps it: writes to the queue **before** the attempt, removes it only after `addNote` confirmed — or when Anki says it already has the card, which becomes «already in Anki» —, records why it failed and resends what is left; a refusal that is not a duplicate stays queued with the add-on's words |
| `ReleaseNotesWindow.kt` | Markdown preview of the GitHub release notes, opened from Settings → About |
| `LicenseWindow.kt` | license text bundled in the jar, opened from Settings → About |
| `HelpWindow.kt` | the «Help & Tips» manual as a screen of the main window (`HelpContent(settings, onBackRequested)`), opened by the header's «❓ Help»: a bar with the back arrow (`backToPreviousIcon`, «◀»; its accessible name is `btnBackToPrevious`), the button reference (`helpButtons()`, icon on the left, name and what it does) and one entry per action (captures, hover, quick search, dictionaries, result, Anki, renderers, engines and tray), in pt/en; the Anki entry says what to install and what has to be running |
| `UpdateInstaller.kt` | downloads the asset with progress, swaps the running AppImage (`.old` backup), hands the installer to the system shell (file association, `.msi` through `msiexec`) and restarts |
| `InstanceLock.kt` | file lock at `~/.yabai_ocr/instance.lock`: one instance per user, before D-Bus and the tray |
| `KdeCapturePermission.kt` | the «silent capture on KDE» switch: writes `~/.config/environment.d/yabai-ocr.conf` with `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, because KWin only authorizes `ScreenShot2` to a fixed `Exec` under a root-writable directory |
| `UpdatesNoticeWindow.kt` | single updates notice: the app version (a bar with the download percentage, restarts, opens the notes) and the dictionaries with a newer revision, in one window; `UpdateDownloadProgress`/`UpdateInstallerNotice` are shared with the About tab and, on Windows/macOS, the end of the download says to continue in the installer (R60) |
| `DictionaryUpdateMonitor.kt` | periodic dictionary check in the application scope (including `--minimized`), offer per remote revision, the «already announced» key and `dictionaryAnnouncement` (announce, stay quiet, or clear the tray tooltip) |
| `NativeLibraryPath.kt` | points `jnativehook.lib.path` at `~/.cache/yabai-ocr/native` on Linux before `GlobalScreen` is touched (an AppImage is read-only) |
| `QuickDictWindow.kt` | quick dictionary lookup in its own window; «＋ Anki» beside every word (off by default), with the notice at the top of the list |
| `DictionaryManagerWindow.kt` | manager content: search, installed, catalog, credits; the same back bar (arrow «◀») as the manual when opened as a screen (`onBackRequested`); opens on the requested tab (`initialTab`, R59) |
| `DictLookup.kt` | runs one dictionary query and reports failure instead of throwing inside a `LaunchedEffect` |
| `DesktopTheme.kt` | light/dark palettes, `contrastRatio`, theme application |
| `AppIconGenerator.kt` | runtime icon (window, tray) and the hover badge in the bottom-right corner |
| `AppScrollbar.kt` | `ScrollableLazyColumn`/`ScrollableColumn`: the scrollable containers with the scrollbar Compose Desktop does not draw |
| `CaptureNoticeWindow.kt` | floating capture and hover notice, with an optional action beside «OK» (`actionLabel`/`onAction`, R59) |
| `i18n/DesktopStrings.kt` | **every** pt/en desktop string (nothing hardcoded in the screens) |

### Screen capture

| file | role |
| --- | --- |
| `ScreenCapture.kt` | primary/region/monitor capture via `Robot`, native size on HiDPI, monitor list and preference (`PRIMARY`/`MOUSE`/`DISPLAY:n`); `CaptureDiagnostics` times the last capture and names the transport that served it (`lastCaptureRoute`) (D81) |
| `PlatformServices.kt` | picks the capture backend per platform/session |
| `ScreenshotMethod.kt` | capture-method override (X11/Wayland/portal) via environment variable |
| `KWinScreenshotCapture.kt` | primary path on KDE Wayland: `org.kde.KWin.ScreenShot2`, FD + raw QImage, fallback to the portal |
| `PortalScreenshotCapture.kt` | KDE `Screenshot` portal (Wayland) over D-Bus; compatibility when ScreenShot2 is absent |
| `SegmentPickerWindow.kt` | full-screen picker: draws the boxes and returns the chosen segment |
| `PickerBoxes.kt` / `PickerReadings.kt` / `PickerWindowPlacement.kt` | box geometry, picker readings and window placement; ruby is not drawn over printed furigana or another piece of text and shrinks to fit the gap before giving up (R57, R58, R64) |
| `RegionSelectionWindow.kt` | manual crop with handles (`CropHandle`) |

### Hover

| file | role |
| --- | --- |
| `HoverOcrManager.kt` | hover cycle: capture → OCR (chosen engine, focused on the pointer) → dictionary → card; the gate also advances by polling on the compositor bridges (`BRIDGE_PUMP_MILLIS`), turning the hover off gives back what it allocated (D94) and trims the heap 1,5 s later so the process does not stay at the peak (D95) |
| `HoverDictionaryCache.kt` | the hover's open dictionaries (~123 MB of index), reused while the folder is unchanged and released by `release()` when the hover is turned off (D94) |
| `PickerDictionaryLease.kt` | the dictionary engine the picker borrows from `DesktopDictLoader.cachedEngine` and gives back when the window leaves composition (a paragraph, the hand-drawn crop or Escape), instead of staying open until the app exits (D97) |
| `HoverGate.kt` / `HoverDismissGate.kt` | when to fire (pointer stopped) and when to close; the rest is a preference (`hover_rest_millis`, default 250 ms, 150/250/420 presets in Settings) applied to the run in progress (D82); also the retry of the same spot when a capture or OCR read fails, paced and capped (D88) |
| `HoverLookupTracker.kt` | which lookup is still worth drawing as the pointer keeps moving: newest wins, at most two in flight (D83) |
| `LensPayload.kt` | what goes up to Lens: PNG up to 400 KB, JPEG q85 above that — the upload is the bottleneck on the Deck (D83) |
| `HoverGeometry.kt` | crop around the pointer, local coordinates, HiDPI |
| `HoverTextSelector.kt` | picks the line/word under the pointer and builds the hit; joins a word split across lines/columns of the same block (D56) |
| `DictionaryInstallRun.kt` | one catalogue install and what it reports (installed/cancelled/failed/finished); the card reset runs in `NonCancellable` |
| `RendererRecovery.kt` | run-in-progress marker carrying the pid, the renderer ladder after a native Skiko/Direct3D crash (Direct3D → OpenGL → Software) that only counts as a crash when a Skiko `hs_err_pid<pid>.log` exists, `--renderer=…` and the `skiko_renderer` preference the Settings window writes (D79/D80); `softwareSwitchOn`/`rendererAfterSoftwareSwitch` — the Linux card is a single switch (D83) |
| `HoverLookup.kt` | hover lookup limited to the chosen dictionary (`dictionaryScope`, `scopedHoverHit`), the card's dictionary-change decision answered from the kept reading (`cachedHoverAnswer`) and what a miss means (`japaneseWordAt`, `classifyHoverMiss`: a card only when a Japanese word is under the pointer, and that word's box is the anchor) — no more fallback answering from another dictionary (D77, D78) |
| `PickerScanMemory.kt` | the picker scan that survives «Snippets»: handed out once and never from a failure (D76) |
| `TrayGlyphs.kt` | tray menu icons drawn with Java2D, in colour (Swing cannot paint colour emoji) (D76) |
| `HoverTooltipWindow.kt` | floating card (size, position, content); a single window for the whole session, only hidden between words (`visible`), because creating and destroying a `ComposeWindow` per word left native surfaces in the allocator (D98); the position comes in the `WindowState` and is applied again once the window exists, because the compositor ignores a `setLocation` on a window still being mapped; a height change for the same pointer keeps the card's corner and only slides it as far as the work area requires (`HoverGeometry.workAreaAt`), because anchoring it back to the pointer threw the card upwards when the list opened near the taskbar (D102). It never writes the AWT `background`: on Windows the transparency depends on Skiko picking that background per renderer (D80). «＋ Anki» sits beside each word, and the card's height counts every notice by the lines it takes — the missing-dictionary notice and the Anki answer can show together without cutting the last definition (it takes `showAnki` from whoever places it) |
| `WindowActivation.kt` | brings the window the user asked for to the screen (clears `ICONIFIED`, requests activation); never used by hover |
| `WindowPlacement.kt` | a window's saved corner, honoured only while a current monitor contains it (D103); current monitors in AWT coordinates |
| `HoverCapability.kt` | is hover supported in this session? (X11/Wayland/Win; on Wayland it picks between the KWin and the GNOME pointer) |
| `PointerSource.kt` | pointer position source (AWT, KWin script or GNOME extension) |
| `KWinHoverBridge.kt` / `KWinIntegration.kt` / `KWinScript.kt` | KWin script + D-Bus that delivers the pointer on Wayland; unloads a stale script and reapplies shortcuts |
| `GnomeHoverBridge.kt` | installs/enables the `yabai-ocr@yabai.dev` extension and detects whether the Shell has loaded it (own D-Bus name); same `CursorMoved` contract as KWin |
| `KGlobalAccel.kt` | clears the stored KGlobalAccel binding before reloading the KWin script |

### Desktop OCR

| file | role |
| --- | --- |
| `DesktopOcr.kt` | **single entry**: engine order (`AUTO`/`LENS`/`PP_OCR`), fallback and `DesktopOcrAnswer` |
| `DesktopPPOcr.kt` | offline engine: cached sessions, `PPOcrOutcome` → `LensResult` conversion keeping the printed-furigana marks (R64); the image is copied into a pixels buffer reused across reads (D99) |
| `PixelBuffer.kt` | the `IntArray` of pixels `DesktopPPOcr` reuses while the image size is unchanged — a hover crop is the whole monitor height (~8 MB at 1080p) on every read (D99) |
| `DesktopDictLoader.kt` | opens/caches the `LookupEngine` from the dictionary folder; `installedTitles`/`dictionaryTitle` read **`meta.json` only** (a dictionary index is tens of MB and the hover card asks for the name list on every composition); `sizeBytes` sums a `.yabai` (which is a folder) |

### Shortcuts, tray and system

| file | role |
| --- | --- |
| `GlobalHotkeyManager.kt` | registers global shortcuts, live re-registration |
| `HotkeyBackend.kt` / `HotkeyMatcher.kt` / `HotkeyBindings.kt` | backend (JNativeHook/portal/KWin), key matching and action catalog; on KDE Wayland the shortcuts belong to the KWin script (KGlobalAccel), which needs no app id |
| `PortalGlobalShortcuts.kt` | global shortcuts on Wayland outside KDE through the D-Bus portal (the portal requires an app id an AppImage opened from its file does not have) |
| `XdgTrigger.kt` | `gsettings`/XDG shortcuts on Linux |
| `SystemTrayManager.kt` / `StatusNotifierTray.kt` / `DbusMenu.kt` / `TrayMenu.kt` / `TrayMenuPlacement.kt` / `TrayMenuInvoker.kt` | tray: StatusNotifier + DBusMenu on every Linux session (Plasma and GNOME with AppIndicator), AWT on Windows/macOS and as the fallback, missing-tray reason in `lastFailure`, Swing menu fallback and placement; the icon follows hover through `IconPixmap`/`IconName` and `NewIcon` |
| `ExternalBrowser.kt` | opens a URL in the user's browser: desktop launchers (`xdg-open`, `gio open`, `kde-open*`) with `Desktop.browse` as the last resort |
| `YabaiDbusService.kt` | own D-Bus service (hover, shortcuts) |
| `StartupManager.kt` / `Autostart.kt` / `WindowsAutostart.kt` / `LinuxAutostart.kt` / `LinuxDesktopEntry.kt` / `LinuxSystemIntegration.kt` | start with the system, desktop integration and the hover icon variants in the theme |
| `ClipboardMonitor.kt` | watches the clipboard (copy from the card) |
| `DesktopPlatform.kt` | OS/session detection (SteamOS, Wayland, X11) |
| `DesktopAppUpdateChecker.kt` | looks for a new release and picks the asset for the OS |
| `DesktopDictionaryCatalog.kt` / `DesktopDictionaryUpdateChecker.kt` / `DictionaryUpdateMonitor.kt` | dictionary catalog, revision comparison and the self-driven check loop (outside any window) |

### Desktop tests

`desktop/src/test/kotlin/dev/yabai/ocr/desktop/` — roughly one file per production class. The ones
worth knowing:

- `DesktopOcrOrderTest` — engine order and PP-OCR result conversion.
- `DesktopEngineMigrationTest` — old preferences (`MANGA_OCR`) fall back to `AUTO`.
- `DesktopPPOcrEndToEndTest` — **opt-in** (`-Pppocr`), reads a drawn page with the real models.
- `PortalGlobalShortcutsTest`, `StatusNotifierTrayTest`, `DbusMenuTest`, `KWinScreenshotCaptureTest`,
  `KWinScriptTest` — D-Bus/Wayland without a session.
- `TrayHoverStatusIconTest` — pixels and geometry of the hover badge in the tray icon.
- `NativeRuntimeModulesTest` — the packaged runtime declares `jdk.net` and `jdk.security.auth`.
- `KWinScreenshotCaptureTest` — also checks that the only UNIX transport on the classpath supports FDs.
- `DictLookupTest` — a failing dictionary query is reported, not thrown out of the window.

---

## 6. `:app` — Android

`app/src/main/java/dev/yabai/ocr/`

### Screens and entry points

| file | role |
| --- | --- |
| `MainActivity.kt` | settings and permissions in four tabs (**General**, **Reading**, **Dictionaries**, **About**), like the desktop: a fixed `TabLayout` and one page laid out at a time (R56); picks the engine, downloads models, starts the service |
| `StartOcrActivity.kt` | launcher/shortcut to start the capture |
| `LookupActivity.kt` | opens the dictionary lookup from a text |
| `CameraActivity.kt` | the bubble's camera, a screen of its own on CameraX: live preview, a shutter with no confirmation, the picture held behind the spinner while the service reads it, and the file handoff; it leaves once the boxes are on screen; the photo's rotation follows the phone's own orientation, not the display, at the shutter and after the phone is turned; the preview and the frozen photo sit in the phone's frame, and that frame is what it hands the service for the picker's page; it closes itself when the user goes to the home screen or the list of apps, but only once the camera is running, because the permission dialog announces a leave too; it measures the screen from the whole display, the same measurement as the picker's page, and it keeps the shutter and the ✕ on the phone's edges — bottom centre and top-right corner — changing only which side of the window shows each edge as the display turns, instead of turning them with the picture (R48p); when the phone turns the window, it measures the frame again so the camera stays the whole screen, turns «Recognising…» with that frame, and the indicator carries the «Cancel» that tells the service and hands the preview back (R46d, R47a, R48a, R48d, R48f, R48g, R48h, R48i, R48j, R48k, R48l, R48m, R48p) |
| `DictionaryActivity.kt` | dictionary screen (install, update, filter) |
| `HelpActivity.kt` | user manual: the button reference (one row per button from `item_help_button.xml`, with the bubble's icon on the left) and then captures, camera, bubble and gestures, engines, dictionaries, AI and translation |
| `YabaiOcrApplication.kt` | `Application`: theme, language, update scheduler |
| `AppSettings.kt` | typed `SharedPreferences` for the **whole** app |
| `UsageTracker.kt` | how many Lens calls per day (there is no public quota) |

### OCR

| file | role |
| --- | --- |
| `OcrDispatcher.kt` | picks the engine: Lens, ML Kit, PP-OCRv6; `AUTO` walks the priority with fallback |
| `OcrEngine.kt` | contract (`OcrEngine`) and result (`OcrOutcome`, `OcrBlock`, `OcrPiece`) |
| `LensOcrEngine.kt` | Lens over a `Bitmap` (resize, encode, call the core) |
| `MlKitOcrEngine.kt` | offline Japanese ML Kit, grouping lines with `BlockColumns` and marking the printed furigana (`mlKitPieces`, R64) |
| `ImagePreparer.kt` | resizes/upscales crops before OCR |

### Capture, service and overlays

| file | role |
| --- | --- |
| `capture/ScreenCaptureSession.kt` | `MediaProjection`: one screen capture per session |
| `capture/CameraPhoto.kt` | camera photo turned upright from EXIF, read once and deleted; and the reduced copy the camera screen shows without deleting the file (R48a) |
| `capture/DeviceRotation.kt` | the photo's rotation from the phone's orientation: sensor degrees to `Surface.ROTATION_*` (R48f), and the turn the picker's page takes to follow the phone's frame (R48g) |
| `capture/CameraControls.kt` | where the shutter and the ✕ sit in the window so they stay on the phone's edges when the screen turns (R48p) |
| `capture/ScreenMetrics.kt` | the single screen measurement — the whole display, bars included — used by the bubble (R23), the camera screen and the picker's page (R48j) |
| `capture/PhotoWait.kt` | the limit on the camera screen's wait for a photo's reading, and the fallback that hands control back (R48c) |
| `capture/PhotoPage.kt` | photo geometry on the picker's page (uniform fit, boxes, page allocation) and the page's size in the phone's frame (R48g) and that frame applied to the camera screen's views (R48h) (R47a, R48g, R48h) |
| `service/FloatingOcrService.kt` | foreground service: capture, OCR, shows the result; every reading runs in `startReading`, which holds the `Job` «Cancel» cancels (R48m); a screen turn clamps the bubble again (`onConfigurationChanged`, R60) |
| `service/LookupOverlayService.kt` | dictionary lookup over other apps |
| `service/YabaiTileService.kt` | quick settings tile |
| `overlay/OverlayController.kt` | overlay lifecycle: bubble (position, drag and the rotation clamp — R51/R60), menus, picker and panels; HOME/recents close the result, the kanji reader and the picker (R60) |
| `overlay/ResultOverlay.kt` | result panel (text, translation, actions, dictionary; the wait for a reading is the shared thin bar — R48m, R57) |
| `overlay/LoadingBar.kt` | the thin wait bar (spinner, weighted label and red «Cancel» beside it), shared by the panel and the camera (R57) |
| `overlay/TextPickerOverlay.kt` | segment picker over the capture, with the page turned into the phone's frame (R48g), the action bar and the hint following that turn (R48g, R48h), ruby that steps aside, or shrinks into the gap, instead of covering another piece of text (R57, R58), and a red «Cancel» (R60) |
| `overlay/PickerDecision.kt` / `PickerTargets.kt` / `PickerZoom.kt` / `PickerReadingLayout.kt` / `PickerReading.kt` / `PageTurn.kt` | "always show the picker" rule, touch targets, zoom, readings (never over printed furigana or the next piece; shrinking into a narrow gap — R57, R58; ruby sized by the column's width, not by the kana count — R60) and the page's turn into the phone's frame (R48g) |
| `overlay/RegionOverlay.kt` | manual crop |
| `overlay/AppPickerOverlay.kt` | AI app choice |
| `overlay/KanjiOverlay.kt` / `KanjiCardText.kt` | kanji card (strokes, readings, compounds) |
| `overlay/DefinitionStyle.kt` / `ActionGrid.kt` / `SelectionToolbar.kt` / `FlowLayout.kt` / `Widgets.kt` | glossary style, action grid, selection bar and base widgets |

### Dictionaries, AI and translation

| file | role |
| --- | --- |
| `update/AppUpdateChecker.kt` | reads the latest GitHub release, compares it with `BuildConfig.VERSION_NAME` and returns the notes |
| `update/MarkdownView.kt` | draws the notes (blocks from `:core`) in a `TextView` with bold, code and tappable links |
| `update/ApkInstall.kt` | downloads the APK with `DownloadManager` into the app's downloads folder and stops there (no install, no permission); `ApkDownloadProgress`/`progress` read its bytes for the screen's bar (R60) |
| `dict/DictionaryCatalog.kt` | catalog of available dictionaries |
| `dict/DictionaryDownloader.kt` | ZIP download/install |
| `dict/DictionaryRepository.kt` | installed dictionaries, filter and order |
| `dict/DictionarySearch.kt` | lookup (term, reading, definition) |
| `dict/DictionaryUpdates.kt` | periodic check (WorkManager) and notification |
| `dict/KanjiDefault.kt` | which dictionary answers the kanji card |
| `ai/AskAi.kt` | builds the question and opens the chosen AI app |
| `translate/OfflineTranslator.kt` | offline translation with ML Kit |
| `anki/AnkiDroidGateway.kt` | the AnkiDroid provider client (A4): install and permission, deck and `Yabai` note type created when missing, fields matched by name, duplicate by first field before creating a deck/note type, insertion with the cards moved to the deck (the note is removed if the card cannot move), and removal for «Undo»; speaks `content://com.ichi2.anki.flashcards` directly, with no dependency (the `<queries>` and permission live in the manifest) |

### Resources

`app/src/main/res/`: `layout/activity_main.xml` (header, `TabLayout` and the four
`pageGeneral`/`pageReading`/`pageDictionaries`/`pageAbout` pages) and `activity_help.xml`, `values/` (pt-BR),
`values-en/`, `values-night/colors.xml`, `drawable/` (`ic_scan.png` is the bubble's icon;
`ic_stat_scan.xml` is the monochrome variant for the notification and the tile),
`mipmap-anydpi-v26/`.
Every interface string lives in `values/strings.xml` **and** `values-en/strings.xml` — see
[LANGUAGES.md](LANGUAGES.md).

### App tests

Robolectric (`app/src/test/java/dev/yabai/ocr/`), no device. One file per area: `overlay/` is the
largest part (layout, touch, theme, picker), then `dict/` and the root (settings, screens, intents).
`PpOcrDispatchTest` covers the offline engine choice.

---

## 7. Main flows

### Full-screen capture (desktop)

```
shortcut → Main.kt → SegmentPickerWindow.scanScreen
        → DesktopOcr.recognize(image, settings)      (chosen engine; AUTO walks the priority)
        → LensResult → draws boxes → user picks one
        → ResultWindow(segment text) → dictionary → card
```

### KDE Wayland capture (ScreenShot2)

```
PlatformServices.capture (monitor chosen in Settings: primary, DISPLAY:n or under the pointer)
    → KdeWaylandCapture → org.kde.KWin.ScreenShot2 (CaptureArea/CaptureWorkspace)
    → hidden yabai-ocr-screenshot.desktop authorizes the running binary
    → temporary FD + raw QImage RGBX8888/RGBA8888 or ARGB32 (stride × height)
    → BufferedImage; if ScreenShot2 fails, PortalScreenshotCapture (fallback, retried every 10 s)
```

### Plasma tray (SNI + DBusMenu)

```
Plasma → org.kde.StatusNotifierItem (Activate/SecondaryActivate/ContextMenu)
       → com.canonical.dbusmenu GetLayout/AboutToShow/Event
       → SystemTrayManager builds the items (shortcuts, hover, dictionary)
       → Event(clicked) → same action as the Swing menu
```

### Manual crop (desktop)

```
shortcut → RegionSelectionWindow → cropped BufferedImage
        → ResultWindow → DesktopOcr.recognize → text + engine used in the header
```

### Hover (desktop)

```
pointer stopped → HoverGate → HoverOcrManager.capture(crop)
        → DesktopOcr.recognize → HoverTextSelector (word under the pointer)
        → LookupEngine → HoverTooltipWindow
```

### Android

```
MainActivity/service → MediaProjection capture
        → OcrDispatcher.recognize (Lens / ML Kit / PP-OCRv6, AUTO with fallback)
        → ResultOverlay (+ TextPickerOverlay when the full screen has blocks)
        → dictionary and translation in the same panel
```

### Offline models (PP-OCRv6)

```
Settings (desktop/app) → PPOcrModelDownloader → ~23 MB in ppocr_v6/
        → PPOcrEngine lazy (det + rec) → local recognition
```

---

## 8. Where to change things for each request

| request | start with |
| --- | --- |
| wrong recognized text (Lens) | `core/lens/LensResultBuilder.kt`, `LensProto.kt` — **never** normalize the text |
| wrong recognized text (offline) | `core/ppocr/PPOcrPreprocess.kt` (tensor), `PPOcrPostprocess.kt` (boxes/order), `PPOcrEngine.kt` (CTC) |
| engine order/fallback | `desktop/DesktopOcr.kt`, `app/ocr/OcrDispatcher.kt` |
| new OCR engine | contract in `app/ocr/OcrEngine.kt`; on desktop, return `LensResult` |
| desktop capture/picker | `ScreenCapture.kt`, `KWinScreenshotCapture.kt`, `SegmentPickerWindow.kt`, `PickerBoxes.kt` |
| hover | `HoverOcrManager.kt`, `HoverGeometry.kt`, `HoverTextSelector.kt`, `HoverLookup.kt` |
| shortcuts | `HotkeyBindings.kt`, `HotkeyMatcher.kt`, `GlobalHotkeyManager.kt`; the key capture lives in `SettingsWindow.kt` (`ShortcutsTab`) |
| Settings tabs / where a card lives | `SettingsWindow.kt` (`SettingsTab`, `SettingsTabRow`, `TabColumn`) |
| language / manual | `SettingsWindow.kt` (`LanguageMenu`) and `HelpWindow.kt` (`HelpContent`), opened by the window in `Main.kt` |
| tray | `SystemTrayManager.kt`, `StatusNotifierTray.kt`, `DbusMenu.kt`, `TrayMenu*.kt` |
| dictionaries (search/import) | `core/dict/*`, then `app/dict/*` or `desktop/DictionaryManagerWindow.kt` |
| furigana | `core/dict/Furigana.kt`, `core/lens/FuriganaMode.kt`, `overlay/PickerReadingLayout.kt` |
| interface texts | `desktop/i18n/DesktopStrings.kt`; `app/src/main/res/values*/strings.xml` |
| theme | `desktop/DesktopTheme.kt`; `app/src/main/res/values*/themes.xml` |
| app update | `desktop/DesktopAppUpdateChecker.kt`, `UpdateInstaller.kt`, `UpdatesNoticeWindow.kt`; `app/update/AppUpdateChecker.kt`, `ApkInstall.kt` |
| dictionary update | `desktop/DictionaryUpdateMonitor.kt` (`dictionaryAnnouncement`: when to announce, when to stay quiet and when to clear the tray tooltip), `DesktopDictionaryUpdateChecker.kt` (the one check behind both the notice and the «Check for Updates» button), `DesktopDictionaryCatalog.kt` (`catalogEntryForDictionaryFile`), `UpdatesNoticeWindow.kt`, `DictionaryManagerWindow.kt` (`downloadAndInstallDictionary`); `app/dict/DictionaryUpdates.kt` |

---

## 9. Conventions and pitfalls

- **The recognized text is data.** Do not fix, complete or "improve" a recognized character.
- **Every UI string exists in all available languages** (today pt-BR and en) and the user chooses the
  language in Settings; code comments and API names are in English.
- **HiDPI:** use `createMultiResolutionScreenCapture`; draw 1:1 with `FilterQuality.None`; AWT bounds
  go straight into `.dp`.
- **Picker:** no veil; the full screen always stops at the picker when there is at least one block.
- **Shortcuts:** JNativeHook's `VC_*` codes ≠ AWT; `GlobalHotkeyManager` is created with `remember`.
- **`:core` cannot depend on Android.** If the API is `android.*`, it belongs to `:app`.
- **Behavior change** requires a regression test + mutation proof + README/ROADMAP.
- **`settings.properties` belongs to the user**: back it up before an E2E and restore it afterwards.
- **Gradle on Windows with a space in the path**: use the short path (`C:\Users\ERICPC~1\...`).

---

## 10. Documents

| file | for whom |
| --- | --- |
| [README.md](README.md) / [README.pt-BR.md](README.pt-BR.md) | people who want to install and use it |
| **ARCHITECTURE.en.md** | people who will change the code |
| [ROADMAP.md](docs/ROADMAP.md) | technical history: root cause, fix, tests, evidence (Portuguese) |
| [LANGUAGES.md](LANGUAGES.md) | i18n: keys, where they live, what is missing |
| [LINUX.md](docs/LINUX.md) / [STEAMOS.md](docs/STEAMOS.md) | Linux and Steam Deck |
| [AGENTS.md](AGENTS.md) | working rules for the repository (agents and people) |
| [.github/agents/yabai.md](.github/agents/yabai.md) / [.github/skills](.github/skills/) | the engineer persona and the project skills, in the GitHub Copilot format |
| [CONTRIBUTING.en.md](CONTRIBUTING.en.md) | how to build, test and send a change |
| [ACCESSIBILITY_SERVICE_GUIDE.md](ACCESSIBILITY_SERVICE_GUIDE.md) | accessibility service |
