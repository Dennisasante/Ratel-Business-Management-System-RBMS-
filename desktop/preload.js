const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("ratel", {
  getConfig: () => ipcRenderer.invoke("ratel:get-config"),
  saveConfig: (appUrl) => ipcRenderer.invoke("ratel:save-config", appUrl),
  resetConfig: () => ipcRenderer.invoke("ratel:reset-config"),
  retry: () => ipcRenderer.invoke("ratel:retry"),
});
