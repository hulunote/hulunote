# Hulunote Electron App

This directory contains the Electron wrapper for the Hulunote application.

## Prerequisites

- Node.js >= 18
- npm or yarn
- ImageMagick (for icon generation)

## Quick Start

### 1. Build the ClojureScript frontend first

```bash
# From the project root
cd ..
yarn install
shadow-cljs release hulunote
```

### 2. Install Electron dependencies

```bash
cd electron
npm install
```

### 3. Generate application icons

```bash
# macOS/Linux
./generate-icons.sh

# Or manually create icons in the icons/ directory:
# - icon.png (512x512)
# - icon.icns (macOS)
# - icon.ico (Windows)
```

### 4. Build the app

```bash
npm run build
```

### 5. Run in development mode

```bash
npm run start:dev
```

### 6. Build distributable packages

```bash
# Build for current platform
npm run dist

# Build for macOS only
npm run dist:mac

# Build for Windows only
npm run dist:win

# Build for Linux only
npm run dist:linux

# Build for all platforms
npm run dist:all
```

## Directory Structure

```
electron/
├── main.js           # Main process
├── preload.js        # Preload script for IPC
├── build.js          # Build script to copy files
├── package.json      # Electron dependencies and config
├── generate-icons.sh # Icon generation script
├── icons/            # Application icons
│   ├── icon.png
│   ├── icon.icns     # macOS
│   └── icon.ico      # Windows
├── app/              # Built app files (generated)
│   ├── css/
│   ├── html/
│   ├── hulunote/
│   └── img/
└── dist/             # Distribution packages (generated)
```

## Configuration

### Backend Server

The backend server URL is configured in `main.js`:

- Development: `http://127.0.0.1:6689`
- Production: `http://104.244.95.160:6689`

To change the production backend URL, edit the `BACKEND_URL` constant in `main.js`.

### Build Configuration

Edit `package.json` to customize:

- `build.appId` - Application ID
- `build.productName` - Application name
- `build.mac` - macOS specific options
- `build.win` - Windows specific options
- `build.linux` - Linux specific options

## Troubleshooting

### Icons not showing

Make sure to run `./generate-icons.sh` or manually create the icon files.

### Build fails on Windows

Make sure you have the Windows build tools installed:

```bash
npm install --global windows-build-tools
```

### Code signing (macOS)

For distribution outside the Mac App Store, you may need to sign the app:

1. Get an Apple Developer certificate
2. Add to `package.json`:

```json
"mac": {
  "identity": "Developer ID Application: Your Name (XXXXXXXXXX)"
}
```

## License

MIT
