const axios = require('axios');

class OpenRouterClient {
  constructor(apiKey, baseURL = 'https://openrouter.ai/api/v1') {
    this.apiKey = apiKey;
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL: this.baseURL,
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://github.com/hulunote/hulunote',
        'X-Title': 'Hulunote MCP Chat'
      }
    });
  }

  /**
   * Update API key
   */
  setApiKey(apiKey) {
    this.apiKey = apiKey;
    this.client.defaults.headers['Authorization'] = `Bearer ${apiKey}`;
  }

  /**
   * Get available models
   */
  async getModels() {
    try {
      const response = await this.client.get('/models');
      return response.data.data || [];
    } catch (error) {
      throw new Error(`Failed to fetch models: ${error.message}`);
    }
  }

  /**
   * Send a message with optional tools
   */
  async sendMessage(options) {
    const {
      model,
      messages,
      tools = null,
      tool_choice = 'auto',
      temperature = 0.7,
      max_tokens = 4000,
      top_p = 1,
      frequency_penalty = 0,
      presence_penalty = 0,
      stream = false
    } = options;

    try {
      const payload = {
        model,
        messages,
        temperature,
        max_tokens,
        top_p,
        frequency_penalty,
        presence_penalty,
        stream
      };

      // Add tools if provided
      if (tools && tools.length > 0) {
        payload.tools = tools;
        payload.tool_choice = tool_choice;
      }

      const response = await this.client.post('/chat/completions', payload);
      return response.data;
    } catch (error) {
      if (error.response) {
        throw new Error(`API Error: ${error.response.data.error?.message || error.message}`);
      }
      throw new Error(`Failed to send message: ${error.message}`);
    }
  }
}

module.exports = { OpenRouterClient };
