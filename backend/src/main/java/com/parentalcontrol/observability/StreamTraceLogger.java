package com.parentalcontrol.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Structured end-to-end stream trace logger.
 * Filter logs with {@code TRACE_ID} or {@code PCP_STREAM_TRACE}.
 */
public final class StreamTraceLogger {

    private static final Logger log = LoggerFactory.getLogger("PCP_STREAM_TRACE");
    private static final AtomicInteger step = new AtomicInteger(0);

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_STREAM_TRACE_ID = "streamTraceId";

    private StreamTraceLogger() {}

    public static String resolveOrCreateTraceId() {
        String fromMdc = MDC.get(MDC_STREAM_TRACE_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromCorrelation = CorrelationContext.correlationId();
        if (fromCorrelation != null && !fromCorrelation.isBlank()) {
            MDC.put(MDC_STREAM_TRACE_ID, fromCorrelation);
            return fromCorrelation;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_STREAM_TRACE_ID, generated);
        return generated;
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_STREAM_TRACE_ID, traceId);
            CorrelationContext.setTraceId(traceId);
        }
    }

    public static String traceId() {
        return MDC.get(MDC_STREAM_TRACE_ID);
    }

    public static void info(String component, String state, String fn, String message, Object detail) {
        int n = step.incrementAndGet();
        String trace = traceId();
        String tracePart = trace != null ? "[TRACE_ID=" + trace + "]" : "[TRACE_ID=none]";
        String extra = detail != null ? " " + detail : "";
        log.info("{} [{}] [{}] [{}] #{} {} {}{}",
                tracePart, component, state, fn,
                String.format("%04d", n), java.time.Instant.now(), message, extra);
    }

    public static void info(String component, String state, String fn, String message) {
        info(component, state, fn, message, null);
    }

    public static void warn(String component, String state, String fn, String message, Object detail) {
        int n = step.incrementAndGet();
        String trace = traceId();
        String tracePart = trace != null ? "[TRACE_ID=" + trace + "]" : "[TRACE_ID=none]";
        String extra = detail != null ? " " + detail : "";
        log.warn("{} [{}] [{}] [{}] #{} {} WARN {}{}",
                tracePart, component, state, fn,
                String.format("%04d", n), java.time.Instant.now(), message, extra);
    }

    public static void error(String component, String state, String fn, String message, Throwable t) {
        int n = step.incrementAndGet();
        String trace = traceId();
        String tracePart = trace != null ? "[TRACE_ID=" + trace + "]" : "[TRACE_ID=none]";
        String line = String.format("%s [%s] [%s] [%s] #%04d %s ERROR %s",
                tracePart, component, state, fn, n, java.time.Instant.now(), message);
        if (t != null) {
            log.error(line, t);
        } else {
            log.error(line);
        }
    }

    public static void failureReport(
            String component,
            String fn,
            String state,
            Object input,
            Object output,
            String expected,
            String actual,
            String reason,
            List<String> nextCauses,
            Throwable t) {
        String divider = "=".repeat(50);
        log.error(divider);
        log.error("FAILURE REPORT");
        log.error(divider);
        log.error("TRACE_ID: {}", traceId());
        log.error("Component: {}", component);
        log.error("Function: {}", fn);
        log.error("State: {}", state);
        if (input != null) log.error("Input: {}", input);
        if (output != null) log.error("Output: {}", output);
        if (expected != null) log.error("Expected: {}", expected);
        if (actual != null) log.error("Actual: {}", actual);
        if (reason != null) log.error("Reason: {}", reason);
        if (nextCauses != null) {
            log.error("Next possible causes:");
            nextCauses.forEach(c -> log.error("  - {}", c));
        }
        if (t != null) {
            log.error("Stacktrace:", t);
        }
        log.error(divider);
    }

    public static void injectTraceId(Map<String, String> payload) {
        String trace = traceId();
        if (trace != null && !trace.isBlank()) {
            payload.put("traceId", trace);
        }
    }

    private static volatile StreamState currentStreamState = StreamState.IDLE;
    private static volatile StreamState previousStreamState = StreamState.IDLE;

    public static StreamState getStreamState() {
        return currentStreamState;
    }

    public static void transitionState(String component, StreamState next, String fn, Object detail) {
        StreamState from = previousStreamState;
        previousStreamState = next;
        currentStreamState = next;
        int n = step.incrementAndGet();
        String trace = traceId();
        String tracePart = trace != null ? "[TRACE_ID=" + trace + "]" : "[TRACE_ID=none]";
        String extra = detail != null ? " " + detail : "";
        log.info("{} [STREAM_STATE] {} → {} [{}] [{}] #{} {} {}{}",
                tracePart, from, next, component, fn,
                String.format("%04d", n), java.time.Instant.now(), "state transition", extra);
    }

    public static void transitionState(String component, StreamState next, String fn) {
        transitionState(component, next, fn, null);
    }

    public static void resetStreamState() {
        previousStreamState = StreamState.IDLE;
        currentStreamState = StreamState.IDLE;
    }
}
