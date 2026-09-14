package com.yuegang.zhihui.inventory.security;
import com.yuegang.zhihui.common.core.*;import com.yuegang.zhihui.common.security.InternalServiceSignature;import jakarta.servlet.http.HttpServletRequest;import java.time.*;import java.util.Set;
public final class InventoryInternalSecurity{
 private static final Set<String>ALLOWED=Set.of("ygh-order-service","ygh-ai-service","ygh-admin-service");private final InternalServiceSignature signatures;
 public InventoryInternalSecurity(byte[]key){signatures=new InternalServiceSignature(key,Clock.systemUTC(),Duration.ofSeconds(30));}
 public void verify(HttpServletRequest request){try{String service=header(request,"X-YGH-Service");if(!ALLOWED.contains(service))throw fail();Instant time=Instant.ofEpochMilli(Long.parseLong(header(request,"X-YGH-Service-Timestamp")));var metadata=new InternalServiceSignature.Metadata(service,request.getMethod(),request.getRequestURI(),time);if(!signatures.verify(metadata,header(request,"X-YGH-Service-Signature")))throw fail();}catch(BusinessException failure){throw failure;}catch(Exception failure){throw fail();}}
 private static String header(HttpServletRequest request,String name){String value=request.getHeader(name);if(value==null||value.isBlank())throw fail();return value;}private static BusinessException fail(){return new BusinessException(ErrorCode.UNAUTHENTICATED);}
}
