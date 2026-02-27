class Middleware {
  get name() {
    return this.constructor.name;
  }

  /** Contribute tools to the agent's tool set. */
  async getTools(state) {
    return [];
  }

  /** Modify the system prompt before it's sent to the model. */
  async modifySystemPrompt(systemPrompt, state) {
    return systemPrompt;
  }

  /** Pre-process messages before the model call. */
  async beforeModelCall(messages, state) {
    return { messages, state };
  }

  /** Post-process a tool execution result. */
  async afterToolExecution(toolName, args, result, state) {
    return result;
  }

  /** Finalize the agent's response when no more tool calls remain. */
  async onComplete(finalMessage, state) {
    return finalMessage;
  }
}

module.exports = Middleware;
