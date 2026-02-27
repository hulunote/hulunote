const Middleware = require('./Middleware');

class SummarizationMiddleware extends Middleware {
  /**
   * @param {Object} options
   * @param {Object} options.llmClient - OpenRouterClient for summarization calls
   * @param {string} options.model - Model to use for summarization
   * @param {number} options.tokenThreshold - Estimated token count to trigger summarization (default 80000)
   */
  constructor({ llmClient, model, tokenThreshold = 80000 }) {
    super();
    this.llmClient = llmClient;
    this.model = model;
    this.tokenThreshold = tokenThreshold;
  }

  _estimateTokens(messages) {
    let charCount = 0;
    for (const msg of messages) {
      if (typeof msg.content === 'string') {
        charCount += msg.content.length;
      }
      if (msg.tool_calls) {
        charCount += JSON.stringify(msg.tool_calls).length;
      }
    }
    return Math.ceil(charCount / 4);
  }

  async beforeModelCall(messages, state) {
    const estimatedTokens = this._estimateTokens(messages);

    if (estimatedTokens <= this.tokenThreshold) {
      return { messages, state };
    }

    console.log(`[SummarizationMiddleware] Token estimate ${estimatedTokens} exceeds threshold ${this.tokenThreshold}, summarizing...`);

    // Keep the most recent messages (last 25% by count, minimum 4)
    const keepCount = Math.max(4, Math.floor(messages.length * 0.25));
    const oldMessages = messages.slice(0, messages.length - keepCount);
    const recentMessages = messages.slice(messages.length - keepCount);

    if (oldMessages.length === 0) {
      return { messages, state };
    }

    // Summarize old messages
    const summaryPrompt = [
      {
        role: 'system',
        content: 'You are a summarizer. Produce a concise summary of the following conversation, preserving key facts, decisions, tool results, and context needed for ongoing work. Output only the summary.'
      },
      ...oldMessages
    ];

    try {
      const summaryResponse = await this.llmClient.sendMessage({
        model: this.model,
        messages: summaryPrompt,
        max_tokens: 2000
      });

      const summaryText = summaryResponse.choices[0].message.content;

      const summarizedMessages = [
        {
          role: 'user',
          content: `[Conversation summary so far]\n${summaryText}`
        },
        ...recentMessages
      ];

      console.log(`[SummarizationMiddleware] Reduced ${messages.length} messages to ${summarizedMessages.length}`);
      return { messages: summarizedMessages, state };
    } catch (error) {
      console.error('[SummarizationMiddleware] Summarization failed, using original messages:', error);
      return { messages, state };
    }
  }
}

module.exports = SummarizationMiddleware;
