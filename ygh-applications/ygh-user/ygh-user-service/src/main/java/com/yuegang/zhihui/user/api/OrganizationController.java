package com.yuegang.zhihui.user.api;

import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.user.application.OrganizationService;
import com.yuegang.zhihui.user.security.TrustedUserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organization")
public final class OrganizationController {
    private final OrganizationService service; private final TrustedUserContextResolver users;
    public OrganizationController(OrganizationService service,TrustedUserContextResolver users){this.service=service;this.users=users;}
    @GetMapping("/departments") ApiResponse<List<DepartmentView>> departments(HttpServletRequest request){require(request,"organization:read");return ok(service.departments(),request);}
    @PostMapping("/departments") ApiResponse<DepartmentView> createDepartment(@Valid @RequestBody CreateDepartmentRequest body,HttpServletRequest request){require(request,"organization:write");return ok(service.createDepartment(body),request);}
    @GetMapping("/positions") ApiResponse<List<PositionView>> positions(HttpServletRequest request){require(request,"organization:read");return ok(service.positions(),request);}
    @PostMapping("/positions") ApiResponse<PositionView> createPosition(@Valid @RequestBody CreatePositionRequest body,HttpServletRequest request){require(request,"organization:write");return ok(service.createPosition(body),request);}
    @GetMapping("/employees") ApiResponse<List<EmployeeView>> employees(HttpServletRequest request){require(request,"employee:read");return ok(service.employees(),request);}
    @PostMapping("/employees") ApiResponse<EmployeeView> createEmployee(@Valid @RequestBody CreateEmployeeRequest body,HttpServletRequest request){require(request,"employee:write");return ok(service.createEmployee(body),request);}
    @PutMapping("/employees/{id}/positions") ApiResponse<EmployeeView> positions(@PathVariable String id,@RequestBody Set<String> positions,HttpServletRequest request){require(request,"employee:write");return ok(service.replacePositions(id,positions),request);}
    @PutMapping("/employees/{id}/status/{status}") ApiResponse<EmployeeView> status(@PathVariable String id,@PathVariable String status,HttpServletRequest request){require(request,"employee:write");return ok(service.changeStatus(id,status),request);}
    private void require(HttpServletRequest request,String permission){CurrentUserPrincipal principal=users.resolve(request);if(!principal.hasRole("ADMIN")&&!principal.hasPermission(permission))throw new BusinessException(ErrorCode.PERMISSION_DENIED);}
    private static<T>ApiResponse<T> ok(T data,HttpServletRequest request){return ApiResponse.success(data,TraceIdResolver.resolve(request));}
}
