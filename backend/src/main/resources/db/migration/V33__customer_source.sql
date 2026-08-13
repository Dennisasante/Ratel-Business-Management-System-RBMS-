-- How a customer found the business (Walk-in, Instagram, WhatsApp, Facebook,
-- Referral, Website, Other) — free text like CustomWigRequest.source (V30),
-- not an enum, so new acquisition channels never need a migration. Null for
-- every existing customer; nothing about their original source is known.
ALTER TABLE customers ADD COLUMN source VARCHAR(30);
