const AgentRunner = require('./AgentRunner');
const AgentState = require('./AgentState');
const McpToolsMiddleware = require('./middleware/McpToolsMiddleware');
const SummarizationMiddleware = require('./middleware/SummarizationMiddleware');
const SubAgentMiddleware = require('./middleware/SubAgentMiddleware');
const PatchToolCallsMiddleware = require('./middleware/PatchToolCallsMiddleware');
const HulunoteSkillMiddleware = require('./middleware/HulunoteSkillMiddleware');
const HulunoteToolsMiddleware = require('./middleware/HulunoteToolsMiddleware');

/**
 * Factory function that creates a runnable agent.
 *
 * @param {Object} options
 * @param {Object} options.llmClient - OpenRouterClient instance
 * @param {string} options.model - Model identifier (e.g. 'anthropic/claude-3.5-sonnet')
 * @param {Object} options.mcpManager - McpClientManager instance (optional, for external MCP servers)
 * @param {Object} options.hulunoteTools - HulunoteToolsMiddleware instance (optional, for built-in Hulunote tools)
 * @param {string} options.systemPrompt - System prompt (optional)
 * @param {Middleware[]} options.middleware - Custom middleware stack (optional; overrides default)
 * @param {string} options.databaseName - Current database name for Hulunote context
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
  hulunoteTools,
  systemPrompt,
  middleware,
  databaseName,
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

    // 0. Hulunote outline-writing skill knowledge (system prompt)
    stack.push(new HulunoteSkillMiddleware({ databaseName }));

    // 1. Built-in Hulunote tools (direct HTTP, no MCP process needed)
    if (hulunoteTools) {
      stack.push(hulunoteTools);
    }

    // 2. External MCP tools (user-configured MCP servers)
    if (mcpManager) {
      stack.push(new McpToolsMiddleware(mcpManager));
    }

    // 3. Summarization when context grows large
    if (llmClient) {
      stack.push(new SummarizationMiddleware({
        llmClient,
        model,
        tokenThreshold: 80000
      }));
    }

    // 4. Sub-agent delegation
    if (subAgents.length > 0) {
      stack.push(new SubAgentMiddleware({
        llmClient,
        parentModel: model,
        mcpManager,
        subAgents,
        createDeepAgent
      }));
    }

    // 5. Patch dangling tool calls (always last before model call)
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
