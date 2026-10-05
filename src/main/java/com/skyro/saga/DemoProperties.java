package com.skyro.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Each product URL is named after the call it carries and is delayed by the latency of the same name. */
@ConfigurationProperties("demo")
public record DemoProperties(
        String holdProductUrl,
        String compensationProductUrl,
        String toxiproxyAdminUrl,
        Duration holdLatency,
        Duration compensationLatency
) { }
