# RBMS v4 — Subscription Billing (Paystack) Spec

Builds on top of what already exists: `platform_admins` (your separate super-admin login, already fully built) and a `subscription_plan` text column on `businesses` that currently does nothing. This turns that into a real billing system.

**Two things I'm deciding by default rather than asking about — correct me if wrong:**
- **Existing businesses**, when this ships, get grandfathered as `ACTIVE` with a `current_period_ends_at` 30 days out from deploy day, rather than instantly landing in read-only. Flipping real, currently-working businesses to read-only the moment this migration runs would be a bad surprise for anyone using the system right now.
- **Paystack currency = GHS.** Your businesses default to GHS and you're in Accra, so I'm assuming your Paystack account is Ghana-based and settles in GHS. If any of your businesses run in a different currency, Paystack would need a matching sub-account or this stays GHS-only for now — flag it if that's not right.

## 1. Database — next migration (`V9` if Service Orders lands first, `V8` if this lands first — Claude Code should just use the next free number)

```sql
CREATE TABLE subscription_plans (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                 VARCHAR(50) NOT NULL,          -- "Basic", "Pro"
    price                NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL DEFAULT 'GHS',
    billing_period_days  INT NOT NULL DEFAULT 30,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE, -- archived, not deleted, once a business has ever used it
    sort_order           INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Single-row global setting, managed by you.
CREATE TABLE platform_billing_settings (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trial_days        INT NOT NULL DEFAULT 14,
    usd_display_rate  NUMERIC(10,4),                    -- GHS-per-USD, e.g. 15.20 — you set/update this manually; null hides the USD toggle entirely
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_billing_settings (trial_days) VALUES (14);

ALTER TABLE businesses
    ADD COLUMN subscription_plan_id     UUID REFERENCES subscription_plans(id) ON DELETE SET NULL,
    ADD COLUMN billing_status           VARCHAR(20) NOT NULL DEFAULT 'TRIALING', -- TRIALING, ACTIVE, READ_ONLY
    ADD COLUMN trial_ends_at            TIMESTAMPTZ,
    ADD COLUMN current_period_ends_at   TIMESTAMPTZ;

-- Grandfather existing businesses (see assumption above) so this ships without
-- locking anyone out on day one.
UPDATE businesses
SET billing_status = 'ACTIVE', current_period_ends_at = now() + INTERVAL '30 days'
WHERE is_active = TRUE;

CREATE TABLE subscription_payments (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id          UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    subscription_plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    amount               NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL,
    paystack_reference   VARCHAR(100) NOT NULL UNIQUE,  -- idempotency key: webhook + client verify can't double-record
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    period_start         TIMESTAMPTZ,
    period_end           TIMESTAMPTZ,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_payments_business_id ON subscription_payments(business_id);
-- old free-text `subscription_plan` column on businesses kept for one release, dropped later.
```

New businesses at signup: `billing_status = 'TRIALING'`, `trial_ends_at = now() + trial_days` (read from `platform_billing_settings`), `subscription_plan_id = NULL` until they pay for the first time.

## 2. Backend

