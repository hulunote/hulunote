// MCP Client Manager - 管理 MCP 客户端连接
// 基于 @modelcontextprotocol/sdk 实现

const { spawn, execFileSync } = require('child_process');
const fs = require('fs');

class McpClientManager {
  constructor() {
    this.clients = new Map();
    this.transports = new Map();
    this.sdkPromise = null;
    this.Client = null;
    this.StdioClientTransport = null;
  }

  /**
   * 初始化 MCP SDK（动态导入 ES Module）
   */
  async initSDK() {
    if (this.sdkPromise) {
      return this.sdkPromise;
    }

    this.sdkPromise = (async () => {
      try {
        // 动态导入 MCP SDK（ES Module）
        const clientModule = await import('@modelcontextprotocol/sdk/client/index.js');
        const stdioModule = await import('@modelcontextprotocol/sdk/client/stdio.js');

        this.Client = clientModule.Client;
        this.StdioClientTransport = stdioModule.StdioClientTransport;

        console.log('MCP SDK loaded successfully');
        return { Client: this.Client, StdioClientTransport: this.StdioClientTransport };
      } catch (error) {
        console.error('Failed to load MCP SDK:', error);
        throw error;
      }
    })();

    return this.sdkPromise;
  }

  /**
   * 智能解析命令：如果 command 包含空格且没有提供 args，自动拆分
   */
  parseCommand(serverConfig) {
    let command = serverConfig.command;
    let args = serverConfig.args || [];

    if (command.includes(' ') && args.length === 0) {
      const parts = command.split(/\s+/).filter(p => p.length > 0);
      command = parts[0];
      args = parts.slice(1);
      console.log(`MCP: Auto-split command: "${command}" args: ${JSON.stringify(args)}`);
    }

    return { command, args };
  }

  /**
   * 预检查命令是否可执行
   */
  validateCommand(command) {
    try {
      // 检查命令文件是否存在（绝对路径时）
      if (command.startsWith('/') || command.startsWith('.')) {
        if (!fs.existsSync(command)) {
          return { valid: false, error: `Command not found: ${command}` };
        }
        try {
          fs.accessSync(command, fs.constants.X_OK);
        } catch {
          return { valid: false, error: `Command not executable: ${command}` };
        }
      }
      return { valid: true };
    } catch (error) {
      return { valid: false, error: `Command validation failed: ${error.message}` };
    }
  }

  /**
   * 创建并连接 MCP 客户端
   * @param {Object} serverConfig - 服务器配置
   * @param {string} serverConfig.name - 服务器名称
   * @param {string} serverConfig.command - 启动命令
   * @param {string[]} serverConfig.args - 命令参数
   * @param {Object} serverConfig.env - 环境变量（可选）
   */
  async createClient(serverConfig) {
    await this.initSDK();

    if (!this.Client || !this.StdioClientTransport) {
      throw new Error('MCP SDK not initialized');
    }

    const clientId = serverConfig.name || `client-${Date.now()}`;

    // 如果已存在同名客户端，先断开
    if (this.clients.has(clientId)) {
      await this.removeClient(clientId);
    }

    // 智能解析命令
    const { command, args } = this.parseCommand(serverConfig);

    // 预检查命令
    const validation = this.validateCommand(command);
    if (!validation.valid) {
      throw new Error(validation.error);
    }

    // 如果有脚本参数，也检查脚本文件是否存在
    if (args.length > 0 && (args[0].endsWith('.py') || args[0].endsWith('.js') || args[0].endsWith('.ts'))) {
      if (!fs.existsSync(args[0])) {
        throw new Error(`Script file not found: ${args[0]}`);
      }
    }

    try {
      // 创建客户端实例
      const client = new this.Client({
        name: 'hulunote-mcp-client',
        version: '1.0.0',
      }, {
        capabilities: {
          tools: {},
          resources: {},
          prompts: {}
        }
      });

      // 准备环境变量
      const env = {
        ...process.env,
        ...(serverConfig.env || {})
      };

      console.log(`MCP: Starting server with command: "${command}" args: ${JSON.stringify(args)}`);

      // 创建 stdio transport，stderr 设为 pipe 以便捕获错误输出
      const transport = new this.StdioClientTransport({
        command: command,
        args: args,
        env: env,
        stderr: 'pipe'
      });

      // 收集 stderr 输出用于错误诊断
      let stderrOutput = '';

      // 包装 transport.start 以捕获 stderr
      const originalStart = transport.start.bind(transport);
      transport.start = async function() {
        const result = await originalStart();
        // start 完成后 _process 已可用，挂载 stderr 监听
        if (transport._process && transport._process.stderr) {
          transport._process.stderr.on('data', (data) => {
            const text = data.toString();
            stderrOutput += text;
            console.error(`MCP Server stderr [${clientId}]:`, text);
          });
        }
        return result;
      };

      // 连接到服务器
      try {
        await client.connect(transport);
      } catch (connectError) {
        // 连接失败时，提供更详细的错误信息
        let errorMsg = connectError.message || 'Connection failed';
        if (stderrOutput.trim()) {
          errorMsg += `\nServer stderr: ${stderrOutput.trim().substring(0, 500)}`;
        }
        throw new Error(errorMsg);
      }

      // 存储客户端和 transport
      this.clients.set(clientId, client);
      this.transports.set(clientId, transport);

      console.log(`MCP Client ${clientId} connected successfully`);

      return {
        clientId,
        serverInfo: client.getServerVersion ? client.getServerVersion() : null
      };
    } catch (error) {
      console.error(`Failed to create MCP client ${clientId}:`, error);
      throw error;
    }
  }

