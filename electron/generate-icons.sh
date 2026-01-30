#!/bin/bash

# Icon generation script for Hulunote
# Requirements: 
# - macOS: brew install imagemagick
# - Linux: sudo apt-get install imagemagick
# - Windows: download from https://imagemagick.org/

# Source image (should be at least 1024x1024)
SOURCE_IMAGE="${1:-../resources/public/img/hulunote.webp}"
OUTPUT_DIR="./icons"

mkdir -p "$OUTPUT_DIR"

# Check if source image exists
if [ ! -f "$SOURCE_IMAGE" ]; then
    echo "Source image not found: $SOURCE_IMAGE"
    echo "Please provide a source image as argument or place hulunote.webp in ../resources/public/img/"
    exit 1
fi

echo "Generating icons from: $SOURCE_IMAGE"

# Convert webp to png first if needed
TEMP_PNG="/tmp/hulunote_temp.png"
convert "$SOURCE_IMAGE" "$TEMP_PNG"

# Generate PNG icons at various sizes
for size in 16 32 48 64 128 256 512 1024; do
    convert "$TEMP_PNG" -resize ${size}x${size} "$OUTPUT_DIR/icon_${size}x${size}.png"
    echo "Created icon_${size}x${size}.png"
done

# Copy main icon
cp "$OUTPUT_DIR/icon_512x512.png" "$OUTPUT_DIR/icon.png"

# Generate macOS icns file (requires macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Generating macOS icns file..."
    mkdir -p "$OUTPUT_DIR/icon.iconset"
    
    cp "$OUTPUT_DIR/icon_16x16.png" "$OUTPUT_DIR/icon.iconset/icon_16x16.png"
    cp "$OUTPUT_DIR/icon_32x32.png" "$OUTPUT_DIR/icon.iconset/icon_16x16@2x.png"
    cp "$OUTPUT_DIR/icon_32x32.png" "$OUTPUT_DIR/icon.iconset/icon_32x32.png"
    cp "$OUTPUT_DIR/icon_64x64.png" "$OUTPUT_DIR/icon.iconset/icon_32x32@2x.png"
    cp "$OUTPUT_DIR/icon_128x128.png" "$OUTPUT_DIR/icon.iconset/icon_128x128.png"
    cp "$OUTPUT_DIR/icon_256x256.png" "$OUTPUT_DIR/icon.iconset/icon_128x128@2x.png"
    cp "$OUTPUT_DIR/icon_256x256.png" "$OUTPUT_DIR/icon.iconset/icon_256x256.png"
    cp "$OUTPUT_DIR/icon_512x512.png" "$OUTPUT_DIR/icon.iconset/icon_256x256@2x.png"
    cp "$OUTPUT_DIR/icon_512x512.png" "$OUTPUT_DIR/icon.iconset/icon_512x512.png"
    cp "$OUTPUT_DIR/icon_1024x1024.png" "$OUTPUT_DIR/icon.iconset/icon_512x512@2x.png"
    
    iconutil -c icns "$OUTPUT_DIR/icon.iconset" -o "$OUTPUT_DIR/icon.icns"
    rm -rf "$OUTPUT_DIR/icon.iconset"
    echo "Created icon.icns"
fi

# Generate Windows ico file
echo "Generating Windows ico file..."
convert "$TEMP_PNG" -define icon:auto-resize=256,128,64,48,32,16 "$OUTPUT_DIR/icon.ico"
echo "Created icon.ico"

# Cleanup
rm -f "$TEMP_PNG"

echo ""
echo "Icon generation complete!"
echo "Icons are in: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"
