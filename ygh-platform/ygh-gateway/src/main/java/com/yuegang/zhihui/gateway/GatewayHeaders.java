package com.yuegang.zhihui.gateway;

/** Edge headers shared only inside the Gateway module. */
interface GatewayHeaders {

    String TRACE_ID = "X-Trace-Id";
    String REQUEST_ID = "X-Request-Id";
    String USER_ID = "X-YGH-User-Id";
    String ROLES = "X-YGH-Roles";
    String PERMISSIONS = "X-YGH-Permissions";
    String CLIENT_IP = "X-YGH-Client-IP";
    String CLIENT_IP_TIMESTAMP = "X-YGH-Client-IP-Timestamp";
    String CLIENT_IP_SIGNATURE = "X-YGH-Client-IP-Signature";
    String USER_CONTEXT_TIMESTAMP = "X-YGH-User-Context-Timestamp";
    String USER_CONTEXT_SIGNATURE = "X-YGH-User-Context-Signature";
}
