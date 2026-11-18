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
| `LensModels.kt` | `EncodedImage`, `PixelBox`, `LensSegment`, `LensBlock`, `LensResult`, `LensException` — the format **every** app layer consumes |
| `LensProto.kt` | hand-written protobuf reader (varint, fields, lengths) |
| `LensResultBuilder.kt` | builds `LensResult` from the raw response, in reading order |
| `FuriganaMode.kt` | `REMOVE` / `PARENTHESES`: what to do with the printed reading next to the kanji |
| `RubyGeometry.kt` | ruby geometry (reading above the kanji) for the overlay/picker |

**Golden rule:** the recognized text is the data. Nothing here "fixes" a recognized character.

### `ppocr/` — offline engine (PP-OCRv6_manga)

| file | role |
| --- | --- |
| `PPOcrSpec.kt` | constants and geometry: longest side 960 (or 3:4 budget on strips), 0.15 threshold, 0.25 box, 1.4 unclip, height 48 |
| `PPOcrPreprocess.kt` | detector tensor (ImageNet) and line tensor (rotated left, 48 px, −1..1) |
| `PPOcrPostprocess.kt` | DB map → boxes (connected components, confidence, unclip) and right-to-left reading order |
| `PPOcrEngine.kt` | ONNX sessions (det + rec), greedy CTC; `PPOcrOutcome`/`PPOcrBlock`/`PPOcrLine`; **focused** read with `focus`/`focusRadiusPx` (only the lines near the point are recognised — D94) |
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
| `LookupEngine.kt` | lookup by term/reading, ranking, `TokenMatch` with inflection reasons |
| `Furigana.kt` | reading generated by the dictionary, aligned to the recognized text |
| `GlossaryRenderer.kt` | glossary formatting (tags, lists, examples) |
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
| `Main.kt` | entry point: main window, tray, shortcuts, opens picker/crop/result/dictionary |
| `DesktopSettings.kt` | `settings.properties` in `~/.yabai_ocr`: shortcuts, engine, theme, dictionaries, AI, translation |
| `SettingsWindow.kt` | the whole Settings screen (appearance, shortcuts, engine + download, dictionaries, AI) |
| `ResultWindow.kt` | result panel: editable text, image, buttons (copy, AI, crop) |
| `ReleaseNotesWindow.kt` | Markdown preview of the GitHub release notes, opened from Settings → About |
| `LicenseWindow.kt` | license text bundled in the jar, opened from Settings → About |
| `UpdateInstaller.kt` | downloads the asset with progress, swaps the running AppImage (`.old` backup), opens the installer on Windows/macOS and restarts |
| `InstanceLock.kt` | file lock at `~/.yabai_ocr/instance.lock`: one instance per user, before D-Bus and the tray |
| `KdeCapturePermission.kt` | the «silent capture on KDE» switch: writes `~/.config/environment.d/yabai-ocr.conf` with `KWIN_SCREENSHOT_NO_PERMISSION_CHECKS=1`, because KWin only authorizes `ScreenShot2` to a fixed `Exec` under a root-writable directory |
| `UpdatesNoticeWindow.kt` | single updates notice: the app version (downloads with progress, restarts, opens the notes) and the dictionaries with a newer revision, in one window |
| `DictionaryUpdateMonitor.kt` | periodic dictionary check in the application scope (including `--minimized`), offer per remote revision and the «already announced» key |
| `NativeLibraryPath.kt` | points `jnativehook.lib.path` at `~/.cache/yabai-ocr/native` on Linux before `GlobalScreen` is touched (an AppImage is read-only) |
| `QuickDictWindow.kt` | quick dictionary lookup in its own window |
| `DictionaryManagerWindow.kt` | manager content: search, installed, catalog, credits |
| `DictLookup.kt` | runs one dictionary query and reports failure instead of throwing inside a `LaunchedEffect` |
| `DesktopTheme.kt` | light/dark palettes, `contrastRatio`, theme application |
| `AppIconGenerator.kt` | runtime icon (window, tray) and the hover badge in the bottom-right corner |
| `AppScrollbar.kt` | `ScrollableLazyColumn`/`ScrollableColumn`: the scrollable containers with the scrollbar Compose Desktop does not draw |
| `CaptureNoticeWindow.kt` | floating capture notice |
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
| `PickerBoxes.kt` / `PickerReadings.kt` / `PickerWindowPlacement.kt` | box geometry, picker readings and window placement |
| `RegionSelectionWindow.kt` | manual crop with handles (`CropHandle`) |

