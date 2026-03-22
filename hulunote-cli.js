#!/usr/bin/env node
"use strict";

/**
 * Hulunote CLI - Command-line interface for Hulunote note operations.
 *
 * Environment variables:
 *   HULUNOTE_API_TOKEN  - API authentication token (required)
 *   HULUNOTE_API_BASE   - API base URL (default: https://www.hulunote.top)
 *
 * Usage:
 *   hulunote <command> [options]
 *
 * Commands:
 *   create-note       Create a new note
 *   get-notes         Get paginated notes from a database
 *   get-all-notes     Get all notes from a database
 *   update-note       Update a note's title/content
 *   create-nav        Create or update a navigation (outline) node
 *   get-navs          Get all navigation nodes for a note
 *   get-all-navs      Get all navigation nodes from a database (paginated)
 */

const https = require("https");
const http = require("http");
const { randomUUID } = require("crypto");

// ==================== Configuration ====================

const API_TOKEN = process.env.HULUNOTE_API_TOKEN || "";
const API_BASE = process.env.HULUNOTE_API_BASE || "https://www.hulunote.top";
const USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

// ==================== HTTP Client ====================

function makeRequest(endpoint, data) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(data);
    const urlObj = new URL(API_BASE + endpoint);
    const protocol = urlObj.protocol === "https:" ? https : http;
    const options = {
      hostname: urlObj.hostname,
      port: urlObj.port,
      path: urlObj.pathname + urlObj.search,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
        "User-Agent": USER_AGENT,
        "X-Functor-Api-Token": API_TOKEN,
        Referer: "https://www.hulunote.top/",
      },
    };

    const req = protocol.request(options, (res) => {
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => {
        const raw = chunks.join("");
        try {
          resolve(JSON.parse(raw));
        } catch (e) {
          reject(new Error(`JSON parse error: ${e.message}\nRaw: ${raw}`));
        }
      });
    });

    req.on("error", (e) => reject(new Error(`HTTP error: ${e.message}`)));
    req.write(body);
    req.end();
  });
}

function getData(result) {
  return result && result.data !== undefined ? result.data : result;
}

// ==================== Commands ====================

async function createNote(args) {
  const dbName = getRequiredArg(args, "--database", "Database name");
  const title = getRequiredArg(args, "--title", "Note title");

  const result = await makeRequest("/hulunote/new-note", {
    "database-name": dbName,
    title: title,
  });
  const data = getData(result);
  output(data);
}

async function getNotes(args) {
  const dbId = getRequiredArg(args, "--database-id", "Database UUID");
  const page = getOptionalArg(args, "--page", 1, Number);
  const pageSize = getOptionalArg(args, "--page-size", 20, Number);

  const result = await makeRequest("/hulunote/get-note-list", {
    "database-id": dbId,
    page: page,
    "page-size": pageSize,
  });
  const data = getData(result);
  output(data);
}

async function getAllNotes(args) {
  const dbId = getRequiredArg(args, "--database-id", "Database UUID");

  const result = await makeRequest("/hulunote/get-all-note-list", {
    "database-id": dbId,
  });
  const data = getData(result);
  output(data);
}

async function updateNote(args) {
  const noteId = getRequiredArg(args, "--note-id", "Note UUID");
  const title = getOptionalArg(args, "--title", null);
  const content = getOptionalArg(args, "--content", null);

  if (!title && !content) {
    die("At least --title or --content is required");
  }

  const payload = { "note-id": noteId };
  if (title) payload.title = title;
  if (content) payload.content = content;

  const result = await makeRequest("/hulunote/update-hulunote-note", payload);
  const data = getData(result);
  output(data);
}

async function createNav(args) {
  const noteId = getRequiredArg(args, "--note-id", "Note UUID");
  const content = getRequiredArg(args, "--content", "Node content");
  const navId = getOptionalArg(args, "--nav-id", null) || randomUUID();
  const parentId = getOptionalArg(args, "--parent-id", null);

  const payload = {
    "note-id": noteId,
    "nav-id": navId,
    content: content,
  };
  if (parentId) payload["parent-id"] = parentId;

  const result = await makeRequest("/hulunote/create-or-update-nav", payload);
  const data = getData(result);
  // Include the nav-id in output so caller knows what was created
  output({ "nav-id": navId, result: data });
}

