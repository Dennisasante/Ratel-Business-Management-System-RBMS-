-- V47: Reverts the customer-side Tax ID added in V46 — Tax ID is a
-- property of the business issuing the invoice, not the customer it's
-- billed to. terms_and_conditions (added in the same V46) stays.

ALTER TABLE invoices DROP COLUMN customer_tax_id;
