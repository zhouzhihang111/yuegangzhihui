package com.yuegang.zhihui.auth.api;

import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;

public final class AuthTrustedUserContextResolver {
    private final InternalUserContextSignature signatures;
    public AuthTrustedUserContextResolver(byte[] secret,Clock clock){signatures=new InternalUserContextSignature(secret,clock,Duration.ofSeconds(30));}
    public CurrentUserPrincipal resolve(HttpServletRequest request){
        try{
            String user=required(request,"X-YGH-User-Id");var roles=values(request.getHeader("X-YGH-Roles"));var permissions=values(request.getHeader("X-YGH-Permissions"));
            var metadata=new InternalUserContextSignature.Metadata(user,roles,permissions,required(request,"X-Trace-Id"),required(request,"X-Request-Id"),request.getMethod(),request.getRequestURI(),Instant.ofEpochMilli(Long.parseLong(required(request,"X-YGH-User-Context-Timestamp"))));
            if(!signatures.verify(metadata,required(request,"X-YGH-User-Context-Signature")))throw failure();
            long id=Long.parseLong(user);if(id<=0)throw failure();return new CurrentUserPrincipal(user,new LinkedHashSet<>(roles),new LinkedHashSet<>(permissions));
        }catch(BusinessException e){throw e;}catch(RuntimeException e){throw failure();}
    }
    private static String required(HttpServletRequest r,String n){String v=r.getHeader(n);if(v==null||v.isBlank())throw failure();return v;}
    private static List<String> values(String v){return v==null||v.isBlank()?List.of():List.of(v.split(",",-1));}
    private static BusinessException failure(){return new BusinessException(ErrorCode.UNAUTHENTICATED);}
}
