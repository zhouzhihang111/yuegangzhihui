package com.yuegang.zhihui.common.web;

/** Receives sanitized structured request-completion events. */
@FunctionalInterface
public interface RequestLogSink {

    void accept(RequestLogEvent event);
}
