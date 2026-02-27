#!/bin/bash

# Hulunote Electron Build Script
# This script builds the complete Electron application

set -e

echo "=================================="
echo "Hulunote Electron Build"
echo "=================================="

export PATH="/usr/local/opt/openjdk@8/bin:$PATH"
export CPPFLAGS="-I/usr/local/opt/openjdk@8/include"

rm -fr electron/app/ electron/dist/
rm -fr ./resources/public/hulunote/cljs-runtime
rm -fr ./electron/app/hulunote/cljs-runtime

echo "build hulunote cljs"
npx shadow-cljs release hulunote

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ELECTRON_DIR="$PROJECT_DIR/electron"

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo ""
echo "Step 1: Checking prerequisites..."

# Check Node.js
if ! command_exists node; then
    echo -e "${RED}Error: Node.js is not installed${NC}"
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi
echo -e "${GREEN}✓ Node.js: $(node --version)${NC}"

# Check npm
if ! command_exists npm; then
    echo -e "${RED}Error: npm is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ npm: $(npm --version)${NC}"

# Check if ClojureScript is compiled
if [ ! -f "$PROJECT_DIR/resources/public/app/hulunote.js" ]; then
    echo ""
    echo -e "${YELLOW}Warning: ClojureScript not compiled${NC}"
    echo "Attempting to compile with shadow-cljs..."

    if ! command_exists npx; then
        echo -e "${RED}Error: npx is not available${NC}"
        exit 1
    fi

    cd "$PROJECT_DIR"

    # Install dependencies if needed
    if [ ! -d "node_modules" ]; then
        echo "Installing project dependencies..."
        yarn install || npm install
    fi

    # Compile ClojureScript
    echo "Compiling ClojureScript (this may take a minute)..."
    npx shadow-cljs release hulunote

    if [ ! -f "$PROJECT_DIR/resources/public/app/hulunote.js" ]; then
        echo -e "${RED}Error: ClojureScript compilation failed${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✓ ClojureScript compiled${NC}"

echo ""
echo "Step 2: Installing Electron dependencies..."
cd "$ELECTRON_DIR"

if [ ! -d "node_modules" ]; then
    npm install
else
    echo "Dependencies already installed"
fi
echo -e "${GREEN}✓ Electron dependencies installed${NC}"

echo ""
echo "Step 3: Generating application icons..."
if [ ! -f "$ELECTRON_DIR/icons/icon.png" ]; then
    if command_exists convert; then
        ./generate-icons.sh "$PROJECT_DIR/resources/public/img/hulunote.webp" || true
    else
        echo -e "${YELLOW}Warning: ImageMagick not found, using placeholder icon${NC}"
        # Create a simple placeholder icon
        mkdir -p "$ELECTRON_DIR/icons"
        # Copy webp as placeholder if no icon exists
        if [ -f "$PROJECT_DIR/resources/public/img/hulunote.webp" ]; then
            cp "$PROJECT_DIR/resources/public/img/hulunote.webp" "$ELECTRON_DIR/icons/icon.png" 2>/dev/null || true
        fi
    fi
fi
echo -e "${GREEN}✓ Icons ready${NC}"

echo ""
echo "Step 4: Copying application files..."
npm run build
echo -e "${GREEN}✓ Application files copied${NC}"

echo ""
echo "Step 5: Building distribution packages..."
echo ""
echo "Select build target:"
echo "  1) Current platform only (recommended)"
echo "  2) macOS only"
echo "  3) Windows only"
echo "  4) Linux only"
echo "  5) All platforms"
echo "  6) Skip (just test run)"
echo ""
read -p "Enter choice [1-6]: " choice

case $choice in
    1)
        npm run dist
        ;;
    2)
        npm run dist:mac
        ;;
    3)
        npm run dist:win
        ;;
    4)
        npm run dist:linux
        ;;
    5)
        npm run dist:all
        ;;
    6)
        echo "Skipping distribution build"
        echo ""
        echo "To test the app, run:"
        echo "  cd electron && npm start"
        ;;
    *)
        echo -e "${YELLOW}Invalid choice, skipping distribution build${NC}"
        ;;
esac

echo ""
echo "=================================="
echo -e "${GREEN}Build Complete!${NC}"
echo "=================================="
echo ""
echo "Built packages are in: $ELECTRON_DIR/dist/"
echo ""
echo "To test the app:"
echo "  cd electron && npm start"
echo ""
