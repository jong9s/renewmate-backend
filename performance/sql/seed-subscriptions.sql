USE renewmate_perf;

SET @perf_user_id = (
    SELECT user_id
    FROM users
    WHERE email = 'perf@renewmate.local'
);

DELETE FROM subscriptions WHERE user_id = @perf_user_id;
DELETE FROM categories WHERE name LIKE 'Category %';

INSERT INTO categories (name, display_order, active, created_at, updated_at)
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 20
)
SELECT
    CONCAT('Category ', LPAD(n, 2, '0')),
    n,
    b'1',
    NOW(),
    NOW()
FROM sequence;

SET SESSION cte_max_recursion_depth = 2000;

INSERT INTO subscriptions (
    amount,
    auto_renew,
    billing_interval,
    next_billing_date,
    reminder_days,
    start_date,
    category_id,
    created_at,
    updated_at,
    user_id,
    payment_method,
    service_name,
    service_url,
    memo,
    billing_cycle,
    currency,
    status
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 1000
)
SELECT
    10000 + (sequence.n MOD 50000),
    b'1',
    1,
    DATE_ADD(CURDATE(), INTERVAL (sequence.n MOD 365) DAY),
    3,
    DATE_SUB(CURDATE(), INTERVAL (sequence.n MOD 730) DAY),
    category.category_id,
    NOW(),
    NOW(),
    @perf_user_id,
    'Performance Card',
    CONCAT('Service ', LPAD(sequence.n, 4, '0')),
    CONCAT('https://example.com/service/', sequence.n),
    'Local performance fixture',
    'MONTHLY',
    'KRW',
    'ACTIVE'
FROM sequence
JOIN categories category
    ON category.name = CONCAT('Category ', LPAD(1 + (sequence.n MOD 20), 2, '0'));

ANALYZE TABLE subscriptions, categories;
