const { contextBridge, ipcRenderer } = require('electron');

// Expose protected methods that allow the renderer process to use
// the ipcRenderer without exposing the entire object
contextBridge.exposeInMainWorld('electronAPI', {
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  getPlatform: () => ipcRenderer.invoke('get-platform'),
  isElectron: true
});

// Notify the renderer that we're running in Electron
window.addEventListener('DOMContentLoaded', () => {
  // Add a class to body to indicate we're in Electron
  document.body.classList.add('electron-app');
});
