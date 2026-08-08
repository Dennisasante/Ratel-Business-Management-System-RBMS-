# RBMS v6 — Booking Scheduling Robustness, Role-Based Dashboards, Hosted Booking Pages, Branding

Scoped against the actual codebase. Confirmed via direct audit (not assumed): roles today are `OWNER`, `MANAGER`, `SALES_PERSON`, `ACCOUNTANT` (`entity/enums/Role.java`) with no DB constraint on the column — a new role is a pure code change. `ServiceOrderController` has zero `@PreAuthorize` today, so every role currently sees every order. `ServiceCatalogItem` has no duration/capacity field. No working-hours/holiday concept exists anywhere. `Business` has no `slug`. `PoweredByRatel.tsx` only appears in the sidebar footer and printed receipts — never on customer-facing booking pages.

**Confirmed decisions:**
- **New `STAFF` role**, distinct from the existing four. `MANAGER` is reframed in the UI as "Administrator" — no permission change needed, it already manages everyone below `OWNER` and already sees everything except billing/integrations (already `OWNER`-gated). `SALES_PERSON`/`ACCOUNTANT` are untouched.
- **`STAFF` is scoped**: can only see/act on service orders where `assignedStaffId` is their own user ID. `OWNER`/`MANAGER` are unrestricted, matching today's behavior.
- **Sidebar trims per role** — `STAFF` sees Dashboard, Service Orders (scoped), Calendar (scoped), Customers (read-only), Profile. Hidden: Inventory, Sales, Expenses, Suppliers, Purchase Orders, Reports, Team, Billing, Integrations.
- **Scheduling rules are business-wide, not per-staff**, for this pass — one working-hours window, one holiday list, one duration/capacity per service. Per-staff availability (so a customer eventually picks a specific stylist) is a real fast-follow, not this pass — it needs a second, larger data model (staff calendars) that isn't worth guessing at yet.
- **Capacity/duration lives on `service_catalog`**, checked at booking-creation time by counting overlapping non-cancelled bookings for that service — this is the actual double-booking fix.
- **Hosted booking page** at `/book/{slug}` — a native React page (not the embeddable widget) for businesses with no website of their own to embed on. Same public API, same visual language as the widget.
- **Coupons and marketing-campaign automation are explicitly deferred** — real ideas, but separate subsystems from what's broken today. Noted in section 4.
- **"Powered by Ratel Systems"** (name correction) gets added to the booking widget and manage-booking page footers, not just the sidebar/receipts.

## 1. Database — `V14__scheduling_roles_slug.sql`

```sql
-- Scheduling robustness on the catalog item itself.
ALTER TABLE service_catalog ADD COLUMN duration_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE service_catalog ADD COLUMN max_concurrent_bookings INT NOT NULL DEFAULT 1;

-- Business-wide working hours — one window, all days configurable on/off.
-- (Split shifts / different hours per day is a fast-follow if actually needed.)
ALTER TABLE business_integrations ADD COLUMN working_days SMALLINT[] NOT NULL DEFAULT '{1,2,3,4,5,6}'; -- ISO weekday, 1=Mon..7=Sun; default excludes Sunday
ALTER TABLE business_integrations ADD COLUMN working_hours_start TIME NOT NULL DEFAULT '09:00';
ALTER TABLE business_integrations ADD COLUMN working_hours_end TIME NOT NULL DEFAULT '18:00';

CREATE TABLE business_blackout_dates (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    date         DATE NOT NULL,
    label        VARCHAR(100), -- "Christmas", "Staff retreat", nullable
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_blackout_dates UNIQUE (business_id, date)
);
CREATE INDEX idx_business_blackout_dates_business_id ON business_blackout_dates(business_id);

-- Hosted booking page for businesses without their own website.
ALTER TABLE businesses ADD COLUMN slug VARCHAR(80) UNIQUE;
-- Backfilled from name (lowercased, hyphenated, deduped with a numeric suffix on collision)
-- in a one-off migration step; owner can edit it afterward from Business Profile.
```

No migration needed for the `STAFF` role — `users.role` is an unconstrained `VARCHAR(30)`.

## 2. Backend

