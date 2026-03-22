const Middleware = require('./Middleware');
const https = require('https');
const http = require('http');
const crypto = require('crypto');

/**
 * Built-in Hulunote tools middleware.
 * Directly calls the Hulunote HTTP API — no MCP server process needed.
 */
class HulunoteToolsMiddleware extends Middleware {
  constructor({ token, apiBase } = {}) {
    super();
    this.token = token || '';
    this.apiBase = (apiBase || 'https://www.hulunote.top').replace(/\/+$/, '');
  }

  setToken(token) {
    this.token = token;
  }

  // ==================== HTTP ====================

  _post(endpoint, data) {
    return new Promise((resolve, reject) => {
      const body = JSON.stringify(data);
      const urlObj = new URL(this.apiBase + endpoint);
      const proto = urlObj.protocol === 'https:' ? https : http;
      const options = {
        hostname: urlObj.hostname,
        port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
        path: urlObj.pathname + urlObj.search,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
          'x-functor-api-token': this.token,
          'Referer': 'https://www.hulunote.top/',
        },
      };
      const req = proto.request(options, (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          try { resolve(JSON.parse(chunks.join(''))); }
          catch (e) { reject(new Error('JSON parse: ' + e.message)); }
        });
      });
      req.on('error', (e) => reject(e));
      req.write(body);
      req.end();
    });
  }

  // ==================== Tool definitions ====================

  async getTools(state) {
    if (!this.token) return [];

    return [
      {
        type: 'function',
        function: {
          name: 'hulunote__create_note',
          description: 'Create a new note in Hulunote. Returns note ID and root-nav-id needed for adding outline nodes.',
          parameters: {
            type: 'object',
            properties: {
              database_name: { type: 'string', description: 'Name of the database' },
              title:         { type: 'string', description: 'Title of the new note' },
            },
            required: ['database_name', 'title'],
          },
        },
        _executor: (args) => this._createNote(args),
      },
      {
        type: 'function',
        function: {
          name: 'hulunote__create_or_update_nav',
          description: 'Create or update an outline node. Use parid = root-nav-id for top-level items.',
          parameters: {
            type: 'object',
            properties: {
              note_id: { type: 'string', description: 'UUID of the note' },
              id:      { type: 'string', description: 'UUID v4 for this node (generate a new one for new nodes)' },
              content: { type: 'string', description: 'Text content of the outline node' },
              parid:   { type: 'string', description: 'UUID of the parent node (use root-nav-id for top-level)' },
              order:   { type: 'number', description: 'Sort order among siblings (1, 2, 3...)' },
            },
            required: ['note_id', 'id', 'parid'],
          },
        },
        _executor: (args) => this._createNav(args),
      },
      {
        type: 'function',
        function: {
          name: 'hulunote__get_note_navigation',
          description: 'Get all outline nodes for a note',
          parameters: {
            type: 'object',
            properties: {
              note_id: { type: 'string', description: 'UUID of the note' },
            },
            required: ['note_id'],
          },
        },
        _executor: (args) => this._getNavs(args),
      },
      {
        type: 'function',
        function: {
          name: 'hulunote__get_notes',
          description: 'Get paginated list of notes from a database',
          parameters: {
            type: 'object',
            properties: {
              database_id: { type: 'string', description: 'UUID of the database' },
              page:        { type: 'number', description: 'Page number (default: 1)' },
              page_size:   { type: 'number', description: 'Notes per page (default: 20)' },
            },
            required: ['database_id'],
          },
        },
        _executor: (args) => this._getNotes(args),
      },
      {
        type: 'function',
        function: {
          name: 'hulunote__get_all_notes',
          description: 'Get all notes from a database',
          parameters: {
            type: 'object',
            properties: {
              database_id: { type: 'string', description: 'UUID of the database' },
            },
            required: ['database_id'],
          },
        },
        _executor: (args) => this._getAllNotes(args),
      },
      {
        type: 'function',
        function: {
          name: 'hulunote__update_note',
          description: 'Update a note title',
          parameters: {
            type: 'object',
            properties: {
              note_id: { type: 'string', description: 'UUID of the note' },
              title:   { type: 'string', description: 'New title' },
            },
            required: ['note_id', 'title'],
          },
        },
        _executor: (args) => this._updateNote(args),
      },
    ];
  }

  // ==================== Tool executors ====================

  async _createNote({ database_name, title }) {
    const result = await this._post('/hulunote/new-note', {
      'database-name': database_name,
      title,
    });
    if (result.error) return JSON.stringify({ error: result.message });
    const noteId = result['hulunote-notes/id'];
    const rootNavId = result['hulunote-notes/root-nav-id'];
    const dbId = result['hulunote-notes/database-id'];
    return `Successfully created note!\nTitle: ${title}\nNote ID: ${noteId}\nDatabase ID: ${dbId}\nRoot Nav ID: ${rootNavId}`;
  }

  async _createNav({ note_id, id, content, parid, order }) {
    const payload = {
      'note-id': note_id,
      id: id,
      content: content || '',
      order: order || 0,
    };
    if (parid) payload.parid = parid;
    const result = await this._post('/hulunote/create-or-update-nav', payload);
    if (result.error) return JSON.stringify({ error: result.message });
    return `Created nav node ${id} under parent ${parid || 'root'}\nContent: ${content || ''}`;
  }

  async _getNavs({ note_id }) {
    const result = await this._post('/hulunote/get-note-navs', { 'note-id': note_id });
    if (result.error) return JSON.stringify({ error: result.message });
    const nodes = result['nav-list'] || [];
    if (nodes.length === 0) return 'No nav nodes found';
    const lines = nodes.map(n =>
      `Nav ID: ${n.id}\nContent: ${n.content}\nParent: ${n.parid}\nOrder: ${n['same-deep-order'] || 0}`
    );
    return `Outline (${nodes.length} nodes):\n` + lines.join('\n---\n');
  }

  async _getNotes({ database_id, page, page_size }) {
    const result = await this._post('/hulunote/get-note-list', {
      'database-id': database_id,
      page: page || 1,
      'page-size': page_size || 20,
    });
    if (result.error) return JSON.stringify({ error: result.message });
    const notes = result['note-list'] || [];
    if (notes.length === 0) return 'No notes found';
    const lines = notes.map(n =>
      `Title: ${n['hulunote-notes/title']}\nNote ID: ${n['hulunote-notes/id']}\nRoot Nav ID: ${n['hulunote-notes/root-nav-id']}`
    );
    return `Notes (${notes.length}):\n` + lines.join('\n---\n');
  }

  async _getAllNotes({ database_id }) {
    const result = await this._post('/hulunote/get-all-note-list', {
      'database-id': database_id,
    });
    if (result.error) return JSON.stringify({ error: result.message });
    const notes = result['note-list'] || [];
    if (notes.length === 0) return 'No notes found';
    const lines = notes.map(n =>
      `Title: ${n['hulunote-notes/title']}\nNote ID: ${n['hulunote-notes/id']}\nRoot Nav ID: ${n['hulunote-notes/root-nav-id']}`
    );
    return `All Notes (${notes.length}):\n` + lines.join('\n---\n');
  }

  async _updateNote({ note_id, title }) {
    const result = await this._post('/hulunote/update-hulunote-note', {
      'note-id': note_id,
      title,
    });
    if (result.error) return JSON.stringify({ error: result.message });
    return `Updated note ${note_id}, title: ${title}`;
  }
}

module.exports = HulunoteToolsMiddleware;
