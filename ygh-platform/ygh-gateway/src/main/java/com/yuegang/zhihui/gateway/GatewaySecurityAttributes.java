package com.yuegang.zhihui.gateway;

/** Trusted exchange attributes; values must never be sourced from client headers. */
interface GatewaySecurityAttributes {

    String TRACE_ID = GatewaySecurityAttributes.class.getName() + ".traceId";
    String REQUEST_ID = GatewaySecurityAttributes.class.getName() + ".requestId";
    String AUTHENTICATED_PRINCIPAL = GatewaySecurityAttributes.class.getName() + ".principal";
}
