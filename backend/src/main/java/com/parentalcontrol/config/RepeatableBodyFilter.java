package com.parentalcontrol.config;

import com.parentalcontrol.common.constant.ApiConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/** Buffers POST bodies under /api/v1/ for HMAC verification. */
public class RepeatableBodyFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest httpRequest)) {
      chain.doFilter(request, response);
      return;
    }
    if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    String uri = httpRequest.getRequestURI();
    if (uri == null || !uri.startsWith(ApiConstants.API_V1_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    // Buffering the body breaks servlet multipart parsing (chat media uploads).
    String contentType = httpRequest.getContentType();
    if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
      chain.doFilter(request, response);
      return;
    }
    chain.doFilter(new RepeatableBodyRequestWrapper(httpRequest), response);
  }
}
