# RBMS Backend (Spring Boot)

The API for the Ratel Business Management System. V1 foundation covers:
multi-tenant schema, business registration, JWT auth, and role-scoped users.

## How multi-tenancy works here

Every tenant-owned table has a `business_id` column. On every authenticated
request, `JwtAuthenticationFilter` reads the JWT, pulls out `business_id` and
`user_id`, and stores them in `TenantContext` (a thread-local) for that
request only. Controllers and repositories then read `TenantContext.getBusinessId()`
instead of trusting anything the client sends — so a user can never query another
business's data by passing a different id in the URL or body.

As you add modules (Inventory, Sales, Customers, Expenses...), every new
entity/table follows the same pattern: a `business_id` column, and repository
methods scoped by it (see `UserRepository.findAllByBusinessId` as the template).

## Requirements

- Java 17+
- Maven 3.9+ (or use your IDE's bundled Maven)
- Docker (for local Postgres) — or a Postgres 14+ instance you already have

## Run it locally

1. Start Postgres:
   ```bash
   docker compose up -d
   ```

2. (Optional) copy values from `.env.example` as real environment variables —
   the app runs fine on defaults for local dev without setting anything, except
   for Google Sign-In and the Super Admin, which need explicit setup (see the
   dedicated sections below).

3. Run the app:
   ```bash
   mvn spring-boot:run
   ```

   Flyway runs automatically on startup and creates the schema from
   `src/main/resources/db/migration/`.

4. The API is now on `http://localhost:8090`.

## Endpoints (V1)

| Method | Path                          | Auth? | Purpose                                   |
|--------|-------------------------------|-------|--------------------------------------------|
| POST   | `/api/auth/register`         | No    | Register a business + its owner in one go |
| POST   | `/api/auth/login`            | No    | Log in, get a JWT                         |
| GET    | `/api/business/me`           | Yes   | Current tenant's business details         |
| GET    | `/api/users`                 | Yes   | Users in the current tenant               |
| GET    | `/api/products`               | Yes   | List all products                         |
| GET    | `/api/products/low-stock`     | Yes   | Products at or below their threshold      |
| GET    | `/api/products/{id}`          | Yes   | Get one product                           |
| POST   | `/api/products`               | Yes   | Create a product (logs opening stock)     |
| PUT    | `/api/products/{id}`          | Yes   | Update product details (not quantity)     |
| POST   | `/api/products/{id}/stock`    | Yes   | Add / remove / adjust stock               |
| GET    | `/api/products/{id}/stock-history` | Yes | Full movement history for a product   |
| GET    | `/api/customers`              | Yes   | List customers (with computed spend/purchase count) |
| GET    | `/api/customers/{id}`         | Yes   | Get one customer                          |
| POST   | `/api/customers`               | Yes   | Create a customer                         |
| GET    | `/api/sales`                   | Yes   | List sales, most recent first, with items |
| GET    | `/api/sales/{id}`              | Yes   | Get one sale with items                   |
| POST   | `/api/sales`                    | Yes   | Record a sale — deducts stock automatically |
| GET    | `/api/expenses`                | Yes   | List expenses, most recent first          |
| GET    | `/api/expenses/{id}`           | Yes   | Get one expense                           |
| POST   | `/api/expenses`                 | Yes   | Log an expense                            |
| PUT    | `/api/expenses/{id}`           | Yes   | Update an expense                         |
| GET    | `/api/reports/summary`          | Yes   | Revenue/expenses/profit for a date range (`?from=&to=`, defaults to month-to-date) |
| POST   | `/api/auth/google/register`     | No    | Register a business using a Google account instead of a password |
| POST   | `/api/auth/google/login`        | No    | Log in with a Google account (matches by email) |
| GET    | `/api/activity-logs?userId=&from=&to=` | Yes | Your business's activity log — all params optional filters |
| POST   | `/api/business/logo`            | Owner/Manager | Upload/replace the business logo (PNG/JPEG/WEBP, max 3MB) |
| POST   | `/api/platform/auth/login`      | No    | Super Admin login (separate account, separate token type) |
| GET    | `/api/platform/businesses`      | Super Admin | Every business on the platform |
| GET    | `/api/platform/businesses/{id}` | Super Admin | One business's full detail + team |
| GET    | `/api/platform/activity-logs?businessId=&userId=&from=&to=` | Super Admin | Activity log across every business — all params optional filters |
| POST   | `/api/auth/forgot-password`     | No    | Request a password reset email (business users) |
| POST   | `/api/auth/reset-password`      | No    | Complete a reset using the emailed token |
| POST   | `/api/auth/change-password`     | Yes   | Self-service password change (needs current password) |
| POST   | `/api/users`                    | Owner/Manager | Add a staff account with a temporary password |
| PATCH  | `/api/users/{id}/status`        | Owner/Manager | Activate/deactivate a staff account |
| PATCH  | `/api/users/{id}/role`          | Owner | Change a staff member's role |
| POST   | `/api/platform/auth/forgot-password` | No | Super Admin password reset request |
| POST   | `/api/platform/auth/reset-password`  | No | Super Admin password reset completion |
| GET    | `/api/platform/businesses?query=&active=` | Super Admin | Search/filter businesses |
| PATCH  | `/api/platform/businesses/{id}/status` | Super Admin | Suspend/reactivate a business |
| DELETE | `/api/platform/businesses/{id}` | Super Admin | Permanently delete a business and everything in it |
| POST   | `/api/platform/businesses/{id}/users/{userId}/reset-password` | Super Admin | Reset any user's password (support cases) |
| GET    | `/api/platform/stats`           | Super Admin | Platform totals + 30-day signup/activity trends |
| GET    | `/api/platform/audit-logs`      | Super Admin | The Super Admin's own action history |
| PUT    | `/api/business/me`              | Owner/Manager | Edit business name/industry/location/contact |
| POST   | `/api/suppliers`                | Yes   | Add a supplier |
| GET    | `/api/suppliers`                | Yes   | List suppliers |
| POST   | `/api/purchase-orders`          | Yes   | Create a purchase order (no stock change yet) |
| GET    | `/api/purchase-orders`          | Yes   | List purchase orders |
| GET    | `/api/purchase-orders/{id}`     | Yes   | Get one purchase order |
| POST   | `/api/purchase-orders/{id}/receive` | Yes | Mark received — adds stock for every line item |
| POST   | `/api/purchase-orders/{id}/cancel`  | Yes | Cancel — no stock effect |
| GET    | `/api/sales/{id}/receipt`       | Yes   | PDF receipt for a sale |
| GET    | `/api/reports/commissions?from=&to=` | Yes | Per-staff commission breakdown for a date range |
| GET    | `/api/reports/export?from=&to=` | Yes   | Excel export (Summary/Sales/Expenses sheets) |
| PATCH  | `/api/users/{id}/commission-rate` | Owner | Set a staff member's commission rate |
| POST   | `/api/platform/admins`          | Super Admin | Create a second Super Admin (temporary password, same pattern as staff) |
| GET    | `/api/platform/admins`          | Super Admin | List all Super Admin accounts |
| POST   | `/api/platform/auth/change-password` | Super Admin (self) | Change your own password |

Authenticated requests need `Authorization: Bearer <token>`.

### Example: register a business

```bash
curl -X POST http://localhost:8090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "businessName": "Winamzua Creative Hive",
    "industry": "RETAIL",
    "location": "Accra, Ghana",
    "contactPhone": "+233200000000",
    "ownerFullName": "Ama Mensah",
    "email": "ama@winamzua.com",
    "password": "supersecret123"
  }'
```

The response includes a `token` — use it as the Bearer token for `/api/business/me`
and `/api/users`.

## Notes / things to know before extending this

- **Email uniqueness is per-business today** (`uq_users_business_email`), but
  login resolves by email alone (`UserRepository.findByEmail`), which assumes
  one business per person for V1. If you need one person managing multiple
  businesses, that's a real V2 change (login returns a business picker instead
  of a token directly).
