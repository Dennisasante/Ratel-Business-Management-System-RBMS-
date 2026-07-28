# RBMS v4 — Service Orders, Inventory, and UI/UX Upgrade Spec

Scoped against the actual codebase (Spring Boot + Postgres/Flyway backend, Next.js frontend, multi-tenant via `business_id` on every table).

**Confirmed decisions (final, not open questions):**
- `service_catalog` is a brand-new table — no existing fixed-price catalog to wire to.
- Removing a `product_category` that's still in use on any product is **blocked** with a clear message ("2 products use this — reassign them first"), not auto-uncategorized.
- Receipt printing uses a dedicated print-formatted view + the browser's native `window.print()` — no Web USB/ESC/POS driver work. Works on any OS/browser against whatever printer (58mm/80mm thermal or otherwise) is set up at the OS level. No programmatic cut/drawer-kick control; that's a clean later add-on if ever needed, not a rebuild.
- Brand palette: primary `#004aad` (blue), accent `#db1a1a` (red), base `#ffffff`, carried over from Skulba, with room for secondary/neutral tones added so status badges/highlights don't all compete for the same two colors.
- "Order ready" email: auto-sends on transition to Completed (if the client has an email on file) **and** has a manual "Resend" button on the order for later.

## 1. Database — `V8__service_orders.sql`

```sql
-- Fixed-price catalog for services (installs/revamps/wig-making "standard" prices).
-- Kept separate from products; an order's price always starts here but is
-- copied onto the order and is editable per-order from that point on.
CREATE TABLE service_catalog (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL, -- INSTALLATION, REVAMP, WIG_MAKING, OTHER
    name         VARCHAR(150) NOT NULL,
    price        NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE service_orders (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    order_number        BIGSERIAL,                 -- "Order #204" — same pattern as sale_number/po_number
    type                VARCHAR(20) NOT NULL,       -- INSTALLATION, REVAMP, WIG_MAKING, OTHER
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, IN_PROGRESS, COMPLETED, PICKED_UP, CANCELLED
    customer_id         UUID REFERENCES customers(id) ON DELETE SET NULL,  -- reuse existing customers table
    service_catalog_id  UUID REFERENCES service_catalog(id) ON DELETE SET NULL, -- which catalog item this priced from, if any
    notes               TEXT,                       -- what's wrong / what's needed
    price               NUMERIC(12,2) NOT NULL DEFAULT 0,   -- editable, pre-filled from catalog
    assigned_staff_id   UUID REFERENCES users(id) ON DELETE SET NULL, -- tracking only, no commission math
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(), -- the two timestamps flagged as most important
    picked_up_at         TIMESTAMPTZ,
    ready_email_sent_at TIMESTAMPTZ,                 -- null until the "ready" email has gone out at least once
    created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_catalog_business_id ON service_catalog(business_id);
CREATE INDEX idx_service_orders_business_id ON service_orders(business_id);
CREATE INDEX idx_service_orders_status ON service_orders(business_id, status);
CREATE INDEX idx_service_orders_type ON service_orders(business_id, type);
CREATE INDEX idx_service_orders_customer_id ON service_orders(customer_id);

-- Inventory: real categories instead of a free-text column.
CREATE TABLE product_categories (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_categories_business_name UNIQUE (business_id, name)
);

ALTER TABLE products ADD COLUMN category_id UUID REFERENCES product_categories(id) ON DELETE SET NULL;
-- Backfill: turn existing free-text `category` values into real rows, then point products at them.
INSERT INTO product_categories (business_id, name)
SELECT DISTINCT business_id, category FROM products WHERE category IS NOT NULL AND category <> '';
UPDATE products p SET category_id = pc.id
FROM product_categories pc
WHERE pc.business_id = p.business_id AND pc.name = p.category;
-- Old free-text column kept for one release as a safety net, then dropped in V9.
```

Why `service_catalog` rather than reusing `products`: services aren't stock items (no quantity/SKU), and a "type" concept (Installation/Revamp/Wig-Making) doesn't belong on the product table. Reusing `customers` rather than a new table: wig-making clients arrive by phone/WhatsApp/IG with no distinct shape from revamp clients, so one client record either way.

`is_active` on `product_categories` isn't included — categories aren't archived, they're managed outright (add/rename/remove) since nothing snapshots a category name the way sale/PO line items snapshot a product name. Removing a category in use should either block ("2 products use this — reassign them first") or set those products to uncategorized; I'd default to block, since silent uncategorizing hides a data problem. Flag if you'd rather it auto-uncategorize.

## 2. Backend

New Java package additions, following the existing `entity` / `repository` / `service` / `controller` / `dto` split (mirrors `Supplier`/`PurchaseOrder`):

