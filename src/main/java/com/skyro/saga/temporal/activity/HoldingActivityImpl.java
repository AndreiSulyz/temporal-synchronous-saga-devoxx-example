package com.skyro.saga.temporal.activity;

import com.skyro.saga.product.ProductClient;
import com.skyro.saga.temporal.HoldingWorkflowV1;
import io.temporal.activity.Activity;
import io.temporal.common.CancellationToken;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = HoldingWorkflowV1.TASK_QUEUE)
public class HoldingActivityImpl implements HoldingActivity {

    private final ProductClient productClient;

    public HoldingActivityImpl(ProductClient productClient) {
        this.productClient = productClient;
    }

    @Override
    public String holdCredit(String operationId, long amount) {
        return productClient.holdCredit(operationId, amount);
    }

    @Override
    public void unholdCredit(String operationId) {
        productClient.unholdCredit(operationId);
    }

    @Override
    public String holdDebit(String operationId, long amount) {
        return productClient.holdDebit(operationId, amount);
    }

    @Override
    public void unholdDebit(String operationId) {
        productClient.unholdDebit(operationId);
    }

}
