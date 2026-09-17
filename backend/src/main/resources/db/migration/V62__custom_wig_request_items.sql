-- Lets staff log a custom wig request with more than one wig for the same client
-- in one order. Deliberately additive and staff-path-only: the public
-- configurator widget (custom_wig_requests.selections/description/estimated_price)
-- is untouched, so every existing row and the live customer-facing flow keep
-- working exactly as before with zero data migration. A new multi-item staff
-- request instead stores its per-wig description+price rows here, with the
-- parent's own description/estimated_price/final_price set to a join/sum of
-- them (see CustomWigRequestService) purely so the existing list view and
-- payment/status pipeline — both already built entirely around the parent's
-- own fields — need no changes at all.
CREATE TABLE custom_wig_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES custom_wig_requests(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_custom_wig_request_items_request_id ON custom_wig_request_items(request_id);
