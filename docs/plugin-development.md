# Hulunote Plugin Development Guide

Hulunote provides a plugin system that lets you extend the outliner with custom components, styles, and JavaScript logic. Plugins are managed entirely from within Hulunote itself — no need to edit HTML or config files.

## Table of Contents

- [How Plugins Are Loaded](#how-plugins-are-loaded)
- [Code Blocks with CodeMirror](#code-blocks-with-codemirror)
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

Create a note with the title `hulunote/javascript`. Each **child block** is one JavaScript entry.

Three formats are supported:

**1. URL (loaded as `<script src="...">`)**

```
/plugins/hulunote-kanban-table-plugin.js
```

**2. Inline code (executed directly)**

```
console.log("hello from inline JS")
```

**3. Fenced code block with CodeMirror editor (recommended for inline code)**

    ```js
    window.HulunotePlugin.register({
      name: 'my-plugin',
      version: '1.0.0',
      renderers: {
        greeting: function(ctx) {
          ctx.container.innerHTML = '<div>Hello!</div>';
        }
      }
    });
    ```

When a block uses ``` fences, the code is displayed in a full **CodeMirror editor** with syntax highlighting, line numbers, and editable in place. The ``` fences are automatically stripped before execution.

### `hulunote/css`

Create a note with the title `hulunote/css`. Each **child block** is one CSS entry. Same three formats:

    ```css
    .my-custom-class {
      color: #6c8dfa;
      font-weight: bold;
    }
    ```

### Loading Timing

Plugins are loaded automatically after the database finishes syncing (all notes and blocks are in memory). Each URL or inline snippet is loaded only once — duplicates are ignored on hot-reload.

---

## Code Blocks with CodeMirror

Any block in the outliner whose content is wrapped in ``` fences will render as an **editable CodeMirror editor** instead of the normal text input.

### Syntax

    ```language
    your code here
    ```

The language tag is optional. Supported languages:

| Tag | Language |
|-----|----------|
| `js`, `javascript`, `ts`, `typescript`, `json` | JavaScript |
| `css` | CSS |
| `clj`, `cljs`, `clojure` | Clojure |
| `html` | HTML |
| `xml` | XML |
| `md`, `markdown` | Markdown |

### Features

- Syntax highlighting with a dark theme
- Line numbers
- Auto-indentation
- Tab inserts spaces (2-space indent)
- Line wrapping
- Press **Escape** to exit the editor
- Changes are saved automatically on blur (clicking outside)

### How It Works in Plugin Notes

In `hulunote/javascript` and `hulunote/css` notes, fenced code blocks serve double duty:

1. **Display** — The code is shown in a syntax-highlighted CodeMirror editor, making it easy to read and edit plugin code directly in the outliner
2. **Execution** — When the plugin system loads entries from these notes, it automatically strips the ``` fences and executes the inner code

This means you can write, edit, and manage plugin code entirely within Hulunote:

```
hulunote/javascript                    ← note title
  /plugins/kanban-table-plugin.js      ← child block: URL
  ```js                                ← child block: inline code in CodeMirror
  window.HulunotePlugin.register({
    name: 'quick-plugin',
    renderers: {
      hello: function(ctx) {
        ctx.container.innerHTML = '<b>Hello!</b>';
      }
    }
  });
  ```
```

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

A plugin that renders `{{hello}}` blocks with a greeting. Written as a fenced code block in the `hulunote/javascript` note:

    ```js
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

This code block renders in Hulunote as a **CodeMirror editor** with JavaScript syntax highlighting. The plugin system strips the ``` fences and executes the code automatically.

Usage in any note:

```
{{hello}}
  Alice
  Bob
```

Result: renders a styled box saying "Hello, Alice, Bob!"

Alternatively, save the code as a `.js` file and reference it by URL:

```
/plugins/hello-plugin.js
```

---

## Official Example Plugin

See [hulunote-kanban-table-plugin](https://github.com/hulunote/hulunote-kanban-table-plugin) for a full TypeScript example that implements:

- **`{{table}}`** — Renders child blocks as a table (pipe `|` separated columns)
- **`{{kanban}}`** — Renders child blocks as a kanban board (children = columns, grandchildren = cards)

This plugin demonstrates custom styles, multiple renderers, tag extraction, and the full build pipeline with TypeScript + esbuild.
