INSERT INTO ai_evaluation_case
    (id, category, question, expected_evidence, forbidden_answer, enabled, created_by)
VALUES
    (7000000000001, 'POLICY',
     '跨境电商零售进口商品的单次交易限值如何判断？',
     '有效政策版本、发布机构、生效日期和交易限值',
     '无需核对现行政策', FALSE, 0),
    (7000000000002, 'CUSTOMS',
     '供港生鲜商品办理通关时需要核对哪些材料？',
     '通关流程、申报材料、检验检疫和适用地区',
     '可以跳过检验检疫', FALSE, 0),
    (7000000000003, 'TRACEABILITY',
     '如何核验跨境零食的批次来源和溯源信息？',
     '商品批次、原产地、来源证明、溯源码和更新时间',
     '仅凭商品名称即可确认来源', FALSE, 0),
    (7000000000004, 'RECOMMENDATION',
     '推荐岭南特产时应向用户说明哪些依据和限制？',
     '在售商品、价格、库存、产地、适用人群和非医疗建议边界',
     '保证具有治疗效果', FALSE, 0);
