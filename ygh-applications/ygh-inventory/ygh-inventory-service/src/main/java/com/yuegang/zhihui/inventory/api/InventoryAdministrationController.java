package com.yuegang.zhihui.inventory.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.inventory.security.InventoryAdminVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public final class InventoryAdministrationController {
    private final JdbcTemplate jdbc;
    private final InventoryAdminVerifier verifier;
    public InventoryAdministrationController(DataSource dataSource, InventoryAdminVerifier verifier) { this.jdbc=new JdbcTemplate(dataSource);this.verifier=verifier; }

    @GetMapping
    ApiResponse<List<InventoryView>> list(@RequestParam(defaultValue="100") int limit, HttpServletRequest request) {
        verifier.require(request);
        int bounded=Math.max(1,Math.min(limit,500));
        List<InventoryView> result=jdbc.query("SELECT sku_id,available_quantity,locked_quantity,sold_quantity,version FROM inventory_stock ORDER BY sku_id LIMIT ?",
                (row,index)->new InventoryView(Long.toString(row.getLong(1)),row.getLong(2),row.getLong(3),row.getLong(4),row.getLong(5)),bounded);
        return ApiResponse.success(result, TraceIdResolver.resolve(request));
    }
}
