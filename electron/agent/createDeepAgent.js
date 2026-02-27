const AgentRunner = require('./AgentRunner');
const AgentState = require('./AgentState');
const McpToolsMiddleware = require('./middleware/McpToolsMiddleware');
const SummarizationMiddleware = require('./middleware/SummarizationMiddleware');
const SubAgentMiddleware = require('./middleware/SubAgentMiddleware');
const PatchToolCallsMiddleware = require('./middleware/PatchToolCallsMiddleware');

/**
 * Factory function that creates a runnable agent.
 *
 * @param {Object} options
 * @param {Object} options.llmClient - OpenRouterClient instance
 * @param {string} options.model - Model identifier (e.g. 'anthropic/claude-3.5-sonnet')
 * @param {Object} options.mcpManager - McpClientManager instance (optional)
 * @param {string} options.systemPrompt - System prompt (optional)
 * @param {Middleware[]} options.middleware - Custom middleware stack (optional; overrides default)
 * @param {Array} options.subAgents - Sub-agent configurations (optional)
 * @param {number} options.maxIterations - Max ReAct loop iterations (default 20)
 * @param {Function} options.onProgress - Progress callback (optional)
 *
 * @returns {Function} async (messages, initialState?) => result
 */
function createDeepAgent({
  llmClient,
  model,
  mcpManager,
  systemPrompt,
  middleware,
  subAgents = [],
  maxIterations = 20,
  onProgress
}) {
  // Build middleware stack
  let stack;
  if (middleware) {
    stack = middleware;
  } else {
    stack = [];

    // 1. MCP tools collection
    if (mcpManager) {
      stack.push(new McpToolsMiddleware(mcpManager));
    }

    // 2. Summarization when context grows large
    if (llmClient) {
      stack.push(new SummarizationMiddleware({
        llmClient,
        model,
        tokenThreshold: 80000
      }));
    }

    // 3. Sub-agent delegation
    if (subAgents.length > 0) {
      stack.push(new SubAgentMiddleware({
        llmClient,
        parentModel: model,
        mcpManager,
        subAgents,
        createDeepAgent
      }));
    }

    // 4. Patch dangling tool calls (always last before model call)
    stack.push(new PatchToolCallsMiddleware());
  }

  const runner = new AgentRunner({
    llmClient,
    model,
    mcpManager,
    systemPrompt,
    middleware: stack,
    maxIterations,
    onProgress
  });

  return async (messages, initialState) => {
    const state = initialState || new AgentState();
    return runner.run(messages, state);
  };
}

module.exports = createDeepAgent;
