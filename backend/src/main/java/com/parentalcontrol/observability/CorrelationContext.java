package com.parentalcontrol.observability;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/** Thread-local correlation identifiers propagated through HTTP → logs → downstream calls. */
public final class CorrelationContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String DEVICE_ID = "deviceId";
    public static final String USER_ID = "userId";
    public static final String COMMAND_ID = "commandId";
    public static final String SESSION_ID = "sessionId";

    private CorrelationContext() {}

    public static String correlationId() {
        return MDC.get(CORRELATION_ID);
    }

    public static Optional<String> optionalCorrelationId() {
        return Optional.ofNullable(correlationId());
    }

    public static void setCorrelationId(String id) {
        if (id != null && !id.isBlank()) {
            MDC.put(CORRELATION_ID, id);
        }
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    public static void setSpanId(String spanId) {
        if (spanId != null && !spanId.isBlank()) {
            MDC.put(SPAN_ID, spanId);
        }
    }

    public static void setDeviceId(UUID deviceId) {
        if (deviceId != null) {
            MDC.put(DEVICE_ID, deviceId.toString());
        }
    }

    public static void setUserId(UUID userId) {
        if (userId != null) {
            MDC.put(USER_ID, userId.toString());
        }
    }

    public static void setCommandId(UUID commandId) {
        if (commandId != null) {
            MDC.put(COMMAND_ID, commandId.toString());
        }
    }

    public static void setSessionId(UUID sessionId) {
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId.toString());
        }
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
        MDC.remove(DEVICE_ID);
        MDC.remove(USER_ID);
        MDC.remove(COMMAND_ID);
        MDC.remove(SESSION_ID);
        MDC.remove(StreamTraceLogger.MDC_STREAM_TRACE_ID);
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
