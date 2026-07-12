package com.parentalcontrol.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Buffers POST body so HMAC verification and @RequestBody can both read it. */
public class RepeatableBodyRequestWrapper extends HttpServletRequestWrapper {

  private final byte[] body;

  public RepeatableBodyRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    body = request.getInputStream().readAllBytes();
  }

  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream input = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return input.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {}

      @Override
      public int read() {
        return input.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
