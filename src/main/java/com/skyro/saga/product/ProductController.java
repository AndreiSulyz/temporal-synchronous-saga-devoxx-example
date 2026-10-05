package com.skyro.saga.product;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product/{system}")
public class ProductController {

    private final ProductLedger ledger;

    public record ProductRequest(String operationId, long amount) { }

    public ProductController(ProductLedger ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/hold")
    public String hold(@PathVariable String system, @RequestBody ProductRequest request) {
        return ledger.hold(system, request.operationId(), request.amount());
    }

    @PostMapping("/unhold")
    public String unhold(@PathVariable String system, @RequestBody ProductRequest request) {
        return ledger.unhold(system, request.operationId());
    }
}
