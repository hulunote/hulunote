const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process to use
// the ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('electronAPI', {
  // ============= Basic App Info =============
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  getPlatform: () => ipcRenderer.invoke('get-platform'),
  isElectron: true,

  // ============= Chat / OpenRouter API =============
  chat: {
    setApiKey: (apiKey) => ipcRenderer.invoke('chat:set-api-key', apiKey),
    getApiKey: () => ipcRenderer.invoke('chat:get-api-key'),
    setModel: (model) => ipcRenderer.invoke('chat:set-model', model),
    getModel: () => ipcRenderer.invoke('chat:get-model'),
    getModels: () => ipcRenderer.invoke('chat:get-models'),
    sendMessage: (params) => ipcRenderer.invoke('chat:send-message', params),
  },

  // ============= Auth =============
  setAuthToken: (token) => ipcRenderer.invoke('hulunote:set-auth-token', token),

  // ============= MCP Configuration =============
  mcp: {
    // Settings management
    loadSettings: () => ipcRenderer.invoke('mcp:load-settings'),
    saveSettings: (settings) => ipcRenderer.invoke('mcp:save-settings', settings),

    // Server management
    getServers: () => ipcRenderer.invoke('mcp:get-servers'),
    addServer: (serverConfig) => ipcRenderer.invoke('mcp:add-server', serverConfig),
    removeServer: (serverName) => ipcRenderer.invoke('mcp:remove-server', serverName),

    // Client operations
    createClient: (serverConfig) => ipcRenderer.invoke('mcp:create-client', serverConfig),
    disconnectClient: (clientId) => ipcRenderer.invoke('mcp:disconnect-client', clientId),
    isConnected: (clientId) => ipcRenderer.invoke('mcp:is-connected', clientId),

    // Tool operations
    listTools: (clientId) => ipcRenderer.invoke('mcp:list-tools', clientId),
    callTool: (params) => ipcRenderer.invoke('mcp:call-tool', params),
    getAllTools: () => ipcRenderer.invoke('mcp:get-all-tools'),

    // Resource operations
    listResources: (clientId) => ipcRenderer.invoke('mcp:list-resources', clientId),
    readResource: (params) => ipcRenderer.invoke('mcp:read-resource', params),

    // Event listeners
    onServerConnected: (callback) => {
      ipcRenderer.on('mcp:server-connected', (event, data) => callback(data));
    },
    onServerDisconnected: (callback) => {
      ipcRenderer.on('mcp:server-disconnected', (event, data) => callback(data));
    },

    // Remove event listeners
    removeServerConnectedListener: () => {
      ipcRenderer.removeAllListeners('mcp:server-connected');
    },
    removeServerDisconnectedListener: () => {
      ipcRenderer.removeAllListeners('mcp:server-disconnected');
    }
  }
});

// Notify the renderer that we're running in Electron
window.addEventListener('DOMContentLoaded', () => {
  // Add a class to body to indicate we're in Electron
  document.body.classList.add('electron-app');
});

console.log('Preload script loaded - MCP API available');
