---
name: hulunote-notes
description: Write and manage outline notes in Hulunote using the hulunote CLI
user_invocable: true
---

# Hulunote Outline Notes Skill

Use the `hulunote-cli.js` CLI tool (in the project root) to create and manage outline notes in Hulunote. All commands output JSON.

## Prerequisites

The environment variable `HULUNOTE_API_TOKEN` must be set. If it's not set, ask the user to provide their API token.

## Core Concepts

- **Database**: A workspace that contains notes. Identified by name (for creation) or UUID.
- **Note**: A document within a database. Has a title, UUID, and a `root-nav-id` (the root of its outline tree).
- **Navigation Node (Nav)**: An outline item within a note. Each nav has a UUID, content (text), and optional parent-id to form a tree hierarchy.

## Workflow: Writing an Outline Note

### Step 1: Create or find the note

```bash
# Create a new note
node hulunote-cli.js create-note --database "DatabaseName" --title "My Note Title"
```

The response contains the note's `id` and `root-nav-id`. Save both — you need them to add outline content.

If the note already exists, find it:

```bash
node hulunote-cli.js get-all-notes --database-id <database-uuid>
```

### Step 2: Read existing outline (if any)

```bash
node hulunote-cli.js get-navs --note-id <note-uuid>
```

This returns all nav nodes with their IDs, content, and parent-id relationships. Use this to understand the existing structure before adding to it.

### Step 3: Add outline nodes

Each outline item is a nav node. Use `--parent-id` to create hierarchy:

```bash
# Add a top-level item (child of root)
node hulunote-cli.js create-nav --note-id <note-id> --content "Chapter 1: Introduction" --parent-id <root-nav-id>

# The command returns the generated nav-id. Use it as parent for sub-items:
node hulunote-cli.js create-nav --note-id <note-id> --content "1.1 Background" --parent-id <chapter1-nav-id>

# Add another sub-item
node hulunote-cli.js create-nav --note-id <note-id> --content "1.2 Motivation" --parent-id <chapter1-nav-id>
```

### Step 4: Update existing nodes

To update content of an existing nav node, specify its `--nav-id`:

```bash
node hulunote-cli.js create-nav --note-id <note-id> --nav-id <existing-nav-id> --content "Updated content"
```

### Step 5: Update note title

```bash
node hulunote-cli.js update-note --note-id <note-id> --title "New Title"
```

## Best Practices for Outline Writing

1. **Always get the root-nav-id first** — When creating a new note, the response includes `root-nav-id`. All top-level outline items should use this as their `--parent-id`.

2. **Build top-down** — Create parent nodes first, then children. Save each returned `nav-id` to use as `--parent-id` for sub-items.

3. **Use meaningful content** — Each nav node's content is a single outline entry. Keep it concise but informative.

4. **Check before writing** — Run `get-navs` first to see what already exists. Avoid creating duplicate nodes.

5. **Batch efficiently** — When writing a multi-level outline, plan the full structure first, then create nodes in order (parents before children).

## Example: Create a Complete Outline

```bash
# 1. Create the note
node hulunote-cli.js create-note --database "Work" --title "Project Plan Q2"
# Response: { "id": "note-uuid", "root-nav-id": "root-uuid", ... }

# 2. Add top-level sections (using root-nav-id as parent)
node hulunote-cli.js create-nav --note-id note-uuid --content "Goals" --parent-id root-uuid
# Response: { "nav-id": "goals-uuid", ... }

node hulunote-cli.js create-nav --note-id note-uuid --content "Timeline" --parent-id root-uuid
# Response: { "nav-id": "timeline-uuid", ... }

node hulunote-cli.js create-nav --note-id note-uuid --content "Resources" --parent-id root-uuid
# Response: { "nav-id": "resources-uuid", ... }

# 3. Add sub-items under Goals
node hulunote-cli.js create-nav --note-id note-uuid --content "Ship v2.0 by June" --parent-id goals-uuid
node hulunote-cli.js create-nav --note-id note-uuid --content "Reduce latency by 30%" --parent-id goals-uuid

# 4. Add sub-items under Timeline
node hulunote-cli.js create-nav --note-id note-uuid --content "April: Design phase" --parent-id timeline-uuid
node hulunote-cli.js create-nav --note-id note-uuid --content "May: Implementation" --parent-id timeline-uuid
node hulunote-cli.js create-nav --note-id note-uuid --content "June: Testing & release" --parent-id timeline-uuid
```

## Command Reference

| Command | Required Args | Optional Args | Description |
|---------|--------------|---------------|-------------|
| `create-note` | `--database`, `--title` | | Create a new note |
| `get-notes` | `--database-id` | `--page`, `--page-size` | List notes (paginated) |
| `get-all-notes` | `--database-id` | | List all notes |
| `update-note` | `--note-id` | `--title`, `--content` | Update note metadata |
| `create-nav` | `--note-id`, `--content` | `--nav-id`, `--parent-id` | Create/update outline node |
| `get-navs` | `--note-id` | | Get note's outline tree |
| `get-all-navs` | `--database-id` | `--page`, `--page-size` | List all nav nodes |
