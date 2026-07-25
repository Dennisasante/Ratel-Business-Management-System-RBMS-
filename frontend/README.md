# RBMS Frontend (Next.js)

The web client for the Ratel Business Management System. V1 covers business
registration, login, and a dashboard showing the current business + its team.

## Run it locally

1. Make sure the backend is running first (see `../ratel-backend/README.md`).

2. Install deps:
   ```bash
   npm install
   ```

3. (Optional) copy `.env.example` to `.env.local` if your backend isn't on
   `http://localhost:8080`.

4. Run the dev server:
   ```bash
   npm run dev
   ```

5. Open `http://localhost:3000`.

## How auth works here

The raw JWT never touches client-side JS — this was a known V1 shortcut
(localStorage) that's now fixed. Here's the actual flow:

- **Login/register/Google auth go through `/session/*` route handlers**
  (`app/session/login/route.ts`, `register`, `google-login`, `google-register`,
  `logout`, and the `platform-*` equivalents for Super Admin), not directly to
  the backend. Each one calls the real backend endpoint server-side, then
  strips the `token` field out of the response before sending it to the
  browser — the token goes into an **httpOnly cookie** instead
  (`lib/cookies.ts` has the cookie names), set right there in the route
  handler's response. Client JS never sees it, so an XSS payload can't steal it.
- **`middleware.ts`** (project root) is what makes the rest of the app work
  unchanged: every request to `/api/*` or `/uploads/*` gets intercepted,
  the httpOnly cookie is read server-side, and it's forwarded to the backend
  as the `Authorization: Bearer <token>` header the backend actually expects
  — the backend's auth contract didn't change at all, only how the frontend
  carries the token to it.
- **`lib/auth.tsx` / `lib/platformAuth.tsx`** still keep a `localStorage`
  copy of the session — but it's just display info now (name, role,
  `mustChangePassword`, etc.), never the token. There's a vestigial `token`
  field kept on the stored session object purely so the ~30 existing
  `api.xxx(session.token, ...)` call sites across the app didn't all need
  editing; it's never a real secret and nothing reads it as one. `lib/api.ts`'s
  `request()`/`downloadFile()` helpers accept but ignore that param for the
  same reason — real auth is the cookie, forwarded automatically by the
  browser on every same-origin request.
- **Logout calls `/session/logout`** (or `/session/platform-logout`) before
  clearing local state — client JS can't delete an httpOnly cookie on its
  own, so a server response has to do it.

If you're adding a new authenticated flow that issues its own token (there
isn't one today beyond login/register), route it through a new `/session/*`
handler following the same pattern — never return a raw token directly to
a client component.

## Design system

The UI is a proper admin shell now, not standalone pages: a persistent dark
sidebar (desktop) / off-canvas drawer (mobile) via `components/shell/`, and a
light content canvas built from reusable primitives in `components/ui/`
(`Card`, `Badge`, `Button`, `Table`, `StatCard`, `PageHeader`, `EmptyState`).

- **Colors** are Tailwind tokens in `tailwind.config.ts` — `canvas`/`surface`
  for backgrounds, `ink-*` for text, `sidebar-*` for the dark shell,
  `accent` (amber-gold) as the one bold color, plus `success`/`danger`/`info`
  for status. Don't reach for raw hex values or Tailwind's default palette
  (`gray-500`, `blue-600`, etc.) in new pages — extend the token set instead
  so the palette stays consistent.
- **Font** is Inter, self-hosted via `@fontsource/inter` (imported in
  `app/layout.tsx`) rather than `next/font/google` — this avoids a
  network fetch at build time, which matters if you're ever building in an
  environment without internet access.
- **Icons** are `lucide-react`, used at `size={16}`–`size={20}` with
  `strokeWidth={1.75}` for a slightly lighter weight than the default.
- New dashboard pages should live under `app/dashboard/` — the layout there
  (`app/dashboard/layout.tsx`) automatically wraps them in the sidebar/topbar
  shell, so a new page just needs its own content starting with
  `<PageHeader ... />`, no `<main>` wrapper or back-link needed.

## Google Sign-In