- **`ddl-auto: validate`** — Hibernate never changes your schema. All schema
  changes go through a new Flyway migration file (`V2__...sql`, `V3__...sql`, etc).
  Never edit `V1__init_schema.sql` after it's been run anywhere.
- **JWT secret** — the default in `application.yml` is fine for local dev only.
  Set a real `JWT_SECRET` env var before deploying anywhere reachable.
- Role enum (`OWNER`, `MANAGER`, `SALES_PERSON`, `ACCOUNTANT`) matches the spec.
  Endpoint-level role restrictions (e.g. only `OWNER`/`MANAGER` can see `/api/users`)
  aren't wired up yet — that's a natural next step once you add more modules.
- **Stock movements** (`POST /api/products/{id}/stock`) take a `movementType` of
  `ADD`, `REMOVE`, or `ADJUST`, plus a `quantity`:
  - `ADD` / `REMOVE` treat `quantity` as a delta (e.g. "+20 units arrived",
    "-5 units sold"). `REMOVE` is rejected with a 400 if it would take stock
    negative.
  - `ADJUST` treats `quantity` as the new absolute count — use this after a
    physical stock take, when you know the real number but not the delta.
  - Every adjustment writes a row to `stock_movements` with the signed change
    and the resulting quantity, so `GET /api/products/{id}/stock-history` is a
    complete audit trail, not just a running total.
  - `quantity` on `Product` is intentionally not editable via `PUT /api/products/{id}`
    — it can only change through the stock endpoint, so it's never possible to
    silently move stock without a logged reason.
