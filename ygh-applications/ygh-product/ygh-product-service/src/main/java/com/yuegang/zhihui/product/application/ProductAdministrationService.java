package com.yuegang.zhihui.product.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.product.api.*;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class ProductAdministrationService {
    private final JdbcTemplate jdbc;
    private final ProductService products;
    private final ObjectMapper json;

    public ProductAdministrationService(DataSource dataSource, ProductService products, ObjectMapper json) {
        jdbc = new JdbcTemplate(dataSource); this.products = products; this.json = json;
    }

    @Transactional
    public ProductView update(String sku, UpdateProductRequest command) {
        long skuId = id(sku);
        Object[] before = jdbc.query("SELECT s.price,s.spu_id FROM product_sku s WHERE s.id=?", result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return new Object[]{result.getBigDecimal(1), result.getLong(2)};
        }, skuId);
        int changed = jdbc.update("UPDATE product_sku s JOIN product_spu p ON p.id=s.spu_id SET p.category_id=?,p.brand_id=?,p.name=?,p.description=?,s.price=?,s.currency=?,s.traceability_code=?,s.version=s.version+1,p.version=p.version+1 WHERE s.id=? AND s.version=?", id(command.categoryId()), blank(command.brandId()), command.name(), command.description(), command.price(), command.currency(), command.traceabilityCode(), skuId, command.version());
        if (changed < 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        jdbc.update("DELETE FROM product_image WHERE sku_id=?", skuId);
        int sort = 0;
        for (String url : command.images()) jdbc.update("INSERT INTO product_image(id,spu_id,sku_id,url,sort_order) VALUES(?,?,?,?,?)", next(), before[1], skuId, url, sort++);
        products.replaceSpecifications(skuId, command.specifications());
        if (((java.math.BigDecimal) before[0]).compareTo(command.price()) != 0) jdbc.update("INSERT INTO product_price_history(id,sku_id,old_price,new_price,currency) VALUES(?,?,?,?,?)", next(), skuId, before[0], command.price(), command.currency());
        event(skuId, "PRODUCT_UPDATED", Map.of("skuId", sku, "price", command.price(), "currency", command.currency()));
        products.searchJob(skuId);
        return products.get(sku, false);
    }

    @Transactional
    public ProductBatchView batch(String sku, SaveProductBatchRequest command) {
        long skuId = id(sku);
        if (command.producedOn() != null && command.expiresOn() != null && command.expiresOn().isBefore(command.producedOn())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        long batchId = next();
        jdbc.update("INSERT INTO product_batch(id,sku_id,batch_no,origin,proof_url,produced_on,expires_on,trace_description) VALUES(?,?,?,?,?,?,?,?)", batchId, skuId, command.batchNo(), command.origin(), command.proofUrl(), command.producedOn(), command.expiresOn(), command.traceDescription());
        event(skuId, "PRODUCT_BATCH_CREATED", Map.of("skuId", sku, "batchId", Long.toString(batchId), "batchNo", command.batchNo()));
        products.searchJob(skuId);
        return new ProductBatchView(Long.toString(batchId), sku, command.batchNo(), command.origin(), command.proofUrl(), command.producedOn(), command.expiresOn(), command.traceDescription());
    }

    public List<ProductBatchView> batches(String sku) {
        return jdbc.query("SELECT id,sku_id,batch_no,origin,proof_url,produced_on,expires_on,trace_description FROM product_batch WHERE sku_id=? ORDER BY created_at DESC", (result, row) -> new ProductBatchView(Long.toString(result.getLong(1)), Long.toString(result.getLong(2)), result.getString(3), result.getString(4), result.getString(5), date(result.getDate(6)), date(result.getDate(7)), result.getString(8)), id(sku));
    }

    private void event(long skuId, String type, Object payload) { jdbc.update("INSERT INTO product_outbox(id,aggregate_id,event_type,payload_json) VALUES(?,?,?,?)", UUID.randomUUID().toString(), Long.toString(skuId), type, write(payload)); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private static LocalDate date(java.sql.Date value) { return value == null ? null : value.toLocalDate(); }
    private static Long blank(String value) { return value == null || value.isBlank() ? null : id(value); }
    private static long id(String value) { try { long parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; } catch (Exception failure) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); } }
    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
}
