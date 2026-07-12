package com.parentalcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.smtp")
public class SmtpProperties {

  private String host = "";
  private int port = 587;
  private String username = "";
  private String password = "";
  private String fromAddress = "noreply@parentalcontrol.local";
}