async function getNavs(args) {
  const noteId = getRequiredArg(args, "--note-id", "Note UUID");

  const result = await makeRequest("/hulunote/get-note-navs", {
    "note-id": noteId,
  });
  const data = getData(result);
  output(data);
}

async function getAllNavs(args) {
  const dbId = getRequiredArg(args, "--database-id", "Database UUID");
  const page = getOptionalArg(args, "--page", 1, Number);
  const pageSize = getOptionalArg(args, "--page-size", 100, Number);

  const result = await makeRequest("/hulunote/get-all-nav-by-page", {
    "database-id": dbId,
    page: page,
    "page-size": pageSize,
  });
  const data = getData(result);
  output(data);
}

// ==================== Argument Parsing ====================

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith("--")) {
      const key = argv[i];
      // Check if next arg exists and is not a flag
      if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
        args[key] = argv[i + 1];
        i++;
      } else {
        args[key] = true;
      }
    }
  }
  return args;
}

function getRequiredArg(args, key, label) {
  if (!args[key] || args[key] === true) {
    die(`Missing required argument: ${key} (${label})`);
  }
  return args[key];
}

function getOptionalArg(args, key, defaultValue, transform) {
  const val = args[key];
  if (val === undefined || val === true) return defaultValue;
  return transform ? transform(val) : val;
}

// ==================== Output ====================

function output(data) {
  console.log(JSON.stringify(data, null, 2));
}

function die(msg) {
  console.error(`Error: ${msg}`);
  process.exit(1);
}

function printUsage() {
  console.log(`Hulunote CLI - Command-line interface for Hulunote

Usage: hulunote <command> [options]

Environment variables:
  HULUNOTE_API_TOKEN    API authentication token (required)
  HULUNOTE_API_BASE     API base URL (default: https://www.hulunote.top)

Commands:
  create-note           Create a new note
    --database <name>     Database name (required)
    --title <title>       Note title (required)

  get-notes             Get paginated notes from a database
    --database-id <uuid>  Database UUID (required)
    --page <n>            Page number (default: 1)
    --page-size <n>       Notes per page (default: 20)

  get-all-notes         Get all notes from a database
    --database-id <uuid>  Database UUID (required)

  update-note           Update a note's title and/or content
    --note-id <uuid>      Note UUID (required)
    --title <title>       New title (optional)
    --content <content>   New content (optional)

  create-nav            Create or update a navigation (outline) node
    --note-id <uuid>      Note UUID (required)
    --content <text>      Node content (required)
    --nav-id <uuid>       Node UUID (auto-generated if omitted)
    --parent-id <uuid>    Parent node UUID (root level if omitted)

  get-navs              Get all navigation nodes for a note
    --note-id <uuid>      Note UUID (required)

  get-all-navs          Get all navigation nodes from a database (paginated)
    --database-id <uuid>  Database UUID (required)
    --page <n>            Page number (default: 1)
    --page-size <n>       Nodes per page (default: 100)

Examples:
  hulunote create-note --database "My Notes" --title "Daily Log"
  hulunote get-navs --note-id 550e8400-e29b-41d4-a716-446655440000
  hulunote create-nav --note-id <id> --content "First item" --parent-id <root-nav-id>
`);
}

// ==================== Main ====================

async function main() {
  const argv = process.argv.slice(2);

  if (argv.length === 0 || argv[0] === "--help" || argv[0] === "-h") {
    printUsage();
    process.exit(0);
  }

  if (!API_TOKEN) {
    die("HULUNOTE_API_TOKEN environment variable is required.\nSet it with: export HULUNOTE_API_TOKEN=your_token");
  }

  const command = argv[0];
  const args = parseArgs(argv.slice(1));

  const commands = {
    "create-note": createNote,
    "get-notes": getNotes,
    "get-all-notes": getAllNotes,
    "update-note": updateNote,
    "create-nav": createNav,
    "get-navs": getNavs,
    "get-all-navs": getAllNavs,
  };

  const handler = commands[command];
  if (!handler) {
    die(`Unknown command: ${command}\nRun 'hulunote --help' for usage.`);
  }

  try {
    await handler(args);
  } catch (err) {
    die(err.message);
  }
}

main();
