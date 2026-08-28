-- Per-business toggle for a thermal receipt printer (e.g. an Xprinter
-- XP-80T) — not every business has one, so this defaults to off. When on,
-- the paper width lets the receipt view (already 58mm/80mm-aware, see
-- ReceiptView.tsx) pick the right @page size automatically instead of
-- requiring a manual toggle on every print. Printing itself still goes
-- through the browser's native print dialog against whatever printer is
-- installed at the OS level — this setting never talks to a printer
-- directly, so it works the same whether the printer is USB, network, or a
-- paired Bluetooth device appearing as a normal system printer.
ALTER TABLE business_integrations
    ADD COLUMN receipt_printer_enabled     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN receipt_printer_paper_width VARCHAR(5) NOT NULL DEFAULT '80';
