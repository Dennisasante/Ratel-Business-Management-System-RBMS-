-- Redesign support for the "Build Your Own Wig" configurator:
-- - selection_type lets an attribute be single-choice (radio-style, the only
--   mode that existed before) or multiple-choice (e.g. "Finishing options" —
--   pre-plucked, baby hairs, elastic band, all pickable together). No wire
--   format change was needed for submission itself — a multiple-choice
--   attribute just contributes more than one {attributeId, optionId} pair to
--   the same flat selections array the backend already sums unconditionally.
-- - step_group is a purely cosmetic hint for the hosted page: attributes
--   sharing the same non-null step_group render together as one step (e.g.
--   "Length" + "Texture" both tagged "Choose your hair"), instead of forcing
--   one attribute per page.
-- - requires_manual_quote flags an option (e.g. "Custom Colour") whose real
--   price isn't knowable up front — its price_modifier stays informational
--   (typically 0), and the customer-facing estimate is labeled as such
--   whenever a selected option carries this flag.
ALTER TABLE custom_item_attributes
    ADD COLUMN selection_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN step_group VARCHAR(60);

ALTER TABLE custom_item_attribute_options
    ADD COLUMN requires_manual_quote BOOLEAN NOT NULL DEFAULT FALSE;
