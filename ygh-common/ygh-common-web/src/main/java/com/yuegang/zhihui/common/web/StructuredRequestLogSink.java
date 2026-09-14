package com.yuegang.zhihui.common.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Default structured application-log sink for sanitized request events. */
final class StructuredRequestLogSink implements RequestLogSink {

    private static final Log LOGGER = LogFactory.getLog("ygh.request");

    @Override
    public void accept(RequestLogEvent event) {
        LOGGER.info("request_completed"
                + " traceId=" + event.traceId()
                + " requestId=" + event.requestId()
                + " method=" + event.method()
                + " path=" + event.path()
                + " status=" + event.status()
                + " durationMs=" + event.durationMs()
                + " headers=" + event.headers());
    }
}