- **Sales** (`POST /api/sales`) take an optional `customerId` (walk-ins are
  fine — leave it out), a `paymentMethod`, and a list of `{productId, quantity}`
  items. The service:
  - Prices each line at the product's *current* `sellingPrice` — there's no
    price override yet, so a discount would need to happen at the product
    level or as a future field.
  - Deducts stock via the same `ProductService.adjustStock(..., REMOVE, ...)`
    used for manual adjustments, tagged with the sale number, so `GET
    /api/products/{id}/stock-history` shows sales and manual corrections in
    one unified timeline.
  - Runs as a single transaction — if any item has insufficient stock, the
    whole sale (and every stock deduction already made for earlier items in
    the same request) rolls back.
  - `saleNumber` is a DB-generated `BIGSERIAL`, global across all businesses
    (not per-tenant) — it's just a friendly display number, not something to
    branch logic on.
- **Customer spend/purchase count** are computed on read (summing that
  customer's sales), not stored as a running total on the row — simpler and
  avoids ever getting out of sync, at the cost of an extra query per customer
  per list call. Fine at this scale; worth revisiting if customer lists get
  into the thousands.
- **Expenses** default `expenseDate` to today if omitted, and are attributed
  to whoever's logged in (`recordedBy`) — matching the "Recorded by: Secretary"
  example in the spec.
- **Reports** (`GET /api/reports/summary?from=&to=`) sums `sales.total_amount`
  for revenue and `expenses.amount` for costs across the range, then
  `profit = revenue - expenses`. No params defaults to month-to-date. Sales
  are matched by `created_at` (a timestamp) and expenses by `expense_date` (a
  plain date) — both are treated as covering the full calendar days in the
  range, in UTC. This is a simple sum-based report, not pre-aggregated, so
  it'll want an index-backed rewrite (or a materialized view) if a business's
  sales volume gets large; `idx_sales_business_id` and
  `idx_expenses_business_date` cover it fine for now.

## Google Sign-In setup

The "Continue with Google" button won't work until you create a real OAuth
Client ID. Steps:

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → create a
   project (or use an existing one).
2. **APIs & Services → OAuth consent screen** — set it up as "External", fill
   in the app name (e.g. "Ratel"), your support email. You can leave it in
   "Testing" mode while developing; publishing is only needed once real users
   outside your test list need to sign in.
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Web application**
   - Authorized JavaScript origins: `http://localhost:3000` (add your real
     domain later, e.g. `https://app.yourdomain.com`)
   - You do **not** need to set an Authorized redirect URI — the frontend uses
     Google Identity Services' button/One Tap flow, which doesn't redirect.
4. Copy the **Client ID** (looks like `123456789-abc...apps.googleusercontent.com`).
   You don't need the Client Secret for this flow.
5. Set it in **both** places — they must match, or every Google token will
   fail verification since the backend checks the token's audience against
   its own copy:
   - Backend: `GOOGLE_CLIENT_ID` env var (see `application.yml`)
   - Frontend: `NEXT_PUBLIC_GOOGLE_CLIENT_ID` in `.env.local`

Until this is configured, the Google button quietly shows a "not configured"
message instead of breaking — email/password registration and login work
exactly as before, unaffected.

**How it works under the hood:** the frontend loads Google's own
`accounts.google.com/gsi/client` script and renders the real Google button.
When someone signs in, Google hands the frontend a signed ID token (a JWT) —
that token is sent to `/api/auth/google/register` or `/api/auth/google/login`,
where `GoogleTokenVerifier` checks its signature against Google's public keys
and confirms it was issued for your Client ID, before trusting the email
inside it. RBMS's backend never sees the person's Google password — only
Google does.

Accounts created this way have no `password_hash` (see `users.auth_provider`),
so they can only log in via Google going forward, not via `/api/auth/login`.

## Email (SMTP) setup

Password reset emails (and nothing else — staff onboarding uses a
directly-set temporary password, not email) need SMTP configured. This is
provider-agnostic: point it at anything that offers SMTP and it works,
no code changes needed.

**Recommended: [Brevo](https://www.brevo.com/)** — free tier includes 300
emails/day forever, which comfortably covers password resets at this scale,
and it offers a plain SMTP relay (no vendor SDK needed). Sign up, go to
**SMTP & API → SMTP**, and you'll get a host, port, login, and password to
use below. SendGrid, Mailgun, or even Gmail's SMTP relay work exactly the
same way if you'd rather use one of those.

```bash
SMTP_HOST=smtp-relay.brevo.com   # or your provider's SMTP host
SMTP_PORT=587
SMTP_USERNAME=your-smtp-login
SMTP_PASSWORD=your-smtp-password
MAIL_FROM=no-reply@yourdomain.com
FRONTEND_URL=http://localhost:3000   # used to build the links inside reset emails
```

Until these are set, `SMTP_HOST` is blank by default — password reset
requests still succeed (so the frontend behaves normally), but no email
actually goes out; the backend logs `[RBMS] Email not sent (SMTP not
configured)` instead. Good for local dev, not something to leave unset once
real users are relying on password reset.

## Super Admin (platform-level access)

The Super Admin is deliberately **not** a business user with extra
permissions — it's a separate account type (`platform_admins` table), with
its own login endpoint, its own JWT type, and its own frontend area
(`/platform/*`), completely outside the tenant model. It can see every
business and every activity log entry across the whole platform.

**There is no public sign-up route for this, on purpose.** The only way to
create a Super Admin is via environment variables on startup:

```bash
SUPER_ADMIN_EMAIL=you@yourdomain.com
SUPER_ADMIN_PASSWORD=some-long-random-password
SUPER_ADMIN_NAME=Dennis Asante   # optional, defaults to "Super Admin"
```

Set these, start the backend once, and check the startup logs for
`[RBMS] Super Admin account created for ...`. After that, **unset them again**
— the seeder is a no-op once an admin exists (it checks `platform_admins`
first), so leaving the env vars set is harmless but unnecessary. If you ever
need a second Super Admin account, you'd currently add it directly in the
database (`platform_admins` table, bcrypt-hash the password) — there's no
API for creating additional ones yet, again on purpose.

Log in at `/platform/login` on the frontend (not `/login` — that's for
business accounts and deliberately doesn't know Super Admin accounts exist).

## Rate limiting / brute-force protection

`RateLimiterService` is a simple in-memory sliding-window limiter, applied to:
- `/api/auth/login` and `/api/platform/auth/login` — 5 attempts per 15
  minutes, keyed by email (not IP). A wrong password records an attempt; a
  successful login resets the counter, so a few typos followed by success
  doesn't leave the account half-throttled.
- `/api/auth/forgot-password` and `/api/platform/auth/forgot-password` — 3
  requests per hour per email, so this can't be used to spam someone's inbox.

**This is per-instance, in-memory state** — fine for the single-server VPS
deployment this project is built around, but if this ever runs across
multiple instances behind a load balancer, each instance tracks attempts
independently and the effective limit becomes `instances × maxAttempts`. If
that happens, this needs to move to shared state (Redis). IP-based
throttling (as opposed to the per-email limiting here) is arguably better
handled at the reverse-proxy level (Nginx, Cloudflare) in front of this
rather than duplicated in the app.

## Business profile editing

`PUT /api/business/me` (Owner/Manager) updates name, industry, location, and
contact info. Currency and subscription plan aren't editable here — those
are platform-level concerns, not something a business self-serves.

## Suppliers & Purchase Orders

Mirrors the Sales pattern almost exactly, but in reverse: a Purchase Order
starts as `PENDING` with no stock effect, and only adds stock (via the same
`ProductService.adjustStock(..., ADD, ...)` used everywhere else) once marked
`RECEIVED` — so a PO you're still waiting on doesn't inflate your on-hand
count. `CANCELLED` is the other terminal state; both are one-way — there's
no "un-receive" or "un-cancel," matching how Sales don't support editing
after the fact either.

If a product or supplier referenced by an old PO is later deleted, the PO
itself still displays fine (`product_name` is snapshotted on each line item,
same as `sale_items`; a missing supplier just shows as absent rather than
erroring).

## Staff commissions

`users.commission_rate` is a percentage (0-100), settable only by an Owner
(`PATCH /api/users/{id}/commission-rate`). The important design choice:
**`sales.commission_amount` is calculated and stored at the moment the sale
is created**, from whatever the cashier's rate was *right then* — it is
never recalculated later. If you raise or lower someone's rate next month,
last month's sales still show the commission they actually earned under the
old rate. `GET /api/reports/commissions?from=&to=` sums these snapshotted
amounts per staff member for a date range — it does not multiply
today's rate by historical sales, which would silently rewrite payroll
history every time a rate changes.

## PDF receipts & Excel exports

Both are generated on-demand, not stored:
- `GET /api/sales/{id}/receipt` — `ReceiptService` builds a simple A5 PDF
  via OpenPDF (business name, line items, total, payment method). Not
  trying to be a fully branded invoice template — just something printable
  or forwardable.
- `GET /api/reports/export?from=&to=` — `ReportExportService` builds an
  `.xlsx` via Apache POI with three sheets: Summary, Sales (one row per
  sale, items summarized as text), Expenses.

Both stream bytes directly in the response rather than writing to disk —
nothing to clean up, nothing that needs the same local-storage caveat as
uploaded logos.

## Second Super Admin

`POST /api/platform/admins` — an existing Super Admin creates another,
exactly like staff creation: a temporary password set directly, and the new
admin is forced to change it on first login (`must_change_password`, same
column pattern as `users`). `GET /api/platform/admins` lists everyone with
platform access, so it's always visible who else can see everything.
`POST /api/platform/auth/change-password` is the self-service counterpart
(needs the current password), used both for that forced first change and
for any admin changing their own password later.

There's still no way to *remove* a Super Admin's access through the API —
that's a deliberate gap, not an oversight, since getting account removal
wrong (e.g. an admin locking out the only other admin) is a worse failure
mode than the inconvenience of doing it directly in the database for now.

## Staff accounts & role hierarchy

`POST /api/users` lets an Owner or Manager add a teammate directly, with a
temporary password they set and relay out-of-band (WhatsApp, in person —
there's no invite email for this, by design, since the person creating the
account is already in the room or on the phone with the new hire). The new
account has `mustChangePassword = true`, so the frontend forces them to a
"set your own password" screen right after their first login, before they
can touch anything else.

The hierarchy (`UserManagementService.assertCanManage`) is stricter than
just "is this an Owner or Manager":

- **Owner** can create/edit/deactivate anyone, including other Owners and Managers.
- **Manager** can create/edit/deactivate `SALES_PERSON` and `ACCOUNTANT`
  accounts only — never `OWNER` or `MANAGER` accounts, including their own role.
- Nobody — not even an Owner — can deactivate or demote the business's
  **last remaining Owner**. That guard exists so a business can never
  accidentally lock itself out of its own account.

Role changes (`PATCH /api/users/{id}/role`) are Owner-only, full stop —
Managers can create Sales/Accountant staff but can't grant anyone a more
powerful role than their own.

## Password recovery

Three flows, all built on the same pattern (a random 256-bit token in a
short-lived DB row, emailed as a link, single-use):

1. **Business users**: `POST /api/auth/forgot-password` → email →
   `POST /api/auth/reset-password`. Always responds the same way whether or
   not the email exists, to avoid leaking which addresses have accounts.
2. **Super Admin**: identical shape, separate tables
   (`platform_admin_reset_tokens`), separate endpoints under `/api/platform/auth/`.
3. **Forced change** (temporary passwords): not a "forgot password" flow at
   all — the person is already authenticated (they logged in with the temp
   password), so `POST /api/auth/change-password` just asks for that current
   password plus a new one, and clears `mustChangePassword`.

All three require `SMTP_HOST` to be set (see `.env.example`) or emails
silently no-op with a log line instead of failing the request — see
`EmailService`.

## Activity Log vs. Admin Actions — two different audit trails

- **`activity_logs`** (`GET /api/activity-logs`, business-scoped) records
  what *business users* do: sales, stock changes, expenses, staff changes,
  logins. It's cascade-deleted along with its business — that's correct,
  since it's that business's own history.
- **`platform_audit_logs`** (`GET /api/platform/audit-logs`, Super Admin
  only) records what *the Super Admin* does *to* the platform: suspending,
  deleting, or resetting a password on someone's behalf. This table has no
  FK cascade to `businesses` on purpose — if you delete a business, the
  record that you deleted it needs to survive that deletion, so
  `business_id` is a plain column plus a `business_name_snapshot`, not a
  live foreign key.

## Business logo storage

Uploaded logos are saved to disk under `UPLOAD_DIR/logos/{businessId}.{ext}`
(default `UPLOAD_DIR` is `./uploads`, relative to wherever the JAR runs) and
served back publicly at `/uploads/logos/...` — no auth required to view one,
same as any other image asset, since it needs to render in `<img>` tags.
Uploading is restricted to Owners/Managers.

**This is local-disk storage, not object storage.** Fine for a single-server
deployment (which is exactly what the Hostinger VPS plan in this project's
own notes describes), but it will NOT survive a redeploy on ephemeral/
multi-instance hosting (Heroku-style dynos, some container platforms) — the
files just vanish. If you ever move to that kind of hosting, this needs to
become S3-compatible object storage instead (Cloudflare R2 and
DigitalOcean Spaces both work as drop-in S3 API replacements). Worth
knowing now, not worth building until it's actually needed.

Make sure `UPLOAD_DIR` is a path that survives backend restarts and is
included in whatever backup strategy you use for the server — it's real
user data, same as the database.

## Daily digest emails

`DigestScheduler` runs once a day (`app.digest.cron`, default `0 0 6 * * *` —
6:00 AM UTC, which is 6:00 AM in Ghana with zero offset needed) and emails
every active business's active Owner(s) a plain-facts summary of the
previous day: sales count + revenue, expenses count + total, net for the
day, new customers, and any products at/below their low-stock threshold.

Deliberately **not** AI-generated — every number is a direct repository
query result, so it's always exactly accurate and costs nothing to run. If
a business has no Owners (shouldn't normally happen) or is suspended, it's
skipped silently. Needs `SMTP_HOST` configured to actually deliver (see
"Email (SMTP) setup" above) — without it, the digest still "runs" but each
email just gets logged instead of sent.

There's no per-Owner opt-out yet — every active Owner gets it every day.
Worth adding a preference toggle if that turns out to matter, but not
built now to keep this simple.
