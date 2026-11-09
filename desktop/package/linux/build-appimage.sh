#!/usr/bin/env bash
# Builds a real single-file AppImage from the Compose distributable.
#
# Compose 1.7 maps TargetFormat.AppImage to `jpackage --type app-image`, which produces a
# directory (the same one `createDistributable` writes), not the AppImage format. This script
# turns that directory into an AppDir and calls appimagetool.
#
# Requirements:
#   - ./gradlew :desktop:createDistributable already ran (this script does not build the app)
#   - appimagetool on PATH, or APPIMAGETOOL=/path/to/appimagetool
#
# Usage:
#   ./gradlew :desktop:packageLinuxAppImage
#   # or:
#   bash desktop/package/linux/build-appimage.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
DIST="$ROOT/desktop/build/distributions/main/app/yabai-ocr"
OUT="$ROOT/desktop/build/distributions/appimage"
APPDIR="$OUT/YabaiOCR.AppDir"
VERSION="${YABAI_VERSION:-1.0.0}"

TOOL="${APPIMAGETOOL:-}"
if [[ -z "$TOOL" ]]; then
    if command -v appimagetool >/dev/null 2>&1; then
        TOOL="appimagetool"
    else
        cat >&2 <<'EOF'
appimagetool não encontrado.

Instale o appimagetool (por exemplo, o AppImage oficial de
https://github.com/AppImage/appimagetool/releases), deixe-o executável e rode:

    export APPIMAGETOOL=/caminho/para/appimagetool
    ./gradlew :desktop:packageLinuxAppImage

Alternativa sem appimagetool: ./gradlew :desktop:packagePortableTarGz
EOF
        exit 1
    fi
fi

if [[ ! -x "$DIST/bin/yabai-ocr" ]]; then
    echo "Distribuição não encontrada em $DIST." >&2
    echo "Rode antes: ./gradlew :desktop:createDistributable" >&2
    exit 1
fi

rm -rf "$APPDIR"
mkdir -p "$APPDIR/opt"
cp -a "$DIST" "$APPDIR/opt/yabai-ocr"
cp "$ROOT/desktop/src/main/resources/ic_scan_linux.png" "$APPDIR/yabai-ocr.png"

cat > "$APPDIR/yabai-ocr.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Yabai OCR
Comment=Leitor de japonês com OCR
Exec=yabai-ocr
Icon=yabai-ocr
Terminal=false
Categories=Utility;
EOF

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/opt/yabai-ocr/bin/yabai-ocr" "$@"
EOF
chmod +x "$APPDIR/AppRun"

mkdir -p "$OUT"
ARCH=x86_64 "$TOOL" --no-appstream "$APPDIR" "$OUT/Yabai_OCR-${VERSION}-x86_64.AppImage"
echo "AppImage gerado em: $OUT/Yabai_OCR-${VERSION}-x86_64.AppImage"
