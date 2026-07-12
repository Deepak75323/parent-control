package com.parentalcontrol.observability;

import com.parentalcontrol.common.security.AuthenticatedUser;
import com.parentalcontrol.common.security.SecurityUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER_CORRELATION = "X-Correlation-Id";
  public static final String HEADER_STREAM_TRACE = StreamTraceLogger.HEADER_TRACE_ID;
  public static final String HEADER_TRACE = "traceparent";

  private final Tracer tracer;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String correlationId = request.getHeader(HEADER_CORRELATION);
      if (correlationId == null || correlationId.isBlank()) {
        correlationId = CorrelationContext.newCorrelationId();
      }
      CorrelationContext.setCorrelationId(correlationId);
      response.setHeader(HEADER_CORRELATION, correlationId);

      String streamTraceId = request.getHeader(HEADER_STREAM_TRACE);
      if (streamTraceId != null && !streamTraceId.isBlank()) {
        StreamTraceLogger.setTraceId(streamTraceId);
      } else {
        StreamTraceLogger.setTraceId(correlationId);
      }
      response.setHeader(HEADER_STREAM_TRACE, StreamTraceLogger.traceId());

      Span current = tracer.currentSpan();
      if (current != null) {
        CorrelationContext.setTraceId(current.context().traceId());
        CorrelationContext.setSpanId(current.context().spanId());
      }

      SecurityUtils.currentUser().ifPresent(this::enrichMdc);

      filterChain.doFilter(request, response);
    } finally {
      CorrelationContext.clear();
    }
  }

  private void enrichMdc(AuthenticatedUser user) {
    if (user.getDeviceId() != null) {
      CorrelationContext.setDeviceId(user.getDeviceId());
    }
    if (user.getId() != null) {
      CorrelationContext.setUserId(user.getId());
    }
  }
}
