'use strict';

// Yabai OCR — GNOME Shell bridge.
//
// GNOME Wayland does not tell an ordinary application where the pointer is: XWayland only sees it
// while it is over an X11 window, and no portal exposes the position. The shell itself may read it
// (`global.get_pointer()`), so this extension polls the pointer inside the compositor and forwards
// it to the app over D-Bus — the same contract the KWin script uses.
//
// It also owns what GNOME cannot give the app otherwise: the global shortcuts. GNOME 46 implements
// no `GlobalShortcuts` portal (`UnknownMethod`) and its Shell exposes no hot-key manager object, so
// the accelerators the user picked in the app are grabbed with `global.display.grab_accelerator`
// (the same call the Shell's own D-Bus grab service makes) and dispatched back as
// `ShortcutPressed(id)`.
//
// The extension owns a bus name so the app can tell "installed but not loaded yet" (GNOME only reads
// extensions at startup, so a fresh install needs one log out) from "ready".

import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import Meta from 'gi://Meta';
import Mtk from 'gi://Mtk';
import Shell from 'gi://Shell';

import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const APP_SERVICE = 'dev.yabai.Ocr';
const HOVER_PATH = '/dev/yabai/Ocr/Hover';
const HOVER_INTERFACE = 'dev.yabai.Ocr.Hover';
const SHORTCUTS_PATH = '/dev/yabai/Ocr/Shortcuts';
const SHORTCUTS_INTERFACE = 'dev.yabai.Ocr.Shortcuts';
const BRIDGE_NAME = 'dev.yabai.GnomePointer';
const INTERVAL_MS = 100;
const SHORTCUT_REFRESH_SECONDS = 5;

export default class YabaiOcrHoverExtension extends Extension {
  enable() {
    this._lastX = -1;
    this._lastY = -1;
    // id -> {accelerator, action}: `action` is the grab the Shell handed back.
    this._shortcuts = new Map();
    // The Shell is the only place the accelerators can be grabbed on GNOME; a silent failure here
    // looks exactly like "the shortcut does nothing", so every step goes to the journal.
    console.log('yabai-ocr: enabling hover bridge and shortcuts');
    this._acceleratorActivatedId = global.display.connect(
      'accelerator-activated',
      (_display, action) => this._onAccelerator(action)
    );
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
    // The app owns the accelerators (the user edits them in Settings), so they are pulled rather
    // than duplicated here. Every few seconds is enough for an edit to take effect.
    this._syncShortcuts();
    this._shortcutTimerId = GLib.timeout_add_seconds(
      GLib.PRIORITY_DEFAULT,
      SHORTCUT_REFRESH_SECONDS,
      () => {
        this._syncShortcuts();
        return GLib.SOURCE_CONTINUE;
      }
    );
  }

  disable() {
    if (this._timerId) {
      GLib.source_remove(this._timerId);
      this._timerId = 0;
    }
    if (this._shortcutTimerId) {
      GLib.source_remove(this._shortcutTimerId);
      this._shortcutTimerId = 0;
    }
    for (const id of Array.from(this._shortcuts.keys())) this._removeShortcut(id);
    this._shortcuts = new Map();
    if (this._acceleratorActivatedId) {
      global.display.disconnect(this._acceleratorActivatedId);
      this._acceleratorActivatedId = 0;
    }
    if (this._busNameId) {
      Gio.bus_unown_name(this._busNameId);
      this._busNameId = 0;
    }
    console.log('yabai-ocr: disabled');
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

  _syncShortcuts() {
    Gio.DBus.session.call(
      APP_SERVICE,
      SHORTCUTS_PATH,
      SHORTCUTS_INTERFACE,
      'ShortcutBindings',
      null,
      new GLib.VariantType('(a{ss})'),
      Gio.DBusCallFlags.NONE,
      -1,
      null,
      (source, result) => {
        let bindings;
        try {
          const [reply] = source.call_finish(result).deep_unpack();
          bindings = reply || {};
        } catch (error) {
          // App closed or not ready: keep what is registered.
          if (!this._shortcutErrorLogged) {
            this._shortcutErrorLogged = true;
            console.error(`yabai-ocr: ShortcutBindings failed: ${error.message}`);
          }
          return;
        }
        this._shortcutErrorLogged = false;
        this._applyShortcuts(bindings);
      }
    );
  }

  _applyShortcuts(bindings) {
    const wanted = new Set(Object.keys(bindings));
    for (const id of Array.from(this._shortcuts.keys())) {
      if (!wanted.has(id)) this._removeShortcut(id);
    }
    for (const [id, accelerator] of Object.entries(bindings)) {
      if (this._shortcuts.get(id)?.accelerator === accelerator) continue;
      this._removeShortcut(id);

      // The shell's `keybindingManager` object is not exported by Main on GNOME Shell 46 (Main
      // exports `wm`, `extensionManager`, ... and nothing for hot keys), so calling `addHotKey` on
      // it threw a TypeError inside the try/catch and nothing was registered. This is the pair the
      // Shell itself uses in shellDBus: grab the accelerator, then allow the binding name Meta
      // assigns to the grab.
      const action = global.display.grab_accelerator(accelerator, Meta.KeyBindingFlags.NONE);
      if (action === Meta.KeyBindingAction.NONE) {
        // The combination is taken by something else; the tray menu still works.
        console.error(`yabai-ocr: could not grab ${id} (${accelerator})`);
        continue;
      }

      try {
        const name = Meta.external_binding_name_for_action(action);
        Main.wm.allowKeybinding(name, Shell.ActionMode.ALL);
        this._shortcuts.set(id, {accelerator, action});
        console.log(`yabai-ocr: grabbed ${id} as ${accelerator} (action ${action})`);
      } catch (error) {
        global.display.ungrab_accelerator(action);
        console.error(`yabai-ocr: could not enable ${id}: ${error.message}`);
      }
    }
  }

  _onAccelerator(action) {
    for (const [id, shortcut] of this._shortcuts) {
      if (shortcut.action === action) {
        this._press(id);
        return;
      }
    }
  }

  _removeShortcut(id) {
    const shortcut = this._shortcuts.get(id);
    if (shortcut) global.display.ungrab_accelerator(shortcut.action);
    this._shortcuts.delete(id);
  }

  _press(id) {
    Gio.DBus.session.call(
      APP_SERVICE,
      SHORTCUTS_PATH,
      SHORTCUTS_INTERFACE,
      'ShortcutPressed',
      new GLib.Variant('(s)', [id]),
      null,
      Gio.DBusCallFlags.NONE,
      -1,
      null,
      () => {}
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
