# Hulunote

Hulunote is an open-source outliner note-taking application with bidirectional linking.

Inspired by Roam Research, designed for networked thought.

![](./demo.png)

## Features

- **Outliner Structure** — Organize thoughts in hierarchical bullet points with infinite nesting
- **Bidirectional Links** — Connect ideas with `[[wiki-style links]]` and backlinks
- **Daily Notes** — Journaling with automatic date-based pages
- **Multiple Databases** — Separate workspaces for different projects
- **MCP Client** — Experimental MCP client integration

## Screenshots

TUI:
![](./demo-tui.png)

MCP client:
![](./demo1-mcp-chat.png)
![](./demo1-mcp-setting.png)

## Repositories

- Frontend (this repo): https://github.com/hulunote/hulunote
- Backend: https://github.com/hulunote/hulunote-rust
- TUI: https://github.com/hulunote/hulunote-tui

## Quick Start (Frontend Dev Only)

Start the frontend development server:

```bash
npx shadow-cljs watch hulunote
```

Then open the app in your browser:

```bash
open http://localhost:6689
```

**Test Account (for local/dev use):** `chanshunli@gmail.com` / `123456`

## Configuration

- **Backend API Base URL (dev):** configured via `:closure-defines` in `shadow-cljs.edn` (`hulunote.http/API_BASE_URL`).

## Clients (Browser / Electron)

### Browser (dev)

Run:

```bash
npx shadow-cljs watch hulunote
```

### Electron (dev)

Run:

```bash
cd electron
npm run start:dev
```

### Electron (build)

Run:

```bash
./clean_build_electron.sh
```

## Contributing

See `CONTRIBUTING.md`.

## License

MIT
