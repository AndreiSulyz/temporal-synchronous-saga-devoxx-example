package com.skyro.saga.toxiproxy;

import com.skyro.saga.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class ToxiproxyAdmin {

    private static final Logger log = LoggerFactory.getLogger(ToxiproxyAdmin.class);

    private static final String TOXIC = "latency";

    private final RestClient client;
    private final DemoProperties properties;

    public ToxiproxyAdmin(DemoProperties properties) {
        this.client = RestClient.create(properties.toxiproxyAdminUrl());
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyLatencies() {
        applyLatency("product-hold", properties.holdLatency());
        applyLatency("product-compensation", properties.compensationLatency());

        log.info("credit hold is delayed by {}, every other product call by {}", properties.holdLatency(), properties.compensationLatency());

        if (properties.compensationLatency().compareTo(properties.holdLatency()) >= 0) {
            log.warn("compensation is not faster than the hold, so it will reach the product last and the race will not be visible");
        }
    }

    private void applyLatency(String proxy, Duration latency) {
        removeLatency(proxy);
        try {
            client.post()
                    .uri("/proxies/{proxy}/toxics", proxy)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "name", TOXIC,
                            "type", "latency",
                            "stream", "upstream",
                            "attributes", Map.of("latency", latency.toMillis(), "jitter", 0)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                    "Toxiproxy is not reachable at %s. Start the stack with: docker compose up -d --wait"
                            .formatted(properties.toxiproxyAdminUrl()), e);
        }
    }

    private void removeLatency(String proxy) {
        client.delete()
                .uri("/proxies/{proxy}/toxics/{toxic}", proxy, TOXIC)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> { })
                .toBodilessEntity();
    }
}
