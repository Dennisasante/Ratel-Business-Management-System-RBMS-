-- Adds single-level subcategory support to product_categories. A category with a
-- non-null parent_id is a subcategory; a subcategory may not itself have children
-- (enforced in ProductCategoryService, not here, since a CHECK constraint can't see
-- other rows) so nesting never goes deeper than one level.
--
-- No ON DELETE CASCADE here, deliberately — matches the existing philosophy already
-- enforced in ProductCategoryService.delete() for products (block, never silently
-- cascade): deleting a category that still has subcategories must fail, not quietly
-- delete them and orphan (categoryId -> NULL) whatever products those subcategories held.
ALTER TABLE product_categories
    ADD COLUMN parent_id UUID REFERENCES product_categories(id);

CREATE INDEX idx_product_categories_parent_id ON product_categories(parent_id);
