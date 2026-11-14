'use strict';

// Yabai OCR — GNOME Shell bridge.
//
// GNOME Wayland does not tell an ordinary application where the pointer is: XWayland only sees it
// while it is over an X11 window, and no portal exposes the position. The shell itself may read it
// (`global.get_pointer()`), so this extension polls the pointer inside the compositor and forwards
// it to the app over D-Bus — the same contract the KWin script uses.
//
// The extension also owns a bus name so the app can tell "installed but not loaded yet" (GNOME only
// reads extensions at startup, so a fresh install needs one log out) from "ready".

import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import Mtk from 'gi://Mtk';

import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const APP_SERVICE = 'dev.yabai.Ocr';
const HOVER_PATH = '/dev/yabai/Ocr/Hover';
const HOVER_INTERFACE = 'dev.yabai.Ocr.Hover';
const BRIDGE_NAME = 'dev.yabai.GnomePointer';
const INTERVAL_MS = 100;

export default class YabaiOcrHoverExtension extends Extension {
  enable() {
    this._lastX = -1;
    this._lastY = -1;
    this._busNameId = Gio.bus_own_name(
      Gio.BusType.SESSION,
      BRIDGE_NAME,
      Gio.BusNameOwnerFlags.NONE,
      null,
      null,
      null
    );
    this._timerId = GLib.timeout_add(GLib.PRIORITY_DEFAULT, INTERVAL_MS, () => {
      this._push();
      return GLib.SOURCE_CONTINUE;
    });
  }

  disable() {
    if (this._timerId) {
      GLib.source_remove(this._timerId);
      this._timerId = 0;
    }
    if (this._busNameId) {
      Gio.bus_unown_name(this._busNameId);
      this._busNameId = 0;
    }
  }

  _push() {
    const [rawX, rawY] = global.get_pointer();
    const scale = this._pointerScale(rawX, rawY);
    const x = Math.round(rawX / scale);
    const y = Math.round(rawY / scale);

    // A resting pointer must not flood the bus; the app rate-limits too, this is the cheap filter.
    if (x === this._lastX && y === this._lastY) return;
    this._lastX = x;
    this._lastY = y;

    Gio.DBus.session.call(
      APP_SERVICE,
      HOVER_PATH,
      HOVER_INTERFACE,
      'CursorMoved',
      new GLib.Variant('(ii)', [x, y]),
      null,
      Gio.DBusCallFlags.NONE,
      -1,
      null,
      (source, result) => {
        // The app may be closed; an unanswered call is not an error worth a journal entry.
        try {
          source.call_finish(result);
        } catch (_error) {
          // ignored on purpose
        }
      }
    );
  }

  // `global.get_pointer()` is in stage pixels. With fractional scaling disabled the coordinates are
  // already logical, which is what AWT reports; only the 100%/200% case needs the divisor.
  _pointerScale(x, y) {
    try {
      const rect = new Mtk.Rectangle({x, y, width: 1, height: 1});
      const monitor = global.display.get_monitor_index_for_rect(rect);
      const monitorScale = global.display.get_monitor_scale(monitor);
      if (!(monitorScale > 1)) return 1;

      const monitorRect = global.display.get_monitor_geometry(monitor);
      for (const view of global.stage.peek_stage_views()) {
        if (view.layout.x === monitorRect.x && view.layout.y === monitorRect.y) {
          return view.get_scale() === 1 ? monitorScale : 1;
        }
      }
    } catch (_error) {
      // Unknown geometry: the raw coordinates are still better than no pointer at all.
    }
    return 1;
  }
}
