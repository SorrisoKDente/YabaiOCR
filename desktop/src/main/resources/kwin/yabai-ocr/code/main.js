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
