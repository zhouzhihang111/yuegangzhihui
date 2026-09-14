INSERT INTO product_category (id, parent_id, code, name, enabled, sort_order) VALUES
    (100, NULL, 'YGH_MALL', '粤港甄选商城', TRUE, 0),
    (101, 100, 'HK_FRESH', '供港生鲜', TRUE, 10),
    (102, 100, 'LINGNAN_SPECIALTY', '岭南特产', TRUE, 20),
    (103, 100, 'CROSS_BORDER_SNACK', '跨境零食', TRUE, 30);
