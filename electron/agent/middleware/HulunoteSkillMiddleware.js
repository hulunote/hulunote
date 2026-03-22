const Middleware = require('./Middleware');

/**
 * Injects Hulunote outline-writing skill knowledge into the system prompt.
 *
 * This teaches the AI chat agent how to use the connected Hulunote MCP tools
 * (create_note, create_or_update_nav, get_note_navigation, etc.) to build
 * structured outline notes.
 */
class HulunoteSkillMiddleware extends Middleware {
  async modifySystemPrompt(systemPrompt, state) {
    return systemPrompt + '\n\n' + HULUNOTE_SKILL_PROMPT;
  }
}

const HULUNOTE_SKILL_PROMPT = `
# Hulunote Outline Notes Skill

You have access to Hulunote tools for creating and managing outline notes. Use them to help users build structured, hierarchical outlines.

## Core Concepts

- **Database**: A workspace containing notes. Identified by name (for creation) or UUID.
- **Note**: A document with a title, UUID, and a \`root-nav-id\` (the root of its outline tree).
- **Navigation Node (Nav)**: An outline item. Each has a UUID, content text, and optional parent-id forming a tree hierarchy.

## Workflow: Writing an Outline Note

### Step 1: Create or find the note

Use the \`create_note\` tool with \`database_name\` and \`title\`.
The response contains the note's \`id\` and \`root-nav-id\` — save both.

To find an existing note, use \`get_notes\` or \`get_all_notes\` with \`database_id\`.

### Step 2: Read existing outline

Use \`get_note_navigation\` with \`note_id\` to see all existing nav nodes, their IDs, content, and parent relationships.

### Step 3: Build the outline tree

Each outline item is a nav node. Use \`create_or_update_nav\` to add nodes:

- **Top-level items**: Set \`parent_id\` to the note's \`root-nav-id\`
- **Sub-items**: Set \`parent_id\` to the parent node's \`nav_id\`
- **nav_id**: Generate a new UUID for each new node. To update an existing node, reuse its \`nav_id\`.

### Step 4: Update existing nodes

To change a node's content, call \`create_or_update_nav\` with the same \`nav_id\` and new \`content\`.

## Best Practices

1. **Always get root-nav-id first** — top-level outline items must use the note's \`root-nav-id\` as \`parent_id\`.
2. **Build top-down** — create parent nodes before children, since children need the parent's nav_id.
3. **Check before writing** — use \`get_note_navigation\` to see what exists before adding nodes.
4. **Keep content concise** — each nav node is a single outline entry.
5. **Plan the full structure first** — when building a multi-level outline, plan all sections, then create nodes in order (parents before children).

## Example: Building a Project Plan Outline

1. Create note → get \`note-id\` and \`root-nav-id\`
2. Create "Goals" node under root → get \`goals-nav-id\`
3. Create "Timeline" node under root → get \`timeline-nav-id\`
4. Create sub-items under "Goals" using \`goals-nav-id\` as parent
5. Create sub-items under "Timeline" using \`timeline-nav-id\` as parent

## Tool Reference

| Tool | Required Params | Optional Params | Description |
|------|----------------|-----------------|-------------|
| \`create_note\` | \`database_name\`, \`title\` | | Create a new note |
| \`get_notes\` | \`database_id\` | \`page\`, \`page_size\` | List notes (paginated) |
| \`get_all_notes\` | \`database_id\` | | List all notes |
| \`update_note\` | \`note_id\` | \`title\`, \`content\` | Update note metadata |
| \`create_or_update_nav\` | \`note_id\`, \`nav_id\`, \`content\` | \`parent_id\` | Create/update outline node |
| \`get_note_navigation\` | \`note_id\` | | Get note's outline tree |
| \`get_all_navigation_nodes\` | \`database_id\` | \`page\`, \`page_size\` | List all nav nodes |
`.trim();

module.exports = HulunoteSkillMiddleware;
