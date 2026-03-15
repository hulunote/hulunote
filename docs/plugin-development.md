# Hulunote Plugin Development Guide

Hulunote provides a plugin system that lets you extend the outliner with custom components, styles, and JavaScript logic. Plugins are managed entirely from within Hulunote itself — no need to edit HTML or config files.

## Table of Contents

- [How Plugins Are Loaded](#how-plugins-are-loaded)
- [Plugin API Reference](#plugin-api-reference)
- [Writing a Custom Renderer](#writing-a-custom-renderer)
- [RenderContext](#rendercontext)
- [Lifecycle Hooks](#lifecycle-hooks)
- [Full Example: Minimal Plugin](#full-example-minimal-plugin)
- [Official Example Plugin](#official-example-plugin)

---

## How Plugins Are Loaded

Plugins are loaded through two **special notes** in your database:

### `hulunote/javascript`

Create a note with the title `hulunote/javascript`. Each **child block** is one JavaScript entry:

```
hulunote/javascript
  /plugins/hulunote-kanban-table-plugin.js
  https://cdn.example.com/another-plugin.js
  console.log("inline JS also works")
```

- Starts with `/`, `http://`, or `https://` → loaded as `<script src="...">`
- Otherwise → executed as inline `<script>` code

### `hulunote/css`

Create a note with the title `hulunote/css`. Each **child block** is one CSS entry:

```
hulunote/css
  /plugins/my-theme.css
  https://cdn.example.com/style.css
  .my-class { color: red; font-weight: bold; }
```

- Starts with `/`, `http://`, or `https://` → loaded as `<link rel="stylesheet" href="...">`
- Otherwise → injected as inline `<style>` code

### Loading Timing

Plugins are loaded automatically after the database finishes syncing (all notes and blocks are in memory). Each URL or inline snippet is loaded only once — duplicates are ignored on hot-reload.

---

## Plugin API Reference

After Hulunote initializes, the API is available at `window.HulunotePlugin`.

### `register(pluginDef)`

Register a plugin.

```js
window.HulunotePlugin.register({
  name: 'my-plugin',       // required, unique identifier
  version: '1.0.0',        // optional
  styles: '/* CSS text */', // optional, injected into <head>
  renderers: {              // optional, block renderer functions
    'chart': renderChart,
    'timeline': renderTimeline,
  },
  init(api) { },            // optional, called after registration
  destroy() { },            // optional, called on unregister
});
```

### `unregister(name)`

Remove a plugin and clean up its renderers and styles.

```js
window.HulunotePlugin.unregister('my-plugin');
```

### `getPlugins()`

Returns an array of registered plugin names.

```js
window.HulunotePlugin.getPlugins(); // ['my-plugin', 'kanban-table']
```

### `getBlockTree(blockId)`

Returns a block and its full subtree as a JS object.

```js
const tree = window.HulunotePlugin.getBlockTree('some-block-id');
// { id: '...', content: '...', children: [{ id, content, children }, ...] }
```

### `getBlockChildren(blockId)`

Returns only the direct children array of a block.

```js
const kids = window.HulunotePlugin.getBlockChildren('some-block-id');
// [{ id, content, children }, ...]
```

---

## Writing a Custom Renderer

A renderer is a function that takes control of a block's display when the block's content matches the pattern `{{renderer-name}}`.

### How It Works

1. User types `{{chart}}` in a block
2. Hulunote's render pipeline detects the `{{...}}` pattern
3. The matching renderer function is called with a `RenderContext`
4. The renderer fills `ctx.container` with custom HTML
5. Child blocks are **not** rendered by Hulunote — they are passed as structured data in `ctx.children`

### Pattern Syntax

```
{{renderer-name}}          →  matches renderer named "renderer-name"
{{renderer-name:params}}   →  same, with params string passed to ctx.params
```

Examples:
- `{{table}}` — triggers the `table` renderer
- `{{kanban}}` — triggers the `kanban` renderer
- `{{chart:bar}}` — triggers `chart` renderer with `ctx.params === "bar"`

### Editing

Users can still click the block to edit its text content (e.g., change `{{table}}` to something else). The plugin rendering only applies in view mode.

---

## RenderContext

The `ctx` object passed to every renderer function:

| Property      | Type          | Description                                      |
|---------------|---------------|--------------------------------------------------|
| `blockId`     | `string`      | The block's unique ID                            |
| `content`     | `string`      | The block's raw content (e.g., `"{{table}}"`)    |
| `params`      | `string|null` | Params after `:` in `{{name:params}}`, or null   |
| `children`    | `Array`       | Child blocks as `{ id, content, children }` tree |
| `container`   | `HTMLElement`  | The DOM element to render into                   |
| `api`         | `object`      | The `HulunotePlugin` API object                  |

### Children Data Structure

Each child in `ctx.children` is a recursive tree:

```js
{
  id: "uuid-string",
  content: "block text content",
  children: [           // grandchildren, or undefined if none
    { id, content, children }
  ]
}
```

This lets your renderer interpret the outline hierarchy however you want — as table rows, kanban columns, tree nodes, etc.

---

## Lifecycle Hooks

### `init(api)`

Called once when the plugin is registered. Use it for one-time setup like adding event listeners or logging.

```js
init(api) {
  console.log('Plugin loaded, available renderers:', api.getPlugins());
}
```

### `destroy()`

Called when the plugin is unregistered via `unregister()`. Use it to clean up event listeners or DOM elements you created outside of block renderers.

```js
destroy() {
  console.log('Plugin unloaded');
}
```

---

## Full Example: Minimal Plugin

A plugin that renders `{{hello}}` blocks with a greeting:

```js
// hello-plugin.js
(function() {
  window.HulunotePlugin.register({
    name: 'hello-plugin',
    version: '0.1.0',

    styles: `
      .hello-box {
        padding: 16px;
        border-radius: 8px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        font-size: 18px;
      }
    `,

    renderers: {
      hello: function(ctx) {
        const names = (ctx.children || []).map(c => c.content);
        ctx.container.innerHTML = '<div class="hello-box">'
          + (names.length > 0
              ? 'Hello, ' + names.join(', ') + '!'
              : 'Hello, World!')
          + '</div>';
      }
    }
  });
})();
```

Usage in the outliner:

```
{{hello}}
  Alice
  Bob
```

Result: renders a styled box saying "Hello, Alice, Bob!"

To install, put the file in `resources/public/plugins/hello-plugin.js`, then add `/plugins/hello-plugin.js` as a child block of the `hulunote/javascript` note.

---

## Official Example Plugin

See [hulunote-kanban-table-plugin](https://github.com/hulunote/hulunote-kanban-table-plugin) for a full TypeScript example that implements:

- **`{{table}}`** — Renders child blocks as a table (pipe `|` separated columns)
- **`{{kanban}}`** — Renders child blocks as a kanban board (children = columns, grandchildren = cards)

This plugin demonstrates custom styles, multiple renderers, tag extraction, and the full build pipeline with TypeScript + esbuild.
