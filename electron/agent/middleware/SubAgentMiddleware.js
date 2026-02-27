const Middleware = require('./Middleware');

class SubAgentMiddleware extends Middleware {
  /**
   * @param {Object} options
   * @param {Object} options.llmClient - OpenRouterClient instance
   * @param {string} options.parentModel - Parent agent's model (fallback for sub-agents)
   * @param {Object} options.mcpManager - McpClientManager instance
   * @param {Array} options.subAgents - Sub-agent configurations
   * @param {Function} options.createDeepAgent - Reference to createDeepAgent factory (avoids circular dep)
   */
  constructor({ llmClient, parentModel, mcpManager, subAgents, createDeepAgent }) {
    super();
    this.llmClient = llmClient;
    this.parentModel = parentModel;
    this.mcpManager = mcpManager;
    this.subAgents = subAgents || [];
    this.createDeepAgent = createDeepAgent;
  }

  async getTools(state) {
    if (this.subAgents.length === 0) return [];

    const agentNames = this.subAgents.map(a => a.name).join(', ');

    return [{
      type: 'function',
      function: {
        name: 'delegate_task',
        description: `Delegate a task to a specialized sub-agent. Available agents: ${agentNames}. Each agent runs independently and returns a result.`,
        parameters: {
          type: 'object',
          properties: {
            agent_name: {
              type: 'string',
              description: `Name of the sub-agent to delegate to. One of: ${agentNames}`,
              enum: this.subAgents.map(a => a.name)
            },
            task_description: {
              type: 'string',
              description: 'A clear description of the task for the sub-agent to perform.'
            }
          },
          required: ['agent_name', 'task_description']
        }
      },
      _executor: async (args, parentState) => {
        return this._executeSubAgent(args, parentState);
      }
    }];
  }

  async _executeSubAgent({ agent_name, task_description }, parentState) {
    const config = this.subAgents.find(a => a.name === agent_name);
    if (!config) {
      return JSON.stringify({ error: `Unknown sub-agent: ${agent_name}` });
    }

    console.log(`[SubAgentMiddleware] Delegating to sub-agent "${agent_name}": ${task_description}`);

    const forkedState = parentState.fork(config.sharedStateKeys || []);

    // Create sub-agent with no sub-agents of its own (prevent recursion)
    const runAgent = this.createDeepAgent({
      llmClient: this.llmClient,
      model: config.model || this.parentModel,
      mcpManager: this.mcpManager,
      systemPrompt: config.systemPrompt || `You are the "${agent_name}" agent.`,
      subAgents: [],
      maxIterations: config.maxIterations || 10
    });

    try {
      const result = await runAgent(
        [{ role: 'user', content: task_description }],
        forkedState
      );

      // Merge shared state back
      if (config.sharedStateKeys && config.sharedStateKeys.length > 0) {
        parentState.mergeFrom(forkedState, config.sharedStateKeys);
      }

      const content = result.response?.choices?.[0]?.message?.content || '';
      return JSON.stringify({
        agent: agent_name,
        result: content,
        iterations: result.iterations
      });
    } catch (error) {
      console.error(`[SubAgentMiddleware] Sub-agent "${agent_name}" failed:`, error);
      return JSON.stringify({ error: `Sub-agent "${agent_name}" failed: ${error.message}` });
    }
  }
}

module.exports = SubAgentMiddleware;
