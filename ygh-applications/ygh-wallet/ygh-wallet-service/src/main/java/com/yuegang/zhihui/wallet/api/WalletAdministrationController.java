package com.yuegang.zhihui.wallet.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.wallet.security.WalletAdminVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallet")
public final class WalletAdministrationController {
    private final JdbcTemplate jdbc;private final WalletAdminVerifier verifier;
    public WalletAdministrationController(DataSource dataSource,WalletAdminVerifier verifier){jdbc=new JdbcTemplate(dataSource);this.verifier=verifier;}
    @GetMapping("/accounts") ApiResponse<List<WalletAdminAccountView>>accounts(@RequestParam(defaultValue="100")int limit,HttpServletRequest request){verifier.require(request);var result=jdbc.query("SELECT user_id,available_balance,frozen_balance,currency,status,version FROM wallet_account ORDER BY updated_at DESC LIMIT ?",(row,index)->new WalletAdminAccountView(Long.toString(row.getLong(1)),row.getBigDecimal(2),row.getBigDecimal(3),row.getString(4),row.getString(5),row.getLong(6)),bounded(limit));return ApiResponse.success(result,TraceIdResolver.resolve(request));}
    @GetMapping("/transactions") ApiResponse<List<WalletAdminTransactionView>>transactions(@RequestParam(defaultValue="100")int limit,HttpServletRequest request){verifier.require(request);var result=jdbc.query("SELECT id,user_id,type,status,amount,currency,reference_id,created_at FROM wallet_transaction ORDER BY created_at DESC LIMIT ?",(row,index)->new WalletAdminTransactionView(Long.toString(row.getLong(1)),Long.toString(row.getLong(2)),row.getString(3),row.getString(4),row.getBigDecimal(5),row.getString(6),row.getString(7),row.getTimestamp(8).toLocalDateTime().atOffset(ZoneOffset.UTC)),bounded(limit));return ApiResponse.success(result,TraceIdResolver.resolve(request));}
    private static int bounded(int limit){return Math.max(1,Math.min(limit,500));}
}
