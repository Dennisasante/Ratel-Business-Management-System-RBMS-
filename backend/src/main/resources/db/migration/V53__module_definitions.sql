-- Talia Unified Platform — Phase 1: establishes ONE canonical module
-- vocabulary shared with Serenity's own ModuleCode, as a pure reference
-- table. Purely additive: does not alter businesses.enabled_modules, does
-- not touch ModuleAccessService/PlanFeatureService's enforcement, and does
-- not activate any module for any business. Nothing in the application
-- reads this table yet — see the architecture proposal §E/§Q for what a
-- later phase does with it. required_modules/default_for_profiles are
-- plain text[] (no per-element FK), matching businesses.enabled_modules'
-- own existing convention — this is meant to stay a lookup table an admin
-- can read, not a rules engine.
CREATE TABLE module_definitions (
    code                  VARCHAR(50) PRIMARY KEY,
    label                 VARCHAR(100) NOT NULL,
    industry_group        VARCHAR(30) NOT NULL,
    required_modules      TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    default_for_profiles  TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    is_core               BOOLEAN NOT NULL DEFAULT FALSE,
    -- TRUE only for the 10 codes ModuleAccessService/PlatformBusinessService
    -- already gate/validate today. Every other row is vocabulary reserved
    -- for a future phase — inserting the row does not turn anything on.
    already_enforced      BOOLEAN NOT NULL DEFAULT FALSE,
    -- Which real codebase's existing vocabulary this code came from —
    -- 'RBMS', 'SERENITY', or 'BOTH' (an exact-name match in both). Purely
    -- documentation; nothing reads this column programmatically.
    source_system         VARCHAR(20) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- RBMS's existing CORE set (Business's own DB default since V1, never
-- gated by ModuleAccessService — see that class's own comment).
INSERT INTO module_definitions (code, label, industry_group, required_modules, default_for_profiles, is_core, already_enforced, source_system) VALUES
('INVENTORY', 'Inventory', 'CORE', ARRAY[]::TEXT[], ARRAY['RETAIL','RESTAURANT','HOSPITALITY','SERVICES','MULTI'], TRUE, TRUE, 'BOTH'),
('SALES', 'Sales', 'CORE', ARRAY[]::TEXT[], ARRAY['RETAIL','RESTAURANT','HOSPITALITY','SERVICES','MULTI'], TRUE, TRUE, 'RBMS'),
('CUSTOMERS', 'Customers', 'CORE', ARRAY[]::TEXT[], ARRAY['RETAIL','RESTAURANT','HOSPITALITY','SERVICES','MULTI'], TRUE, TRUE, 'RBMS'),
('EXPENSES', 'Expenses', 'CORE', ARRAY[]::TEXT[], ARRAY['RETAIL','RESTAURANT','HOSPITALITY','SERVICES','MULTI'], TRUE, TRUE, 'RBMS');

-- RBMS's existing TOGGLEABLE set (PlatformBusinessService.TOGGLEABLE_MODULES
-- — a Super Admin can already grant/revoke each of these per business today).
INSERT INTO module_definitions (code, label, industry_group, required_modules, default_for_profiles, is_core, already_enforced, source_system) VALUES
('SERVICE_ORDERS', 'Service Orders', 'SERVICES', ARRAY[]::TEXT[], ARRAY['SERVICES'], FALSE, TRUE, 'RBMS'),
('CUSTOM_WIG_REQUESTS', 'Custom Wig Requests', 'SERVICES', ARRAY['SERVICE_ORDERS'], ARRAY[]::TEXT[], FALSE, TRUE, 'RBMS'),
('ECOMMERCE', 'E-commerce Orders', 'RETAIL', ARRAY[]::TEXT[], ARRAY['RETAIL'], FALSE, TRUE, 'RBMS'),
('BOOKINGS', 'Bookings', 'SERVICES', ARRAY[]::TEXT[], ARRAY['SERVICES'], FALSE, TRUE, 'RBMS'),
('SUPPLIERS_AND_PURCHASING', 'Suppliers & Purchase Orders', 'CORE', ARRAY[]::TEXT[], ARRAY['RETAIL','RESTAURANT','HOSPITALITY','SERVICES','MULTI'], FALSE, TRUE, 'RBMS'),
('AI', 'Talia AI', 'CORE', ARRAY[]::TEXT[], ARRAY[]::TEXT[], FALSE, TRUE, 'BOTH');

-- Serenity's ModuleCode vocabulary — confirmed this phase to have ZERO call
-- sites anywhere in Serenity's own code (grepped fresh, not assumed). Added
-- here as reserved vocabulary only; already_enforced is FALSE for every one
-- of these, and nothing in either RBMS or Serenity is wired to check them.
INSERT INTO module_definitions (code, label, industry_group, required_modules, default_for_profiles, is_core, already_enforced, source_system) VALUES
('POS', 'Point of Sale', 'RESTAURANT', ARRAY['INVENTORY'], ARRAY['RESTAURANT'], FALSE, FALSE, 'SERENITY'),
('RESERVATIONS', 'Table/Room Reservations', 'RESTAURANT', ARRAY[]::TEXT[], ARRAY['RESTAURANT'], FALSE, FALSE, 'SERENITY'),
('ACCOMMODATION', 'Accommodation', 'HOSPITALITY', ARRAY[]::TEXT[], ARRAY['HOSPITALITY'], FALSE, FALSE, 'SERENITY'),
('WHATSAPP', 'WhatsApp Channel', 'CORE', ARRAY['AI'], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('INSTAGRAM', 'Instagram Channel', 'CORE', ARRAY['AI'], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('FACEBOOK', 'Facebook Channel', 'CORE', ARRAY['AI'], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('VOICE', 'Voice Channel', 'CORE', ARRAY['AI'], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('WEBCHAT', 'Web Chat Channel', 'CORE', ARRAY['AI'], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('CRM', 'CRM', 'CORE', ARRAY[]::TEXT[], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('TICKETS', 'Support Tickets', 'CORE', ARRAY[]::TEXT[], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('CAMPAIGNS', 'Campaigns', 'CORE', ARRAY[]::TEXT[], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY'),
('INTEGRATIONS', 'Integrations', 'CORE', ARRAY[]::TEXT[], ARRAY[]::TEXT[], FALSE, FALSE, 'SERENITY');

-- Named in the architecture proposal §D (kept its own module, not folded
-- into Restaurant/Hospitality); backed by no enforcement code in either
-- system today — RBMS has none, and Serenity's real Events tool
-- (CreateEventEnquiryTool) is gated by an AiCapabilityService capability
-- flag (EVENT_ENQUIRY), a finer axis than ModuleCode entirely, not by this
-- module vocabulary. Reserved here as vocabulary only.
INSERT INTO module_definitions (code, label, industry_group, required_modules, default_for_profiles, is_core, already_enforced, source_system) VALUES
('EVENTS', 'Events', 'EVENTS', ARRAY[]::TEXT[], ARRAY['EVENTS'], FALSE, FALSE, 'PROPOSED');
