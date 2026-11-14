#!/bin/sh
# Launcher artwork comes only from the ready-made files in branding/.
#   sh scripts/branding.sh apply   copy them into the app resources, byte for byte
#   sh scripts/branding.sh verify  fail if the app resources differ from branding/
set -eu
cd "$(dirname "$0")/.."
RES=app/src/main/res
MAP="banner_320x180_mdpi:drawable-mdpi/banner
banner_640x360_xhdpi:drawable-xhdpi/banner
banner_960x540_xxhdpi:drawable-xxhdpi/banner
ic_launcher_foreground_432:mipmap-xxxhdpi/ic_launcher_foreground"
status=0
for pair in $MAP; do
  name=${pair%%:*}; target=${pair##*:}
  src=$(ls branding/"$name".* 2>/dev/null | head -n 1 || true)
  if [ -z "$src" ]; then echo "MISSING  branding/$name.*"; status=1; continue; fi
  dst="$RES/$target.${src##*.}"
  if [ "${1:-verify}" = apply ]; then mkdir -p "$(dirname "$dst")"; cp "$src" "$dst"; fi
  if [ ! -f "$dst" ]; then echo "MISSING  $dst"; status=1; continue; fi
  a=$(sha256sum "$src" | cut -d' ' -f1); b=$(sha256sum "$dst" | cut -d' ' -f1)
  if [ "$a" = "$b" ]; then echo "OK       $a  $dst"; else echo "DIFFERS  $dst"; status=1; fi
done
exit $status