  /**
   * 获取指定客户端
   */
  getClient(clientId) {
    return this.clients.get(clientId);
  }

  /**
   * 获取所有客户端 ID
   */
  getAllClientIds() {
    return Array.from(this.clients.keys());
  }

  /**
   * 列出客户端可用的工具
   */
  async listTools(clientId) {
    const client = this.clients.get(clientId);
    if (!client) {
      throw new Error(`Client ${clientId} not found`);
    }

    try {
      const result = await client.listTools();
      return result.tools || [];
    } catch (error) {
      console.error(`Failed to list tools for client ${clientId}:`, error);
      throw error;
    }
  }

  /**
   * 调用工具
   * @param {string} clientId - 客户端 ID
   * @param {string} toolName - 工具名称
   * @param {Object} args - 工具参数
   */
  async callTool(clientId, toolName, args = {}) {
    const client = this.clients.get(clientId);
    if (!client) {
      throw new Error(`Client ${clientId} not found`);
    }

    try {
      const result = await client.callTool({
        name: toolName,
        arguments: args
      });
      return result;
    } catch (error) {
      console.error(`Failed to call tool ${toolName} on client ${clientId}:`, error);
      throw error;
    }
  }

  /**
   * 列出客户端可用的资源
   */
  async listResources(clientId) {
    const client = this.clients.get(clientId);
    if (!client) {
      throw new Error(`Client ${clientId} not found`);
    }

    try {
      const result = await client.listResources();
      return result.resources || [];
    } catch (error) {
      console.error(`Failed to list resources for client ${clientId}:`, error);
      throw error;
    }
  }

  /**
   * 读取资源
   */
  async readResource(clientId, uri) {
    const client = this.clients.get(clientId);
    if (!client) {
      throw new Error(`Client ${clientId} not found`);
    }

    try {
      const result = await client.readResource({ uri });
      return result;
    } catch (error) {
      console.error(`Failed to read resource ${uri} on client ${clientId}:`, error);
      throw error;
    }
  }

  /**
   * 断开并移除客户端
   */
  async removeClient(clientId) {
    const client = this.clients.get(clientId);
    const transport = this.transports.get(clientId);

    if (client) {
      try {
        await client.close();
      } catch (error) {
        console.error(`Error closing client ${clientId}:`, error);
      }
      this.clients.delete(clientId);
    }

    if (transport) {
      try {
        await transport.close();
      } catch (error) {
        console.error(`Error closing transport for ${clientId}:`, error);
      }
      this.transports.delete(clientId);
    }

    console.log(`MCP Client ${clientId} removed`);
  }

  /**
   * 断开所有客户端
   */
  async disconnectAll() {
    const clientIds = Array.from(this.clients.keys());
    await Promise.all(clientIds.map(id => this.removeClient(id)));
    console.log('All MCP clients disconnected');
  }

  /**
   * 检查客户端是否已连接
   */
  isConnected(clientId) {
    return this.clients.has(clientId);
  }
}

module.exports = McpClientManager;
