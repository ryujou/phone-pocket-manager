#!/bin/zsh
set -e
cd "$(dirname "$0")"
APP="../../Phone Pocket Manager.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
swiftc Launcher.swift -o "$APP/Contents/MacOS/PhoneManager"
mkdir -p /tmp/phone-pocket-manager.iconset
for size in 16 32 128 256 512; do
 sips -z "$size" "$size" AppIcon.png --out "/tmp/phone-pocket-manager.iconset/icon_${size}x${size}.png" >/dev/null
 sips -z "$((size*2))" "$((size*2))" AppIcon.png --out "/tmp/phone-pocket-manager.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns /tmp/phone-pocket-manager.iconset -o "$APP/Contents/Resources/AppIcon.icns"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>PhoneManager</string>
<key>CFBundleIdentifier</key><string>org.phonepocketmanager.desktop</string>
<key>CFBundleName</key><string>Phone Pocket Manager</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleIconFile</key><string>AppIcon</string>
<key>CFBundleVersion</key><string>1</string>
<key>LSMinimumSystemVersion</key><string>12.0</string>
</dict></plist>
PLIST
codesign --force --sign - "$APP"
echo "Built: $APP. Keep the app beside the desktop folder."
