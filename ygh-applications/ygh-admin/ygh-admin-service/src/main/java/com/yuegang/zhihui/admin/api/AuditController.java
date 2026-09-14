package com.yuegang.zhihui.admin.api;

import com.yuegang.zhihui.admin.application.AuditQueryService;
import com.yuegang.zhihui.admin.security.AdminUserVerifier;
import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public final class AuditController {
    private final AuditQueryService service;
    private final AdminUserVerifier users;
    public AuditController(AuditQueryService service, AdminUserVerifier users) { this.service = service; this.users = users; }

    @GetMapping
    ApiResponse<List<AuditLogView>> query(@RequestParam(required = false) String userId,
                                          @RequestParam(required = false) String module,
                                          @RequestParam(required = false) String action,
                                          @RequestParam(required = false) String result,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                          @RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        users.verify(request);
        return ApiResponse.success(service.query(userId, module, action, result, from, to, limit), TraceIdResolver.resolve(request));
    }
}
