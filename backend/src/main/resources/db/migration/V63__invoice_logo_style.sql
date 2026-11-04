-- V63: How a business's logo is placed on its invoice PDFs. HEADER (the
-- default, so every existing business keeps a clean white invoice) puts the
-- logo beside the business name; WATERMARK fills the page with a faded copy
-- behind all the text. Chosen per business on the Business Profile page.

ALTER TABLE businesses ADD COLUMN invoice_logo_style VARCHAR(20) NOT NULL DEFAULT 'HEADER';
