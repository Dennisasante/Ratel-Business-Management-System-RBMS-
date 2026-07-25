const { app, BrowserWindow, ipcMain, shell, Menu } = require("electron");
const path = require("path");
const fs = require("fs");

const CONFIG_PATH = path.join(app.getPath("userData"), "config.json");

function readConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf-8"));
  } catch {
    return null;
  }
}

function writeConfig(config) {
  fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), "utf-8");
}

let mainWindow;
let allowedOrigin = null;

function normalizeUrl(input) {
  const trimmed = input.trim();
  const withProtocol = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  return new URL(withProtocol);
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.once("ready-to-show", () => mainWindow.show());

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (!allowedOrigin) return;
    const target = new URL(url);
    if (target.origin !== allowedOrigin) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  mainWindow.webContents.on("did-fail-load", (_event, errorCode, _desc, validatedURL, isMainFrame) => {
    if (!isMainFrame) return;
    const connectivityErrors = [-2, -6, -7, -21, -100, -101, -102, -105, -106, -109, -118, -130];
    if (connectivityErrors.includes(errorCode) || errorCode <= -100) {
      mainWindow.loadFile(path.join(__dirname, "offline.html"), {
        query: { target: validatedURL },
      });
    }
  });

  const config = readConfig();
  if (config && config.appUrl) {
    loadAppUrl(config.appUrl);
  } else {
    mainWindow.loadFile(path.join(__dirname, "setup.html"));
  }
}

function loadAppUrl(rawUrl) {
  const url = normalizeUrl(rawUrl);
  allowedOrigin = url.origin;
  mainWindow.loadURL(url.toString());
}

ipcMain.handle("ratel:get-config", () => readConfig());

ipcMain.handle("ratel:save-config", (_event, appUrl) => {
  const url = normalizeUrl(appUrl);
  writeConfig({ appUrl: url.toString() });
  loadAppUrl(url.toString());
  return true;
});

ipcMain.handle("ratel:reset-config", () => {
  try {
    fs.unlinkSync(CONFIG_PATH);
  } catch {
    // no config to remove — nothing to do
  }
  allowedOrigin = null;
  mainWindow.loadFile(path.join(__dirname, "setup.html"));
});

ipcMain.handle("ratel:retry", () => {
  const config = readConfig();
  if (config && config.appUrl) {
    loadAppUrl(config.appUrl);
  } else {
    mainWindow.loadFile(path.join(__dirname, "setup.html"));
  }
});

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
