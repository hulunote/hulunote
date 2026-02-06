const { app, BrowserWindow, Menu, shell, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
const fsPromises = require('fs').promises;
const McpClientManager = require('./mcp/mcpClientManager');

// MCP Manager instance
let mcpManager = null;

// MCP Configuration
const MCP_CONFIG_PATH = path.join(app.getPath('userData'), 'hulunote-mcp-config.json');
const DEFAULT_MCP_CONFIG = {
  mcpServers: [],
  lastUpdated: new Date().toISOString()
};

// Keep a global reference of the window object
let mainWindow;

// Determine if we're in development mode
const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged;

// Backend server URL
const BACKEND_URL = isDev 
  ? 'http://127.0.0.1:6689' 
  : 'https://www.hulunote.top';

function createWindow() {
  // Create the browser window
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 800,
    minHeight: 600,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js')
    },
    icon: path.join(__dirname, 'icons', 'icon.png'),
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    show: false,
    backgroundColor: '#ffffff'
  });

  // Load the app
  if (isDev) {
    // In development, load from shadow-cljs dev server
    mainWindow.loadURL('http://localhost:8803/html/hulunote.html');
    // Open DevTools in development
    mainWindow.webContents.openDevTools();
  } else {
    // In production, load the built files
    const htmlPath = path.join(__dirname, 'app', 'html', 'hulunote.html');
    mainWindow.loadFile(htmlPath);
  }

  // Show window when ready
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Open external links in default browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // Emitted when the window is closed
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Create application menu
  createMenu();
}

function createMenu() {
  const template = [
    {
      label: 'File',
      submenu: [
        { role: 'quit' }
      ]
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'selectAll' }
      ]
    },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { role: 'toggleDevTools' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    },
    {
      label: 'Window',
      submenu: [
        { role: 'minimize' },
        { role: 'zoom' },
        { role: 'close' }
      ]
    },
    {
      label: 'Help',
      submenu: [
        {
          label: 'Learn More',
          click: async () => {
            await shell.openExternal('https://github.com/hulunote/hulunote');
          }
        },
        {
          label: 'Documentation',
          click: async () => {
            await shell.openExternal('https://github.com/hulunote/hulunote#readme');
          }
        }
      ]
    }
  ];

  // macOS specific menu items
  if (process.platform === 'darwin') {
    template.unshift({
      label: app.getName(),
      submenu: [
        { role: 'about' },
        { type: 'separator' },
        { role: 'services' },
        { type: 'separator' },
        { role: 'hide' },
        { role: 'hideOthers' },
        { role: 'unhide' },
        { type: 'separator' },
        { role: 'quit' }
      ]
    });

    // Window menu
    template[4].submenu = [
      { role: 'close' },
      { role: 'minimize' },
      { role: 'zoom' },
      { type: 'separator' },
      { role: 'front' }
    ];
  }

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

// ============= MCP Configuration Management =============

async function loadMcpSettings() {
  try {
    const data = await fsPromises.readFile(MCP_CONFIG_PATH, 'utf-8');
    return JSON.parse(data);
  } catch (error) {
    if (error.code === 'ENOENT') {
      await saveMcpSettings(DEFAULT_MCP_CONFIG);
      return DEFAULT_MCP_CONFIG;
    }
    throw error;
  }
}

async function saveMcpSettings(settings) {
  try {
    settings.lastUpdated = new Date().toISOString();
    await fsPromises.writeFile(MCP_CONFIG_PATH, JSON.stringify(settings, null, 2), 'utf-8');
    return true;
  } catch (error) {
    console.error('Failed to save MCP settings:', error);
    throw error;
  }
}

// Initialize MCP Manager
async function initMcpManager() {
  try {
    mcpManager = new McpClientManager();
    await mcpManager.initSDK();
    console.log('MCP Manager initialized successfully');
  } catch (error) {
    console.error('Failed to initialize MCP Manager:', error);
  }
}

// ============= MCP IPC Handlers =============

// Load MCP settings
ipcMain.handle('mcp:load-settings', async () => {
  try {
    const settings = await loadMcpSettings();
    return { success: true, data: settings };
  } catch (error) {
    console.error('Error loading MCP settings:', error);
    return { success: false, error: error.message };
  }
});

// Save MCP settings
ipcMain.handle('mcp:save-settings', async (event, settings) => {
  try {
    await saveMcpSettings(settings);
    return { success: true };
  } catch (error) {
    console.error('Error saving MCP settings:', error);
    return { success: false, error: error.message };
  }
});

// Get MCP servers list
ipcMain.handle('mcp:get-servers', async () => {
  try {
    const settings = await loadMcpSettings();
    const connectedClients = mcpManager ? mcpManager.getAllClientIds() : [];
    const servers = (settings.mcpServers || []).map(server => ({
      ...server,
      connected: connectedClients.includes(server.name)
    }));
    return { success: true, data: servers };
  } catch (error) {
    console.error('Error getting MCP servers:', error);
    return { success: false, error: error.message, data: [] };
  }
});

