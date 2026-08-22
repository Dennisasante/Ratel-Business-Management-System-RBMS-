-- Business.enabledModules was inert scaffolding until this session's
-- ModuleAccessService started actually enforcing it. Every pre-existing
-- business row still only has the original narrow default
-- {INVENTORY,SALES,CUSTOMERS,EXPENSES} — nothing ever wrote to this column
-- before now (confirmed: no write endpoint existed until this same change).
-- Without this backfill, enforcing the gate would instantly lock every
-- existing business out of Service Orders, Bookings, Custom Wig Requests,
-- E-commerce Orders, and Suppliers/Purchase Orders — features they already
-- use today. This is meant to be an opt-out a Super Admin explicitly
-- chooses per business, never a retroactive lockout.
UPDATE businesses
SET enabled_modules = ARRAY[
    'INVENTORY', 'SALES', 'CUSTOMERS', 'EXPENSES',
    'SERVICE_ORDERS', 'CUSTOM_WIG_REQUESTS', 'ECOMMERCE', 'BOOKINGS', 'SUPPLIERS_AND_PURCHASING'
];
