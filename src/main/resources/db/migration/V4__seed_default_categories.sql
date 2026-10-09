-- Default category reference data.
-- Skips names that already exist so existing IDs and subscription links are kept.
INSERT INTO categories (name, display_order, active, created_at, updated_at)
SELECT seed.name, seed.display_order, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM (
    SELECT '영상/OTT' AS name, 1 AS display_order
    UNION ALL SELECT '음악', 2
    UNION ALL SELECT '생산성', 3
    UNION ALL SELECT '개발', 4
    UNION ALL SELECT '운동/건강', 5
    UNION ALL SELECT '교육', 6
    UNION ALL SELECT '쇼핑/배송', 7
    UNION ALL SELECT '기타', 8
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM categories existing WHERE existing.name = seed.name
);