// Add MCP server
ipcMain.handle('mcp:add-server', async (event, serverConfig) => {
  try {
    const settings = await loadMcpSettings();

    // Check if server with same name exists
    const existingIndex = settings.mcpServers.findIndex(s => s.name === serverConfig.name);

    if (existingIndex >= 0) {
      // Update existing server
      settings.mcpServers[existingIndex] = {
        ...serverConfig,
        updatedAt: new Date().toISOString()
      };
    } else {
      // Add new server
      settings.mcpServers.push({
        ...serverConfig,
        id: `server-${Date.now()}`,
        createdAt: new Date().toISOString()
      });
    }

    await saveMcpSettings(settings);
    return { success: true, servers: settings.mcpServers };
  } catch (error) {
    console.error('Error adding MCP server:', error);
    return { success: false, error: error.message };
  }
});

// Remove MCP server
ipcMain.handle('mcp:remove-server', async (event, serverName) => {
  try {
    const settings = await loadMcpSettings();
    settings.mcpServers = settings.mcpServers.filter(s => s.name !== serverName);
    await saveMcpSettings(settings);

    // Disconnect client if connected
    if (mcpManager && mcpManager.isConnected(serverName)) {
      await mcpManager.removeClient(serverName);
    }

    return { success: true, servers: settings.mcpServers };
  } catch (error) {
    console.error('Error removing MCP server:', error);
    return { success: false, error: error.message };
  }
});

// Create/connect MCP client
ipcMain.handle('mcp:create-client', async (event, serverConfig) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    const result = await mcpManager.createClient(serverConfig);

    // Notify renderer of connection
    if (mainWindow) {
      mainWindow.webContents.send('mcp:server-connected', {
        name: serverConfig.name,
        ...result
      });
    }

    return { success: true, ...result };
  } catch (error) {
    console.error('Error creating MCP client:', error);
    return { success: false, error: error.message };
  }
});

// Disconnect MCP client
ipcMain.handle('mcp:disconnect-client', async (event, clientId) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    await mcpManager.removeClient(clientId);

    // Notify renderer of disconnection
    if (mainWindow) {
      mainWindow.webContents.send('mcp:server-disconnected', { name: clientId });
    }

    return { success: true };
  } catch (error) {
    console.error('Error disconnecting MCP client:', error);
    return { success: false, error: error.message };
  }
});

// List tools for a client
ipcMain.handle('mcp:list-tools', async (event, clientId) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    const tools = await mcpManager.listTools(clientId);
    return { success: true, tools };
  } catch (error) {
    console.error('Error listing MCP tools:', error);
    return { success: false, error: error.message, tools: [] };
  }
});

// Call a tool
ipcMain.handle('mcp:call-tool', async (event, { clientId, toolName, args }) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    const result = await mcpManager.callTool(clientId, toolName, args || {});
    return { success: true, result };
  } catch (error) {
    console.error('Error calling MCP tool:', error);
    return { success: false, error: error.message };
  }
});

// List resources for a client
ipcMain.handle('mcp:list-resources', async (event, clientId) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    const resources = await mcpManager.listResources(clientId);
    return { success: true, resources };
  } catch (error) {
    console.error('Error listing MCP resources:', error);
    return { success: false, error: error.message, resources: [] };
  }
});

// Read a resource
ipcMain.handle('mcp:read-resource', async (event, { clientId, uri }) => {
  try {
    if (!mcpManager) {
      throw new Error('MCP Manager not initialized');
    }
    const result = await mcpManager.readResource(clientId, uri);
    return { success: true, result };
  } catch (error) {
    console.error('Error reading MCP resource:', error);
    return { success: false, error: error.message };
  }
});

// Check if client is connected
ipcMain.handle('mcp:is-connected', async (event, clientId) => {
  try {
    const connected = mcpManager ? mcpManager.isConnected(clientId) : false;
    return { success: true, connected };
  } catch (error) {
    return { success: false, error: error.message, connected: false };
  }
});

// ============= Application Lifecycle =============

// This method will be called when Electron has finished initialization
app.whenReady().then(async () => {
  await initMcpManager();
  createWindow();
});

// Quit when all windows are closed
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// Clean up MCP connections before quit
app.on('before-quit', async () => {
  if (mcpManager) {
    try {
      await mcpManager.disconnectAll();
    } catch (error) {
      console.error('Error disconnecting MCP clients on quit:', error);
    }
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});

// Handle IPC messages from renderer
ipcMain.handle('get-app-version', () => {
  return app.getVersion();
});

ipcMain.handle('get-platform', () => {
  return process.platform;
});
