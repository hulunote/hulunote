const AgentState = require('./AgentState');

class AgentRunner {
  /**
   * @param {Object} options
   * @param {Object} options.llmClient - OpenRouterClient instance
   * @param {string} options.model - Model identifier
   * @param {Object} options.mcpManager - McpClientManager instance
   * @param {string} options.systemPrompt - System prompt
   * @param {Middleware[]} options.middleware - Ordered middleware stack
   * @param {number} options.maxIterations - Safety limit (default 20)
   * @param {Function} options.onProgress - Progress callback
   */
  constructor({ llmClient, model, mcpManager, systemPrompt, middleware = [], maxIterations = 20, onProgress }) {
    this.llmClient = llmClient;
    this.model = model;
    this.mcpManager = mcpManager;
    this.systemPrompt = systemPrompt;
    this.middleware = middleware;
    this.maxIterations = maxIterations;
    this.onProgress = onProgress || (() => {});
  }

  async run(messages, initialState) {
    const state = initialState || new AgentState();
    const allToolCalls = [];
    const allToolResults = [];
    let currentMessages = [...messages];
    let iteration = 0;

    // Collect tools from all middleware
    const middlewareTools = [];
    for (const mw of this.middleware) {
      const tools = await mw.getTools(state);
      middlewareTools.push(...tools);
    }

    while (iteration < this.maxIterations) {
      iteration++;
      console.log(`[AgentRunner] Iteration ${iteration}/${this.maxIterations}`);
      this.onProgress({ type: 'iteration', iteration, maxIterations: this.maxIterations });

      // 1. Compose system prompt through middleware
      let systemPrompt = this.systemPrompt || '';
      for (const mw of this.middleware) {
        systemPrompt = await mw.modifySystemPrompt(systemPrompt, state);
      }

      // 2. Pre-process messages through middleware
      let processedMessages = currentMessages;
      for (const mw of this.middleware) {
        const result = await mw.beforeModelCall(processedMessages, state);
        processedMessages = result.messages;
      }

      // Build final messages array with system prompt
      const llmMessages = [];
      if (systemPrompt) {
        llmMessages.push({ role: 'system', content: systemPrompt });
      }
      llmMessages.push(...processedMessages);

      // Build tools list (middleware tools are in OpenAI format already)
      const tools = middlewareTools.length > 0 ? middlewareTools : null;

      // 3. LLM call
      const response = await this.llmClient.sendMessage({
        model: this.model,
        messages: llmMessages,
        tools
      });

      const assistantMessage = response.choices[0].message;

      // 4. If no tool_calls, we're done
      if (!assistantMessage.tool_calls || assistantMessage.tool_calls.length === 0) {
        console.log(`[AgentRunner] Done after ${iteration} iteration(s), no more tool calls`);

        // Run onComplete middleware
        let finalMessage = assistantMessage;
        for (const mw of this.middleware) {
          finalMessage = await mw.onComplete(finalMessage, state);
        }

        return {
          success: true,
          response,
          toolCalls: allToolCalls.length > 0 ? allToolCalls : undefined,
          toolResults: allToolResults.length > 0 ? allToolResults : undefined,
          iterations: iteration
        };
      }

      // 5. Execute tool calls
      console.log(`[AgentRunner] Executing ${assistantMessage.tool_calls.length} tool call(s)`);
      this.onProgress({
        type: 'tool_calls',
        iteration,
        tools: assistantMessage.tool_calls.map(tc => tc.function.name)
      });

      const iterationToolResults = [];

      for (const toolCall of assistantMessage.tool_calls) {
        const fullName = toolCall.function.name;

        let args = {};
        try {
          args = JSON.parse(toolCall.function.arguments || '{}');
        } catch (e) {
          console.error('[AgentRunner] Error parsing tool arguments:', e);
        }

        let resultContent;
        try {
          // Check if any middleware provides this tool (has _executor)
          const middlewareTool = middlewareTools.find(t => t.function.name === fullName);

          if (middlewareTool && middlewareTool._executor) {
            // Middleware-provided tool
            resultContent = await middlewareTool._executor(args, state);
          } else {
            // MCP tool: parse clientId__toolName
            const [clientId, ...toolNameParts] = fullName.split('__');
            const toolName = toolNameParts.join('__');
            const result = await this.mcpManager.callTool(clientId, toolName, args);
            resultContent = JSON.stringify(result);
          }
        } catch (error) {
          resultContent = JSON.stringify({ error: error.message });
        }

        // 6. Run afterToolExecution middleware
        for (const mw of this.middleware) {
          resultContent = await mw.afterToolExecution(fullName, args, resultContent, state);
        }

        iterationToolResults.push({
          tool_call_id: toolCall.id,
          role: 'tool',
          content: typeof resultContent === 'string' ? resultContent : JSON.stringify(resultContent)
        });
      }

      allToolCalls.push(...assistantMessage.tool_calls);
      allToolResults.push(...iterationToolResults);

      // 7. Append assistant + tool messages, loop
      currentMessages = [
        ...currentMessages,
        assistantMessage,
        ...iterationToolResults
      ];
    }

    // Hit max iterations — do a final call without tools for a summary
    console.log(`[AgentRunner] Hit max iterations (${this.maxIterations}), requesting final response`);
    this.onProgress({ type: 'max_iterations', maxIterations: this.maxIterations });

    const finalResponse = await this.llmClient.sendMessage({
      model: this.model,
      messages: currentMessages
    });

    return {
      success: true,
      response: finalResponse,
      toolCalls: allToolCalls,
      toolResults: allToolResults,
      iterations: this.maxIterations,
      maxIterationsReached: true
    };
  }
}

module.exports = AgentRunner;
