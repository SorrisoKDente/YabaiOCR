// Yabai OCR — KWin bridge.
// Sends the global pointer position to the app through D-Bus. The rate limit keeps the D-Bus
// traffic small; the app does the OCR and decides when the pointer really stopped.

const SERVICE = "dev.yabai.Ocr";
const HOVER_PATH = "/dev/yabai/Ocr/Hover";
const HOVER_INTERFACE = "dev.yabai.Ocr.Hover";
const MIN_INTERVAL_MS = 100;

let lastSent = 0;

function pushCursor() {
    const now = Date.now();
    if (now - lastSent < MIN_INTERVAL_MS) return;

    const position = workspace.cursorPos;
    if (!position) return;

    lastSent = now;
    callDBus(
        SERVICE,
        HOVER_PATH,
        HOVER_INTERFACE,
        "CursorMoved",
        Math.round(position.x),
        Math.round(position.y)
    );
}

workspace.cursorPosChanged.connect(pushCursor);

// Wayland has no reliable alwaysOnTop from the client side. Mark the hover window so it stays above
// normal windows and out of the taskbar. windowAdded covers tooltips created after the script
// loaded; windowList/clientList covers a tooltip that was already open.
const HOVER_WINDOW_TITLE = "Yabai OCR - Hover";

function markHoverWindow(window) {
    if (!window || window.caption !== HOVER_WINDOW_TITLE) return;
    window.keepAbove = true;
    window.skipTaskbar = true;
}

workspace.windowAdded.connect(markHoverWindow);

const existingWindows = typeof workspace.windowList === "function"
    ? workspace.windowList()
    : (typeof workspace.clientList === "function" ? workspace.clientList() : []);
existingWindows.forEach(markHoverWindow);
