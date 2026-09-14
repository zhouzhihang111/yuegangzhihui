package com.yuegang.zhihui.product.application;

import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.product.api.*;
import java.util.*;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProductService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ProductSearchGateway search;

    public ProductService(DataSource dataSource) {
        this(dataSource, null);
    }

    public ProductService(DataSource dataSource, ProductSearchGateway search) {
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.search = search;
    }

    public ProductView create(SaveProductRequest command) {
        return transactions.execute(status -> {
            long spu = next(), sku = next();
            jdbc.update("INSERT INTO product_spu(id,category_id,brand_id,name,status) VALUES(?,?,?,?,'DRAFT')", spu, id(command.categoryId()), blankId(command.brandId()), command.name());
            jdbc.update("INSERT INTO product_sku(id,spu_id,sku_code,price,currency,traceability_code,status) VALUES(?,?,?,?,?,?,'DRAFT')", sku, spu, command.skuCode(), command.price(), command.currency(), command.traceabilityCode());
            int sort = 0;
            for (String url : command.images()) jdbc.update("INSERT INTO product_image(id,spu_id,sku_id,url,sort_order) VALUES(?,?,?,?,?)", next(), spu, sku, url, sort++);
            replaceSpecifications(sku, command.specifications());
            return get(Long.toString(sku), false);
        });
    }

    public List<ProductView> list(String category, String keyword, int limit, boolean publicOnly) {
        return list(category, keyword, null, null, null, null, limit, publicOnly);
    }

    public List<ProductView> list(String category, String keyword, BigDecimal minimumPrice, BigDecimal maximumPrice,
                                  String origin, ProductStatus requestedStatus, int limit, boolean publicOnly) {
        if ((minimumPrice != null && minimumPrice.signum() < 0) || (maximumPrice != null && maximumPrice.signum() < 0)
                || (minimumPrice != null && maximumPrice != null && minimumPrice.compareTo(maximumPrice) > 0)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        int size = Math.max(1, Math.min(limit, 100));
        List<String> matchedSkuIds = null;
        if (publicOnly && keyword != null && !keyword.isBlank() && search != null) {
            try {
                matchedSkuIds = search.search(keyword.strip(), 100);
                if (matchedSkuIds.isEmpty()) return List.of();
            } catch (RuntimeException unavailable) {
                LOG.warn("product full-text search unavailable; falling back to transactional database filter");
            }
        }
        StringBuilder sql = new StringBuilder("SELECT s.id FROM product_sku s JOIN product_spu p ON p.id=s.spu_id WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (publicOnly) sql.append(" AND s.status='PUBLISHED' AND p.status='PUBLISHED'");
        if (category != null && !category.isBlank()) { sql.append(" AND p.category_id=?"); arguments.add(id(category)); }
        if (matchedSkuIds != null) {
            sql.append(" AND s.id IN (").append(String.join(",", Collections.nCopies(matchedSkuIds.size(), "?")))
                    .append(')');
            matchedSkuIds.stream().map(Long::parseLong).forEach(arguments::add);
        } else if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR CONVERT(s.sku_code USING utf8mb4) LIKE ?)");
            String query = "%" + keyword.strip() + "%"; arguments.add(query); arguments.add(query);
        }
        if (minimumPrice != null) { sql.append(" AND s.price>=?"); arguments.add(minimumPrice); }
        if (maximumPrice != null) { sql.append(" AND s.price<=?"); arguments.add(maximumPrice); }
        if (origin != null && !origin.isBlank()) { sql.append(" AND EXISTS(SELECT 1 FROM product_batch pb WHERE pb.sku_id=s.id AND pb.origin=?)"); arguments.add(origin.strip()); }
        if (!publicOnly && requestedStatus != null) { sql.append(" AND s.status=?"); arguments.add(requestedStatus.name()); }
        sql.append(" ORDER BY p.updated_at DESC,s.id DESC LIMIT ?"); arguments.add(size);
        return jdbc.queryForList(sql.toString(), Long.class, arguments.toArray()).stream().map(value -> get(Long.toString(value), publicOnly)).toList();
    }

    public ProductView get(String sku, boolean publicOnly) {
        String sql = "SELECT s.spu_id,s.id,p.category_id,p.brand_id,p.name,s.sku_code,s.price,s.currency,s.status,s.traceability_code,s.version FROM product_sku s JOIN product_spu p ON p.id=s.spu_id WHERE s.id=?" + (publicOnly ? " AND s.status='PUBLISHED' AND p.status='PUBLISHED'" : "");
        return jdbc.query(sql, result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            long skuId = result.getLong(2);
            var images = jdbc.queryForList("SELECT url FROM product_image WHERE sku_id=? ORDER BY sort_order", String.class, skuId);
            Map<String,String> specifications = new LinkedHashMap<>();
            jdbc.query("SELECT spec_key,spec_value FROM product_specification WHERE sku_id=? ORDER BY sort_order,spec_key", row -> { specifications.put(row.getString(1), row.getString(2)); }, skuId);
            Object brand = result.getObject(4);
            return new ProductView(Long.toString(result.getLong(1)), Long.toString(skuId), Long.toString(result.getLong(3)), brand == null ? null : brand.toString(), result.getString(5), result.getString(6), result.getBigDecimal(7), result.getString(8), ProductStatus.valueOf(result.getString(9)), images, result.getString(10), result.getLong(11), specifications);
        }, id(sku));
    }

    public ProductView changeStatus(String sku, ProductStatus productStatus, long version) {
        long skuId = id(sku);
        return transactions.execute(status -> {
            int changed = jdbc.update("UPDATE product_sku s JOIN product_spu p ON p.id=s.spu_id SET s.status=?,p.status=?,s.version=s.version+1,p.version=p.version+1 WHERE s.id=? AND s.version=?", productStatus.name(), productStatus.name(), skuId, version);
            if (changed < 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            searchJob(skuId);
            return get(sku, false);
        });
    }

    void searchJob(long skuId) { jdbc.update("INSERT INTO product_search_job(id,sku_id) VALUES(?,?)", UUID.randomUUID().toString(), skuId); }
    void replaceSpecifications(long skuId, Map<String,String> values) { jdbc.update("DELETE FROM product_specification WHERE sku_id=?", skuId); int sort=0; for(var entry:new TreeMap<>(values==null?Map.<String,String>of():values).entrySet()) jdbc.update("INSERT INTO product_specification(sku_id,spec_key,spec_value,sort_order) VALUES(?,?,?,?)",skuId,entry.getKey().strip(),entry.getValue().strip(),sort++); }
    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
    private static Long blankId(String value) { return value == null || value.isBlank() ? null : id(value); }
    private static long id(String value) { try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; } catch (Exception failure) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); } }
}