**Roles**
- `entity/enums/Role.java` — add `STAFF`.
- `service/UserManagementService.java` — extend the existing hierarchy comment/logic: `MANAGER` can also manage `STAFF` (same tier as `SALES_PERSON`/`ACCOUNTANT` today).
- `service/ServiceOrderService.java` — every read/write method gains a scoping check: if `TenantContext.getRole() == STAFF`, filter/verify `assignedStaffId == TenantContext.getUserId()`; else unchanged. `list()` adds the filter at the query level (`ServiceOrderRepository.search` gets an optional `assignedStaffId` param); `get()`/`update()`/`updateStatus()` throw 404 (not 403 — don't leak existence) if a STAFF user targets an order that isn't theirs.
- `controller/ServiceOrderController.java` — no route changes; the scoping lives in the service layer so it can't be bypassed by hitting the controller directly.

**Scheduling**
- `entity/ServiceCatalogItem.java` — add `durationMinutes` (int, default 30), `maxConcurrentBookings` (int, default 1).
- `entity/BusinessIntegrations.java` — add `workingDays` (`List<Integer>`, `@JdbcTypeCode(SqlTypes.ARRAY)`), `workingHoursStart`/`workingHoursEnd` (`LocalTime`).
- `entity/BusinessBlackoutDate.java` (new) + `repository/BusinessBlackoutDateRepository.java`.
- `service/BusinessIntegrationsService.java` — extend `update()` with the same "if present, set" pattern for the three new fields; add `addBlackoutDate`/`removeBlackoutDate`.
- `service/BookingService.java#createBooking` — before creating the `ServiceOrder`, run in order: (1) weekday check against `workingDays`, (2) time-of-day check against `workingHoursStart/End`, (3) blackout-date check, (4) capacity check — count `service_orders` for this `serviceCatalogId` with status not `CANCELLED` whose `[scheduledAt, scheduledAt + durationMinutes)` overlaps the requested window; reject with a clear message ("That time is fully booked — please choose another slot") if the count is already at `maxConcurrentBookings`. Each check throws `ApiException(BAD_REQUEST, ...)` with a customer-readable reason.
- `dto/BookingWidgetConfigResponse.java` — add `workingDays`, `workingHoursStart`, `workingHoursEnd` so the widget can grey out invalid picks client-side (server stays the authoritative enforcement either way).
- Existing service-catalog CRUD (`ServiceCatalogService`/`ServiceCatalogItemRequest`/`Response`) gains the two new fields — same pattern as `bookableOnline` did.

**Hosted booking page**
- `Business.java` — add `slug`.
- `service/BusinessService.java` — slug generation on business creation (slugify name, append `-2`/`-3` on collision) + validated manual edit (lowercase, alphanumeric+hyphen only, uniqueness check) from Business Profile.
- `controller/PublicBookingController.java` — new `GET /api/public/bookings/by-slug/{slug}/widget-config`, mirroring the existing `widget-config` endpoint but resolving `businessId` from `slug` first. Every other public booking endpoint keeps taking `businessId` as today — the hosted page resolves the ID once on load and reuses it, so no need to duplicate every endpoint.

## 3. Frontend

- `components/shell/Sidebar.tsx` — `NAV_ITEMS` gains a `visibleTo?: Role[]` field (omitted = everyone); filter logic extends the existing `ownerOnly` check to a general role check.
- `app/dashboard/service-orders/page.tsx` and the calendar page — no visible change for `OWNER`/`MANAGER`; a `STAFF` login simply receives an already-filtered list from the backend, so the UI needs no new filtering logic itself.
- `app/dashboard/profile/integrations/page.tsx` — new "Booking hours" card: working-days checkboxes (Mon–Sun), start/end time pickers, a small blackout-dates list (add date + label, remove).
- `app/dashboard/profile/page.tsx` (or wherever Business Profile lives) — editable "Booking page link" field showing `ratel.app/book/{slug}` with a copy button, and the slug edit control.
- `app/book/[slug]/page.tsx` (new, public, outside `app/dashboard`) — a React port of the widget's 3-step flow (service → details → confirmation), styled identically to the manage-booking page's warm palette, calling the same public API. This is genuinely simpler to build as React than as another vanilla-JS pass, and it's the one surface where we control the whole page (no host-site CSS to defend against), so no Shadow DOM needed.
- `backend/.../static/widget/booking.js` — date/time input gains client-side validation against `workingDays`/`workingHoursStart/End` from `widget-config` (disable invalid days, clamp the time picker where the browser's own input allows it); server-side check is the real backstop regardless.
- `components/PoweredByRatel.tsx` — text corrected to "Powered by Ratel Systems".
- `backend/.../static/widget/booking.js` + `app/booking/manage/[token]/page.tsx` + the new `app/book/[slug]/page.tsx` — small "Powered by Ratel Systems" footer line added to each (plain text + existing logo asset for the widget; the real component for the two React pages).

## 4. Explicitly out of scope here

- **Per-staff availability / customer picks a specific stylist** — needs its own data model (staff working hours, staff-level capacity) layered on top of this business-wide version. Natural next step once this ships and we see how owners actually use `assignedStaffId`.
- **Coupons/promo codes** — no existing subsystem to extend; a genuinely separate feature (code generation, redemption, expiry) worth its own pass later.
- **Marketing campaign automation** (slow-period/festive discount blasts) — real idea, but constrained by what's actually possible today: most vendors are on the free WhatsApp Business app, which has no send API. A realistic v1 later would be an owner-facing tool that picks a customer segment (e.g. "no visit in 60 days") and generates one-tap `wa.me` links per customer for manual sending — not server-automated blasting. True automation needs a vendor to upgrade to Meta's paid WhatsApp Cloud API, which is a later, opt-in, per-vendor upgrade path, not something to build against now.
- **Split/multi-shift working hours** (e.g. different hours per weekday, lunch breaks) — one window for all working days is the v1 assumption.
- **Business logo on the booking widget/manage page** — business *name* already shows everywhere; adding the logo image itself is a small fast-follow, not included here to keep this pass focused.

## 5. Suggested build order

1. Migration V14 + entity/repo changes across roles, scheduling, and slug (foundation everything else sits on)
2. `STAFF` role + `ServiceOrderService` scoping + sidebar trimming — the role-based dashboard, tested by actually logging in as a STAFF user
3. Scheduling enforcement in `BookingService.createBooking` (working hours, blackout dates, capacity/duration) — tested against the real widget by trying to double-book a slot and book outside hours
4. Booking hours + blackout dates UI in Integrations, service-catalog duration/capacity fields in the catalog manager
5. Hosted booking page (`/book/[slug]`) + slug editor in Business Profile
6. Branding fixes ("Ratel Systems" text, footers on widget/manage/hosted pages)

Then back to WooCommerce sync (the original task #14).
