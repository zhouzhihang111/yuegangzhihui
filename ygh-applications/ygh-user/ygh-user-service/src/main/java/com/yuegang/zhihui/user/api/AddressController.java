package com.yuegang.zhihui.user.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.user.application.AddressService;
import com.yuegang.zhihui.user.security.TrustedUserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me/addresses")
public final class AddressController {
    private final AddressService service; private final TrustedUserContextResolver users;
    public AddressController(AddressService service,TrustedUserContextResolver users){this.service=service;this.users=users;}
    @GetMapping public ApiResponse<List<AddressView>> list(HttpServletRequest request){return ok(service.list(user(request)),request);}
    @PostMapping public ApiResponse<AddressView> create(@Valid @RequestBody CreateAddressRequest body,HttpServletRequest request){return ok(service.create(user(request),body),request);}
    @PutMapping("/{id}") public ApiResponse<AddressView> update(@PathVariable String id,@Valid @RequestBody UpdateAddressRequest body,HttpServletRequest request){return ok(service.update(user(request),id,body),request);}
    @DeleteMapping("/{id}") public ApiResponse<AddressOperationResponse> delete(@PathVariable String id,@RequestParam long version,HttpServletRequest request){return ok(service.delete(user(request),id,version),request);}
    @PutMapping("/{id}/default") public ApiResponse<AddressView> makeDefault(@PathVariable String id,@RequestParam long version,HttpServletRequest request){return ok(service.makeDefault(user(request),id,version),request);}
    private String user(HttpServletRequest request){return users.resolve(request).userId();}
    private static <T> ApiResponse<T> ok(T data,HttpServletRequest request){return ApiResponse.success(data,TraceIdResolver.resolve(request));}
}
