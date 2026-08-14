-- Free-text description for a manually-logged request ("24 inches HD wig")
-- — the staff-facing create flow no longer requires picking from configured
-- attributes at all (see CustomWigRequestService.createByStaff). Null for
-- every existing row and for anything still submitted through the public
-- configurator widget, which keeps using `selections` as before.
ALTER TABLE custom_wig_requests ADD COLUMN description TEXT;

-- Same payment-status/amount-paid tracking as sales and service orders (see
-- V32) — no backfill needed since payment collection on a custom wig request
-- didn't exist before this, so every historical row is correctly UNPAID/0.
ALTER TABLE custom_wig_requests ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE custom_wig_requests ADD COLUMN amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Same gateway-charge tracking as sales/service orders' paystack_reference,
-- for the mobile-money charge-with-OTP flow reused here.
ALTER TABLE custom_wig_requests ADD COLUMN paystack_reference VARCHAR(100);
