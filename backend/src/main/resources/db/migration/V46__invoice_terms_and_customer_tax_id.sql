-- V46: Terms & Conditions (a reusable business-level default, snapshotted
-- per invoice like everything else on Invoice) and an optional per-invoice
-- customer Tax ID, for B2B clients who need their own TIN on the document.

ALTER TABLE businesses ADD COLUMN default_terms_and_conditions TEXT;

ALTER TABLE invoices ADD COLUMN customer_tax_id VARCHAR(50);
ALTER TABLE invoices ADD COLUMN terms_and_conditions TEXT;