### Hover

| file | role |
| --- | --- |
| `HoverOcrManager.kt` | hover cycle: capture → OCR (chosen engine, focused on the pointer) → dictionary → card; the gate also advances by polling on the compositor bridges (`BRIDGE_PUMP_MILLIS`), and turning the hover off gives back what it allocated (D94) |
| `HoverDictionaryCache.kt` | the hover's open dictionaries (~123 MB of index), reused while the folder is unchanged and released by `release()` when the hover is turned off (D94) |
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
| `HoverTooltipWindow.kt` | floating card (size, position, content); the position comes in the `WindowState` and is applied again once the window exists, because the compositor ignores a `setLocation` on a window still being mapped. It never writes the AWT `background`: on Windows the transparency depends on Skiko picking that background per renderer (D80) |
| `WindowActivation.kt` | brings the window the user asked for to the screen (clears `ICONIFIED`, requests activation); never used by hover |
| `HoverCapability.kt` | is hover supported in this session? (X11/Wayland/Win; on Wayland it picks between the KWin and the GNOME pointer) |
| `PointerSource.kt` | pointer position source (AWT, KWin script or GNOME extension) |
| `KWinHoverBridge.kt` / `KWinIntegration.kt` / `KWinScript.kt` | KWin script + D-Bus that delivers the pointer on Wayland; unloads a stale script and reapplies shortcuts |
| `GnomeHoverBridge.kt` | installs/enables the `yabai-ocr@yabai.dev` extension and detects whether the Shell has loaded it (own D-Bus name); same `CursorMoved` contract as KWin |
| `KGlobalAccel.kt` | clears the stored KGlobalAccel binding before reloading the KWin script |

### Desktop OCR

| file | role |
| --- | --- |
| `DesktopOcr.kt` | **single entry**: engine order (`AUTO`/`LENS`/`PP_OCR`), fallback and `DesktopOcrAnswer` |
| `DesktopPPOcr.kt` | offline engine: cached sessions, `PPOcrOutcome` → `LensResult` conversion |
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
| `MainActivity.kt` | settings and permissions; picks the engine, downloads models, starts the service |
| `StartOcrActivity.kt` | launcher/shortcut to start the capture |
| `LookupActivity.kt` | opens the dictionary lookup from a text |
| `DictionaryActivity.kt` | dictionary screen (install, update, filter) |
| `HelpActivity.kt` | help and explanations |
| `YabaiOcrApplication.kt` | `Application`: theme, language, update scheduler |
| `AppSettings.kt` | typed `SharedPreferences` for the **whole** app |
| `UsageTracker.kt` | how many Lens calls per day (there is no public quota) |

### OCR

| file | role |
| --- | --- |
| `OcrDispatcher.kt` | picks the engine: Lens, ML Kit, PP-OCRv6; `AUTO` walks the priority with fallback |
| `OcrEngine.kt` | contract (`OcrEngine`) and result (`OcrOutcome`, `OcrBlock`, `OcrPiece`) |
| `LensOcrEngine.kt` | Lens over a `Bitmap` (resize, encode, call the core) |
| `MlKitOcrEngine.kt` | offline Japanese ML Kit, grouping lines with `BlockColumns` |
| `ImagePreparer.kt` | resizes/upscales crops before OCR |

### Capture, service and overlays

