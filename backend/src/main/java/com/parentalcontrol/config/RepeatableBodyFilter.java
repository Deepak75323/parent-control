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
    if (request instanceof HttpServletRequest httpRequest
        && "POST".equalsIgnoreCase(httpRequest.getMethod())
        && httpRequest.getRequestURI() != null
        && httpRequest.getRequestURI().startsWith(ApiConstants.API_V1_PREFIX)) {
      chain.doFilter(new RepeatableBodyRequestWrapper(httpRequest), response);
      return;
    }
    chain.doFilter(request, response);
  }
}
