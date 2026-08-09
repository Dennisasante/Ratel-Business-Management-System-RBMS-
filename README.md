# Ratel Business Management System (RBMS)

Multi-tenant business management platform — one system, many businesses
(Salon, Winamzua, Kobby's Hub, and whoever comes next), each isolated by
`business_id`.

```
ratel/
├── backend/    Spring Boot API (Java 17, PostgreSQL, JWT auth)
└── frontend/   Next.js 14 web client (TypeScript, Tailwind)
```

## What's built — the full V1 MVP

- Multi-tenant Postgres schema (`businesses`, `users`, `activity_logs`,
  `products`, `stock_movements`, `customers`, `sales`, `sale_items`,
  `expenses`) via Flyway
- Business registration (business + Owner account created together)
- JWT-based login
- Tenant isolation enforced server-side: every authenticated request resolves
  `business_id` from the JWT into a thread-local `TenantContext`, so no
  endpoint ever trusts a business id coming from the client
- Role model: `OWNER`, `MANAGER`, `SALES_PERSON`, `ACCOUNTANT`
- A working dashboard showing the current business + its team
- **Inventory**: add/edit products, add/remove/set-exact stock with a full
  movement history, low-stock flagging
- **Sales / POS**: a cart-based sale flow that deducts stock automatically
  (through the same audited stock-movement path Inventory uses), optional
  walk-in or linked customer, payment method, and a running sales history
- **Customers (lightweight CRM)**: customer records with lifetime spend and
  purchase count computed from their sales
- **Expenses**: category, amount, date, who recorded it
- **Financial Reports**: revenue (from Sales) / expenses / profit for any
  date range, defaulting to month-to-date
- **Google Sign-In**: register or log in with a Google account instead of a
  password — verified server-side against Google's own keys, no password ever
  touches RBMS for these accounts
- **Activity Log**: every key action (sales, stock changes, expenses, new
  customers, logins) is recorded. Business Owners see their own team's log;
  the Super Admin sees everything, across every business
- **Super Admin (platform-level)**: a separate account type outside the
  tenant model entirely — its own login (`/platform/login`), own dashboard,
  full visibility into every business. No public sign-up route; created only
  via env vars on first startup (see backend README)
- **Staff management**: Owners/Managers add teammates directly with a
  temporary password (forced change on first login); a role hierarchy keeps
  Managers from creating or touching Owner/Manager accounts
- **Password recovery**: email-based reset for business users and the Super
  Admin alike (SMTP, provider-agnostic — see backend README)
- **Super Admin operations**: suspend/reactivate or permanently delete a
  business, search/filter the business list, reset any user's password for
  support calls, platform growth stats, and a dedicated Admin Actions log
  that survives even if the business it refers to is later deleted
- **Activity Log filters**: filter by staff member and date range, on both
  the business-scoped log and the Super Admin's platform-wide one
- **Business logo**: upload directly from a phone gallery/camera, shown in
  the sidebar and business profile — no external storage service required
  for a single-server deployment (see backend README for the caveat if you
  ever move off one)
- **Daily digest emails**: every active business's Owner(s) get a plain-facts
  morning summary — sales, expenses, net, new customers, low stock — so
  "how's the shop doing" never requires logging in to find out
- **Rate limiting**: login and password-reset endpoints are throttled against
  brute-force/credential-stuffing (business, Google, and Super Admin alike)
- **Business profile editing**: name, industry, location, and contact info,
  editable by Owners/Managers after registration
- **Suppliers & Purchase Orders**: a restocking workflow that mirrors Sales —
  a PO adds no stock until marked received, so orders still in transit don't
  inflate your on-hand count
- **Staff commissions**: a per-staff percentage rate, snapshotted onto each
  sale at the moment it's made — so a later rate change never rewrites what
  someone already earned
- **PDF receipts & Excel exports**: a printable receipt for any sale, and a
  three-sheet Excel export (Summary/Sales/Expenses) for any report date range
- **Second Super Admin**: an existing Super Admin can add another, same
  temporary-password pattern as staff — no public sign-up route, ever
- **httpOnly cookie auth**: the JWT never touches client-side JS. Login/register
  route through Next.js server-side handlers (`/session/*`) that set an
  httpOnly cookie; `middleware.ts` forwards it to the backend as the
  Authorization header on every request. See "How auth works here" in the
  frontend README for the full flow.

Every item marked ✅ in the original spec's "Version 1 (MVP)" roadmap is done,
plus the platform-operations layer (Google Sign-In, staff management,
password recovery, Activity Log, Super Admin) needed to actually run this as
a real multi-tenant product with real users.

## Quick start

```bash
# 1. Backend
cd backend
docker compose up -d        # starts Postgres
mvn spring-boot:run         # http://localhost:8090

# 2. Frontend (new terminal)
cd frontend
npm install
npm run dev                 # http://localhost:3000
```

Then visit `http://localhost:3000/register` to create your first business.

## Deploying to production

See [`DEPLOY.md`](DEPLOY.md) — a single VPS running the whole stack
(Postgres, backend, frontend, and Caddy for automatic HTTPS) via
`docker-compose.prod.yml`.

To set up the Super Admin account (recommended before inviting anyone else),
see "Super Admin (platform-level access)" in `backend/README.md` — it's a
one-time env var setup, then log in separately at `http://localhost:3000/platform/login`.

## Where this goes next

V1 (from the spec's own roadmap) is complete, plus everything built since:
Google Sign-In, Activity Log with filters, Super Admin (with a second-admin
path and httpOnly cookie auth), staff management with commissions, password
recovery, business profile editing and logos, daily digest emails, rate
limiting, Suppliers/Purchase Orders, and PDF/Excel exports. What's left,
roughly in priority order:

1. **WhatsApp notifications** — needs a paid API (Twilio or Meta's Cloud
   API) and business account verification, a bigger commitment than the free
   email digest already covers.
2. **Digest opt-out / preferences** — every active Owner gets the daily
   digest with no way to turn it off; a per-user notification preference
   would need a small schema addition.
3. **Removing a Super Admin's access** — creating a second admin is built;
   revoking one isn't, on purpose (see "Second Super Admin" in the backend
   README for why that's a deliberate gap, not an oversight).
4. **Object storage for logos** — fine on local disk for a single-server
   VPS, but won't survive ephemeral/multi-instance hosting if this ever
   moves off one.

Each new module should follow the pattern already in place: a `business_id`
column on the table, a Flyway migration (`V8__...`, never editing `V1`-`V7`),
and repository methods scoped by `TenantContext.getBusinessId()`. `SaleService`
and `PurchaseOrderService` are the fullest examples to copy for anything
transactional — between them they show the audit-log pattern via
`stock_movements`, snapshotting values that shouldn't change retroactively
(commission amounts, product names on line items), and multi-table creates
wrapped in one `@Transactional` method.