- `entity/ServiceOrder.java`, `entity/ServiceCatalogItem.java`, `entity/ProductCategory.java`
- `entity/enums/ServiceOrderType.java` (`INSTALLATION, REVAMP, WIG_MAKING, OTHER`), `entity/enums/ServiceOrderStatus.java` (`RECEIVED, IN_PROGRESS, COMPLETED, PICKED_UP, CANCELLED`)
- `repository/ServiceOrderRepository.java` — filtered finders by business+type+status, plus the report queries (revenue by type for a date range on picked-up orders, status counts, avg turnaround)
- `service/ServiceOrderService.java` — status transition logic; on transition to `COMPLETED`, if `customer.email` is set, calls `EmailService` (existing) fire-and-forget, sets `ready_email_sent_at`; exposes a `resendReadyEmail(orderId)` for the manual resend button regardless of current status
- `service/ServiceOrderReportService.java` — the three report pieces, kept separate from `SalesReportService`
- `controller/ServiceOrderController.java` — `GET /service-orders?type=&status=&page=`, `POST`, `PATCH /{id}/status`, `PATCH /{id}` (edit notes/price/assignee), `POST /{id}/resend-ready-email`
- `controller/ServiceCatalogController.java` — CRUD for the fixed-price list, `is_active` toggle instead of delete (same "archive not destroy" logic as products, see below)
- `controller/ProductCategoryController.java` — CRUD; delete blocked while in use (see above)
- `dto/` — `ServiceOrderRequest/Response`, `ServiceOrderReportResponse`, `ProductCategoryRequest/Response`

Report endpoint shape (`GET /service-orders/report?from=&to=`):
```json
{
  "revenueByType": { "INSTALLATION": 4200, "REVAMP": 1800, "WIG_MAKING": 3100, "OTHER": 0 },
  "statusCounts": { "RECEIVED": 3, "IN_PROGRESS": 7, "COMPLETED": 2, "PICKED_UP": 40, "CANCELLED": 1 },
  "avgTurnaroundHours": 52.4
}
```
`avgTurnaroundHours` computed only over orders with both `received_at` and `picked_up_at` set, over the same date range (by `picked_up_at`, matching how revenue is scoped to picked-up orders).

**Product delete → archive, not hard delete.** Since `sale_items`/`purchase_order_items` already snapshot `product_name`, this is genuinely safe as a single-path operation: `PATCH /products/{id}/archive` sets `is_active=false`, filtered out of `GET /products` (Inventory list), the Sales product picker, and the PO product picker by default, with an `includeArchived=true` param for a "show archived" toggle so it's discoverable and reversible, not just hidden forever.

**Inventory search + category filter.** Add `?search=&categoryId=` to the existing products endpoint (search across `name` + `sku`), and reuse the identical params on whatever endpoint backs the Sales and PO product pickers — right now, worth checking whether those pickers hit `GET /products` directly or a separate lightweight endpoint; if separate, the same two params need adding there too so behavior doesn't diverge.

## 3. Frontend

- `app/dashboard/service-orders/` — list page (table with type/status filter chips, the two timestamps as real columns not tucked in a detail view), new/edit form (`ServiceOrderForm.tsx` mirroring `ProductForm.tsx`/`SupplierForm.tsx` conventions), a status-change control that's a single obvious action (not a raw dropdown) since Received→In Progress→Completed→Picked Up is a fixed line, with Cancelled as the one branch off it
- `app/dashboard/service-orders/report/` — its own page, not folded into the sales report
- Inventory: category management UI (simple list with add/rename/remove, rename is instant since it's just relabeling, remove blocked with a clear reason if in use), search box + category dropdown filter, same two controls added to the Sales and PO product picker modals, "Remove" button on each product row that always archives (with an undo toast, and an "Archived" tab to restore from)

## 4. UI/UX pass

**The 2-second dead-click problem.** Two separate fixes needed:
1. Every data-fetching page needs a skeleton state (not a spinner-only state) shown immediately on mount/navigation — content-shaped gray blocks so the layout doesn't jump when data arrives. Given the number of list/table pages in this app, this is worth one shared `<TableSkeleton />` / `<CardSkeleton />` component rather than one-off per page.
2. Every button that triggers a network call (save, delete, status change, resend email) needs its own local pending state — disable + inline spinner on that specific button the instant it's clicked, independent of any page-level loading state. This is the actual fix for "clicked and nothing happened for 2 seconds" — that's a mutation, not a page load, and needs per-action feedback.

**"Boring" → visual identity.** Concretely, not just "add color": a real type scale and consistent spacing rhythm (the skill I'll load for this enforces both), one accent color used deliberately for primary actions and status (not every UI element), status badges as actual colored pills for RECEIVED/IN_PROGRESS/COMPLETED/PICKED_UP/CANCELLED so a list is scannable at a glance, subtle motion on state changes (a status pill transitioning, a toast sliding in) rather than instant snap-changes, and empty states that look designed ("No orders yet — service orders you create will show up here") instead of a bare blank table.

**Receipt printing (58/80mm).** A dedicated print-formatted receipt view — fixed narrow width (toggle for 58mm vs 80mm), monospace, no nav chrome, business name/logo/contact header, line items, total, timestamp — for both Sales and picked-up Service Orders, triggered from a "Print Receipt" button that opens the view and calls `window.print()`. Works against whatever printer is set up at the OS level (thermal or otherwise), on any browser/OS, no drivers or extra install. CSS `@media print` rules strip everything but the receipt itself so it doesn't print the app chrome.

## 5. Explicitly out of scope here
- WhatsApp "ready" notification — a later step, not now
- Commission math on service orders — confirmed off, tracking-only `assigned_staff_id`

## 6. Suggested build order
1. Migration V8 + entities/repos (foundation everything else sits on)
2. Service Orders backend (CRUD, status transitions, email hook)
3. Service Orders report
4. Inventory categories + search + archive-delete (backend, then picker UI)
5. Service Orders frontend (list, form, report page)
6. Shared skeleton/pending-state components, rolled out across existing pages
7. Visual pass (palette, badges, empty states, motion)
8. Receipt printing
