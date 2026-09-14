package com.yuegang.zhihui.search.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.Set;

public final class SearchInternalSecurity {
    private static final Set<String> ALLOWED = Set.of("ygh-ai-service", "ygh-knowledge-service", "ygh-product-service", "ygh-admin-service");
    private final InternalServiceSignature signatures;
    public SearchInternalSecurity(byte[] secret){signatures=new InternalServiceSignature(secret,Clock.systemUTC(),Duration.ofSeconds(30));}
    public void verify(HttpServletRequest request){try{String service=header(request,"X-YGH-Service");if(!ALLOWED.contains(service))throw failure();Instant timestamp=Instant.ofEpochMilli(Long.parseLong(header(request,"X-YGH-Service-Timestamp")));var metadata=new InternalServiceSignature.Metadata(service,request.getMethod(),request.getRequestURI(),timestamp);if(!signatures.verify(metadata,header(request,"X-YGH-Service-Signature")))throw failure();}catch(BusinessException e){throw e;}catch(RuntimeException e){throw failure();}}
    private static String header(HttpServletRequest request,String name){String value=request.getHeader(name);if(value==null||value.isBlank())throw failure();return value;}
    private static BusinessException failure(){return new BusinessException(ErrorCode.UNAUTHENTICATED);}
}