- `entity/SubscriptionPlan.java`, `entity/SubscriptionPayment.java`, `entity/PlatformBillingSettings.java`
- `entity/enums/BillingStatus.java` (`TRIALING, ACTIVE, READ_ONLY`)
- `service/PaystackService.java` — thin wrapper around Paystack's REST API: `initializeTransaction(email, amountKobo, reference, metadata)`, `verifyTransaction(reference)`, `verifyWebhookSignature(rawBody, signatureHeader)` (HMAC-SHA512 against your secret key, per Paystack's docs)
- `service/BillingService.java` — business-facing logic:
  - `getStatus(businessId)` → plan, billing_status, days remaining, next due date
  - `startCheckout(businessId, planId)` → calls Paystack `initializeTransaction`, returns the `access_code` for the Inline popup (kept in-app rather than redirecting off-site)
  - `verifyPayment(reference)` → **always re-verifies server-side against Paystack** (never trusts a client-reported "success"), then on confirmed success: records the `subscription_payments` row, sets `current_period_ends_at = GREATEST(now(), current_period_ends_at) + plan.billing_period_days` (so paying early doesn't forfeit remaining days), sets `subscription_plan_id`, flips `billing_status` to `ACTIVE`
  - webhook handler does the same verify-and-record path as a redundant source of truth, guarded by the unique `paystack_reference` so a webhook + a client-triggered verify for the same payment can't double-extend the period
- `controller/PlatformSubscriptionPlanController.java` — super admin CRUD for the 2 (or more, later) tiers; "delete" archives (`is_active=false`) once any business has ever used it, same pattern as products
- `controller/PlatformBillingSettingsController.java` — super admin get/set `trial_days` and `usd_display_rate` (the GHS-per-USD rate you keep updated manually — no external FX API dependency, no extra moving part to break)
- `controller/BillingController.java` — `GET /billing/status`, `GET /billing/plans`, `POST /billing/checkout`, `POST /billing/verify`, `GET /billing/history` — restricted to the `OWNER` role (billing shouldn't be a staff-level action)
- `controller/PaystackWebhookController.java` — `POST /webhooks/paystack`, signature-verified, idempotent on `paystack_reference`
- **Read-only enforcement**: a request filter/interceptor (sits next to the existing JWT auth filter) that, on any mutating request (`POST`/`PUT`/`PATCH`/`DELETE`) from a business-scoped user whose `billing_status = READ_ONLY`, short-circuits with `402 Payment Required` and a clear error body — except for `/billing/**`, `/auth/**`, and anything under `/platform/**` (you're never locked out of your own system). Enforced server-side regardless of what the frontend shows, since that's the actual security boundary.
- **Scheduled job**, reusing the existing `@Scheduled` digest pattern already in the codebase: daily, find businesses where `trial_ends_at < now()` (still `TRIALING`) or `current_period_ends_at < now()` (still `ACTIVE`), flip them to `READ_ONLY`. Worth adding on top, same free-infrastructure logic as the service-order email: a reminder email via the existing `EmailService` 3 days before expiry, so it's not a total surprise. Easy to leave out if you'd rather not.
- New env vars: `PAYSTACK_SECRET_KEY`, `PAYSTACK_PUBLIC_KEY` (frontend also needs the public one) — same `application.yml` pattern as the existing `SMTP_*`/`SUPER_ADMIN_*` vars.

## 3. Frontend

- `app/platform/plans/` — super admin: list the tiers, edit name/price, add a new tier, archive/restore
- `app/platform/billing-settings/` (or folded into an existing platform settings page if one exists) — `trial_days`, plus the `usd_display_rate` field so you can update it whenever the real rate moves
- `app/dashboard/billing/` — owner-only: current plan + a `billing_status` badge, days remaining until `current_period_ends_at`/`trial_ends_at`, plan cards to choose/switch plans, "Pay with Paystack" button using Paystack's Inline JS (`paystack-inline.js`) so payment happens in a popup without leaving the page, payment history table. A **GHS / USD display toggle** on the plan cards — switching it recalculates the shown price client-side using `usd_display_rate` (no re-fetch needed), with a small "≈" and a one-line note that checkout always charges in GHS regardless of which display currency is selected, so there's no confusion about what actually gets charged at the Paystack popup. If `usd_display_rate` isn't set, the toggle doesn't render at all rather than showing a broken/zero conversion.
- Global read-only experience: a persistent banner shown app-wide when `billing_status === READ_ONLY` ("Your subscription has ended — renew to keep creating and editing"), with create/edit/delete controls disabled or redirected to the billing page. This is UX polish only — the filter above is what actually enforces it.

## 4. Explicitly out of scope here
- Proration on mid-cycle plan changes — switching plans takes effect at the next renewal, no partial-period math
- Multi-currency Paystack sub-accounts — GHS only, per the assumption above
- Auto-retry/dunning on failed card payments — Paystack Inline surfaces the failure immediately at checkout time; there's no stored card to retry against later since this isn't card-on-file recurring billing, just "pay to extend 30 days"

## 5. Suggested build order
1. Migration + entities/repos (including the grandfathering update)
2. `PaystackService` (isolated, easy to test against Paystack's test keys before touching billing logic)
3. `BillingService` + `BillingController` + webhook handler
4. Read-only enforcement filter
5. Scheduled expiry job (+ optional reminder email)
6. Super admin plan + trial-length management pages
7. Business owner Billing page + Paystack Inline checkout
8. Global read-only banner/gating in the frontend
