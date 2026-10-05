package com.skyro.saga.product;

import com.skyro.saga.product.ProductController.ProductRequest;
import com.skyro.saga.DemoProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the product through Toxiproxy so that latency can be injected into the wire, the way an
 * unhealthy external service would behave.
 * <p>
 * The credit hold gets its own link because a latency toxic applies to every connection of its
 * proxy: sending the compensation through the same one would delay the unhold just as much and
 * hide the race instead of exposing it.
 */
@Component
public class ProductClient {

    private final RestClient holdLink;
    private final RestClient compensationLink;

    public ProductClient(DemoProperties properties) {
        this.holdLink = RestClient.create(properties.holdProductUrl());
        this.compensationLink = RestClient.create(properties.compensationProductUrl());
    }

    public String holdCredit(String operationId, long amount) {
        return call(holdLink, "credit", "hold", operationId, amount);
    }

    public void unholdCredit(String operationId) {
        call(compensationLink, "credit", "unhold", operationId, 0);
    }

    public String holdDebit(String operationId, long amount) {
        return call(holdLink, "debit", "hold", operationId, amount);
    }

    public void unholdDebit(String operationId) {
        call(compensationLink, "debit", "unhold", operationId, 0);
    }

    private String call(RestClient client, String system, String action, String operationId, long amount) {
        return client.post()
                .uri("/product/{system}/{action}", system, action)
                .body(new ProductRequest(operationId, amount))
                .retrieve()
                .body(String.class);
    }

}
