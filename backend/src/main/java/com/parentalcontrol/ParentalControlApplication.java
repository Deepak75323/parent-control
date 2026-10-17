package com.parentalcontrol;

import com.parentalcontrol.common.security.JwtProperties;
import com.parentalcontrol.config.ChatAttachmentProperties;
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.config.ChatDecryptProperties;
import com.parentalcontrol.config.DeviceCommandProperties;
import com.parentalcontrol.config.DeviceTrustProperties;
import com.parentalcontrol.config.FcmProperties;
import com.parentalcontrol.config.ObservabilityProperties;
import com.parentalcontrol.config.PasswordResetProperties;
import com.parentalcontrol.config.PlayIntegrityProperties;
import com.parentalcontrol.config.SecurityProperties;
import com.parentalcontrol.config.StompBrokerProperties;
import com.parentalcontrol.config.StompClusterProperties;
import com.parentalcontrol.config.WebSocketProperties;
import com.parentalcontrol.config.WebrtcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.parentalcontrol")
@EntityScan(basePackages = "com.parentalcontrol")
@EnableJpaRepositories(basePackages = "com.parentalcontrol")
@EnableConfigurationProperties({
  JwtProperties.class,
  WebSocketProperties.class,
  StompBrokerProperties.class,
  StompClusterProperties.class,
  WebrtcProperties.class,
  FcmProperties.class,
  PlayIntegrityProperties.class,
  DeviceTrustProperties.class,
  ObservabilityProperties.class,
  PasswordResetProperties.class,
  DeviceCommandProperties.class,
  ChatAttachmentProperties.class,
  ChatAutoDeleteProperties.class,
  ChatDecryptProperties.class,
  SecurityProperties.class
})
@EnableScheduling
public class ParentalControlApplication {

  public static void main(String[] args) {
    SpringApplication.run(ParentalControlApplication.class, args);
  }
}
