package com.yuegang.zhihui.common.security;

/** Publishes security decisions to an audit sink without coupling to MQ. */
@FunctionalInterface
public interface SecurityAuditPublisher {

    void publish(SecurityAuditEvent event);
}
