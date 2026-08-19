package com.parentalcontrol.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

  @Test
  void setAllowedOrigins_splitsCommaSeparatedOrigins() {
    CorsProperties props = new CorsProperties();
    props.setAllowedOrigins(List.of("http://localhost:5173,http://127.0.0.1:5173"));

    assertThat(props.getAllowedOrigins())
        .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
  }

  @Test
  void setAllowedOrigins_trimsAndDropsBlanks() {
    CorsProperties props = new CorsProperties();
    props.setAllowedOrigins(List.of(" http://localhost:5173 ", "", "http://127.0.0.1:5173"));

    assertThat(props.getAllowedOrigins())
        .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
  }
}
