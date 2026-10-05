package com.skyro.saga.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Stands in for the Credit and Debit systems of the article. Every request is logged in the order
 * the system really received it, which is what makes the race visible: the compensating unhold can
 * arrive while the hold it should reverse is still travelling.
 */
@Component
public class ProductLedger {

    private static final Logger log = LoggerFactory.getLogger(ProductLedger.class);

    private final Map<String, Long> holds = new HashMap<>();

    public synchronized String hold(String system, String operationId, long amount) {
        String result = holds.putIfAbsent(key(system, operationId), amount) == null
                ? "APPLIED"
                : "ALREADY_HELD";

        log.info("skyro <- HOLD {} {} amount={} => {}", system, operationId, amount, result);

        return result;
    }

    public synchronized String unhold(String system, String operationId) {
        Long released = holds.remove(key(system, operationId));
        String result = released == null ? "NOTHING_TO_RELEASE" : "RELEASED";
        log.info("skyro <- UNHOLD {} {} => {}", system, operationId, result);

        return result;
    }

    private String key(String system, String operationId) {
        return system + "|" + operationId;
    }

}
