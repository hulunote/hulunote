const Middleware = require('./Middleware');

class McpToolsMiddleware extends Middleware {
  /**
   * @param {Object} mcpManager - McpClientManager instance
   */
  constructor(mcpManager) {
    super();
    this.mcpManager = mcpManager;
  }

  async getTools(state) {
    if (!this.mcpManager) return [];

    const clientIds = this.mcpManager.getAllClientIds();
    if (clientIds.length === 0) return [];

    const tools = [];
    for (const clientId of clientIds) {
      try {
        const clientTools = await this.mcpManager.listTools(clientId);
        for (const tool of clientTools) {
          tools.push({
            type: 'function',
            function: {
              name: `${clientId}__${tool.name}`,
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
}

module.exports = McpToolsMiddleware;
