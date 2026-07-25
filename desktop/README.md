# Ratel Desktop

A thin Electron shell around the hosted Ratel web app. It's for shop computers that
should feel like they're running "the Ratel app" instead of a browser tab — no
offline database, no sync engine, just a dedicated window pointed at your
Hostinger-hosted URL, with graceful handling when the connection drops.

## What this is (and isn't)

- **Is:** an installable/portable desktop window wrapping your hosted Ratel site.
  Login, sessions, and data all work exactly as they do in a browser, because it
  *is* a browser under the hood (Chromium via Electron).
- **Isn't:** an offline-first app. If the shop's internet is down, the app can't
  reach the server, same as a browser tab would fail. It shows a friendly
  "can't reach your account" screen with a retry button instead of a browser
  error page — that's the extent of the "offline" handling here.
- Staff can still use the regular web app in any browser when away from the
  shop computer; this shell is just a convenience for the in-shop machine.

## First run on a shop computer

1. Copy `RatelRBMS-Portable-<version>.exe` (or run the installer) onto the
   shop's computer — from a pendrive, email, or however you distribute it.
2. Launch it. On first run it asks for the business's Ratel URL (the same
   address used to log in from a browser) and remembers it from then on
   (stored per-Windows-user, not inside the exe itself).
3. From then on, launching the app goes straight to the login screen.

To point the app at a different URL later (e.g. moved hosting), use the
"Change business URL" link that appears on the offline screen, or delete the
app's config folder (`%APPDATA%/ratel-desktop`) and relaunch.

## Development

```bash
npm install
npm start
```

## Building the distributable

```bash
npm install
npm run dist
```

Outputs land in `release/`:
- `RatelRBMS-Portable-<version>.exe` — single file, no install, ideal for a
  pendrive handoff.
- `RatelRBMS-Setup-<version>.exe` — a normal Windows installer, for shops that
  want a Start Menu shortcut instead.

## Icon

No app icon is bundled yet — electron-builder will fall back to its default.
To brand it, drop a `256x256`+ `assets/icon.ico` next to `main.js` and add
`"icon": "assets/icon.ico"` under `build.win` in `package.json`.
