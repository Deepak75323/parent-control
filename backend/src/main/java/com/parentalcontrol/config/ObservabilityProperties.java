package com.parentalcontrol.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.observability")
public class ObservabilityProperties {

    /** Allow unauthenticated GET /actuator/prometheus (dev only). */
    private boolean prometheusPublic = false;

    /** Shared secret for Prometheus scrape via X-Metrics-Token header. */
    private String metricsScrapeToken = "";

    /** Emit structured JSON logs (prod default). */
    private boolean jsonLogging = false;

    /** OTLP trace export enabled. */
    private boolean tracingEnabled = false;

    /** Trace sampling probability 0.0–1.0. */
    private double traceSamplingProbability = 0.1;
}
