package com.parentalcontrol.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepeatableBodyFilterTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;

  private final RepeatableBodyFilter filter = new RepeatableBodyFilter();

  @Test
  void passesMultipartPostsThroughWithoutBuffering() throws Exception {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/chat/rooms/x/messages/media");
    when(request.getContentType()).thenReturn("multipart/form-data; boundary=abc");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(same(request), same(response));
    verify(chain, never()).doFilter(any(RepeatableBodyRequestWrapper.class), any());
  }

  @Test
  void wrapsJsonPostsForHmacReplay() throws Exception {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/device/heartbeat");
    when(request.getContentType()).thenReturn("application/json");
    when(request.getInputStream())
        .thenReturn(
            new ServletInputStream() {
              private final byte[] body = "{}".getBytes();
              private int index;

              @Override
              public boolean isFinished() {
                return index >= body.length;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener readListener) {}

              @Override
              public int read() {
                return index < body.length ? body[index++] & 0xff : -1;
              }
            });

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain).doFilter(captor.capture(), same(response));
    assertThat(captor.getValue()).isInstanceOf(RepeatableBodyRequestWrapper.class);
  }
}
