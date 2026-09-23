EXPLAIN ANALYZE
SELECT
    s.*
FROM subscriptions s
WHERE s.user_id = 1;

EXPLAIN ANALYZE
SELECT
    c.*
FROM categories c
WHERE c.category_id = 1;

EXPLAIN ANALYZE
SELECT
    s.*,
    c.category_id,
    c.active,
    c.created_at,
    c.display_order,
    c.name,
    c.updated_at
FROM subscriptions s
JOIN categories c ON c.category_id = s.category_id
WHERE s.user_id = 1;
