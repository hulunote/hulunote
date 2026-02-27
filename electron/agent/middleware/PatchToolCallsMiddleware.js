const Middleware = require('./Middleware');

class PatchToolCallsMiddleware extends Middleware {
  /**
   * Scan messages for assistant tool_calls that lack corresponding tool responses.
   * Insert placeholder responses so the LLM doesn't error on missing tool results.
   */
  async beforeModelCall(messages, state) {
    const patched = [];
    const pendingToolCallIds = new Set();

    for (const msg of messages) {
      patched.push(msg);

      if (msg.role === 'assistant' && msg.tool_calls) {
        for (const tc of msg.tool_calls) {
          pendingToolCallIds.add(tc.id);
        }
      }

      if (msg.role === 'tool' && msg.tool_call_id) {
        pendingToolCallIds.delete(msg.tool_call_id);
      }
    }

    // If there are dangling tool calls at the end, insert placeholder responses
    if (pendingToolCallIds.size > 0) {
      console.log(`[PatchToolCallsMiddleware] Patching ${pendingToolCallIds.size} dangling tool call(s)`);
      for (const id of pendingToolCallIds) {
        patched.push({
          role: 'tool',
          tool_call_id: id,
          content: JSON.stringify({ note: 'Tool execution was interrupted. No result available.' })
        });
      }
    }

    return { messages: patched, state };
  }
}

module.exports = PatchToolCallsMiddleware;
