const Middleware = require('./Middleware');

class McpToolsMiddleware extends Middleware {
  /**
   * @param {Object} mcpManager - McpClientManager instance
   */
  constructor(mcpManager) {
    super();
    this.mcpManager = mcpManager;
    // Map sanitized tool name -> { clientId, toolName }
    this.toolNameMap = new Map();
  }

  async getTools(state) {
    if (!this.mcpManager) return [];

    const clientIds = this.mcpManager.getAllClientIds();
    if (clientIds.length === 0) return [];

    this.toolNameMap.clear();
    const tools = [];
    for (const clientId of clientIds) {
      try {
        const clientTools = await this.mcpManager.listTools(clientId);
        const safeClientId = clientId.replace(/[^a-zA-Z0-9_-]/g, '_');
        for (const tool of clientTools) {
          const safeName = `${safeClientId}__${tool.name}`.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 128);
          this.toolNameMap.set(safeName, { clientId, toolName: tool.name });
          tools.push({
            type: 'function',
            function: {
              name: safeName,
              description: tool.description || '',
              parameters: tool.inputSchema || { type: 'object', properties: {} }
            }
          });
        }
      } catch (error) {
        console.error(`[McpToolsMiddleware] Error getting tools for ${clientId}:`, error);
      }
    }

    return tools;
  }

  /**
   * Resolve a sanitized tool name back to original clientId and toolName
   */
  resolve(safeName) {
    return this.toolNameMap.get(safeName) || null;
  }
}

module.exports = McpToolsMiddleware;