The "Continue with Google" button on `/login` and `/register` needs
`NEXT_PUBLIC_GOOGLE_CLIENT_ID` set in `.env.local` (see backend README for how
to get one). Without it, the button shows a small "not configured" note
instead of breaking — email/password auth is unaffected either way.

`components/GoogleButton.tsx` loads Google's own Identity Services script on
demand and hands back a signed ID token, which gets sent to
`/api/auth/google/register` or `/api/auth/google/login` for the backend to
verify. The frontend never sees a Google password — only the token.

## Super Admin area

`/platform/*` is a completely separate part of the app — its own auth context
(`lib/platformAuth.tsx`, storage key `rbms_platform_session`, entirely
independent from the business `lib/auth.tsx`), own login page
(`/platform/login`), own shell (`components/platform/PlatformShell.tsx`).
There's no link to it anywhere in the business-facing UI on purpose — see
"Super Admin (platform-level access)" in `backend/README.md` for how the
account itself gets created.

## Password reset & forced password change

- `/forgot-password` and `/reset-password` (business), `/platform/forgot-password`
  and `/platform/reset-password` (Super Admin) — standard email-link reset flow.
- `/dashboard/change-password` doubles as both self-service (via the Topbar
  user menu) and the forced screen staff see when they log in with a
  temporary password. `components/shell/AppShell.tsx` checks
  `session.mustChangePassword` on every route change and redirects there
  until it's cleared — nothing else in `/dashboard/*` is reachable until then.

## Team / staff management

`/dashboard/team` — visible to everyone, but only Owners/Managers see the
"Add staff" button and management controls (client-side gating for UX; the
backend enforces the real permission boundary regardless, see
`UserManagementService` in the backend README). `components/StaffForm.tsx`
includes a one-click temporary password generator.

## Super Admin area

`/platform/*` is a completely separate part of the app — its own auth context
(`lib/platformAuth.tsx`, storage key `rbms_platform_session`, entirely
independent from the business `lib/auth.tsx`), own login page
(`/platform/login`), own shell (`components/platform/PlatformShell.tsx`).
There's no link to it anywhere in the business-facing UI on purpose — see
"Super Admin (platform-level access)" in `backend/README.md` for how the
account itself gets created.

From there: **Overview** (platform totals + 30-day signup/activity charts via
a small hand-rolled SVG bar chart, `components/ui/MiniBarChart.tsx` — not a
charting library, to keep the bundle light for two simple bar charts),
**Businesses** (search/filter, suspend/reactivate, delete-with-typed-confirmation,
per-user password reset), **Activity Log** (cross-tenant business activity),
and **Admin Actions** (a separate log of what the Super Admin itself has done
— see the backend README's "Activity Log vs. Admin Actions" section for why
these are deliberately two different tables).

## Business logo & shared business state

`lib/auth.tsx` now fetches `GET /api/business/me` automatically whenever a
session is set, and exposes both `business` and `refreshBusiness()` from
`useAuth()`. The Sidebar and the Dashboard home page both read `business`
from this shared context rather than each fetching it separately — so
uploading a new logo on the Dashboard page and calling `refreshBusiness()`
updates the Sidebar too, with no extra plumbing.

Logo upload itself (`api.uploadBusinessLogo`) is a plain `FormData` POST —
it doesn't go through the typed `request()` helper the rest of the API
client uses, since that helper always sets `Content-Type: application/json`,
which breaks multipart uploads.

## Activity Log filters

Both `/dashboard/activity` (business) and `/platform/activity` (Super Admin)
now have staff-member and date-range filters, backed by real query params on
the backend (not client-side filtering of an already-capped list — see
`ActivityLogRepository.search` in the backend README for why that
distinction matters). The platform page's business/staff dropdown options
are populated from one unfiltered pull on mount, separate from the filtered
results shown in the list — so the dropdowns don't shrink to only what's
currently visible as you filter.

## What's here vs. what's next

Built: registration (business + owner in one step), login, protected
dashboard showing business info + team list.

Not built yet (natural next steps, matching the roadmap in the spec doc):
route guards beyond the dashboard's own redirect, an actual layout/nav shell
once there's more than one page behind login, and UI for the first real
module (Inventory is the natural next one to build against the same
`business_id`-scoped pattern the backend already uses).