| file | role |
| --- | --- |
| `capture/ScreenCaptureSession.kt` | `MediaProjection`: one screen capture per session |
| `service/FloatingOcrService.kt` | foreground service: capture, OCR, shows the result |
| `service/LookupOverlayService.kt` | dictionary lookup over other apps |
| `service/YabaiTileService.kt` | quick settings tile |
| `overlay/OverlayController.kt` | overlay lifecycle |
| `overlay/ResultOverlay.kt` | result panel (text, translation, actions, dictionary) |
| `overlay/TextPickerOverlay.kt` | segment picker over the capture |
| `overlay/PickerDecision.kt` / `PickerTargets.kt` / `PickerZoom.kt` / `PickerReadingLayout.kt` / `PickerReading.kt` | "always show the picker" rule, touch targets, zoom and readings |
| `overlay/RegionOverlay.kt` | manual crop |
| `overlay/AppPickerOverlay.kt` | AI app choice |
| `overlay/KanjiOverlay.kt` / `KanjiCardText.kt` | kanji card (strokes, readings, compounds) |
| `overlay/DefinitionStyle.kt` / `ActionGrid.kt` / `SelectionToolbar.kt` / `FlowLayout.kt` / `Widgets.kt` | glossary style, action grid, selection bar and base widgets |

### Dictionaries, AI and translation

| file | role |
| --- | --- |
| `update/AppUpdateChecker.kt` | reads the latest GitHub release, compares it with `BuildConfig.VERSION_NAME` and returns the notes |
| `update/MarkdownView.kt` | draws the notes (blocks from `:core`) in a `TextView` with bold, code and tappable links |
| `update/ApkInstall.kt` | downloads the APK with `DownloadManager` into the app's downloads folder and stops there (no install, no permission) |
| `dict/DictionaryCatalog.kt` | catalog of available dictionaries |
| `dict/DictionaryDownloader.kt` | ZIP download/install |
| `dict/DictionaryRepository.kt` | installed dictionaries, filter and order |
| `dict/DictionarySearch.kt` | lookup (term, reading, definition) |
| `dict/DictionaryUpdates.kt` | periodic check (WorkManager) and notification |
| `dict/KanjiDefault.kt` | which dictionary answers the kanji card |
| `ai/AskAi.kt` | builds the question and opens the chosen AI app |
| `translate/OfflineTranslator.kt` | offline translation with ML Kit |

### Resources

`app/src/main/res/`: `layout/activity_main.xml` and `activity_help.xml`, `values/` (pt-BR),
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
| shortcuts | `HotkeyBindings.kt`, `HotkeyMatcher.kt`, `GlobalHotkeyManager.kt` |
| tray | `SystemTrayManager.kt`, `StatusNotifierTray.kt`, `DbusMenu.kt`, `TrayMenu*.kt` |
| dictionaries (search/import) | `core/dict/*`, then `app/dict/*` or `desktop/DictionaryManagerWindow.kt` |
| furigana | `core/dict/Furigana.kt`, `core/lens/FuriganaMode.kt`, `overlay/PickerReadingLayout.kt` |
| interface texts | `desktop/i18n/DesktopStrings.kt`; `app/src/main/res/values*/strings.xml` |
| theme | `desktop/DesktopTheme.kt`; `app/src/main/res/values*/themes.xml` |
| app update | `desktop/DesktopAppUpdateChecker.kt`, `UpdateInstaller.kt`, `UpdatesNoticeWindow.kt`; `app/update/AppUpdateChecker.kt`, `ApkInstall.kt` |
| dictionary update | `desktop/DictionaryUpdateMonitor.kt`, `DesktopDictionaryUpdateChecker.kt` (the one check behind both the notice and the «Check for Updates» button), `DesktopDictionaryCatalog.kt` (`catalogEntryForDictionaryFile`), `UpdatesNoticeWindow.kt`, `DictionaryManagerWindow.kt` (`downloadAndInstallDictionary`); `app/dict/DictionaryUpdates.kt` |

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
| [CONTRIBUTING.en.md](CONTRIBUTING.en.md) | how to build, test and send a change |
| [ACCESSIBILITY_SERVICE_GUIDE.md](ACCESSIBILITY_SERVICE_GUIDE.md) | accessibility service |
