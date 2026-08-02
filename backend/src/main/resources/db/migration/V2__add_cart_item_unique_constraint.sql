-- Rule 2 (a food item appears at most once per cart) was enforced only in CartService, so two
-- concurrent adds could each read an empty cart and both INSERT, duplicating the line forever.
-- The constraint below makes the database the authority: the losing INSERT now fails with a
-- DataIntegrityViolationException, which GlobalExceptionHandler maps to 409. CartService does not
-- retry that failure yet, so a genuinely concurrent add currently surfaces as an error rather than
-- merging into the existing line - adding that retry is tracked separately as follow-up work.

-- Duplicates already sitting in the table would make the ALTER fail, so fold them together first.
-- The lowest id of each (cart_id, food_item_id) group inherits the summed quantity, which is what
-- the merge rule would have produced had the adds been serialised.
UPDATE cart_items ci
JOIN (
    SELECT MIN(id) AS keep_id, SUM(quantity) AS merged_quantity
    FROM cart_items
    GROUP BY cart_id, food_item_id
    HAVING COUNT(*) > 1
) dup ON ci.id = dup.keep_id
SET ci.quantity = dup.merged_quantity;

-- The surviving higher-id rows of those groups are then dropped.
DELETE ci FROM cart_items ci
JOIN (
    SELECT cart_id, food_item_id, MIN(id) AS keep_id
    FROM cart_items
    GROUP BY cart_id, food_item_id
    HAVING COUNT(*) > 1
) dup ON ci.cart_id = dup.cart_id AND ci.food_item_id = dup.food_item_id
WHERE ci.id > dup.keep_id;

ALTER TABLE cart_items
    ADD CONSTRAINT uq_cart_items_cart_id_food_item_id UNIQUE (cart_id, food_item_id);
