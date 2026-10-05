package com.skyro.saga.controller;

import com.skyro.saga.service.ProdHoldingAmountOperationService;
import com.skyro.saga.temporal.HoldingWorkflowLocalActivitiesV1;
import com.skyro.saga.temporal.HoldingWorkflowParallelExecutionV1;
import com.skyro.saga.temporal.HoldingWorkflowV1;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/holdings-parallel")
public class HoldingParallelController {

    private final WorkflowClient workflowClient;
    private final ProdHoldingAmountOperationService holdingAmountOperationService;

    public HoldingParallelController(WorkflowClient workflowClient, ProdHoldingAmountOperationService holdingAmountOperationService) {
        this.workflowClient = workflowClient;
        this.holdingAmountOperationService = holdingAmountOperationService;
    }

    @PostMapping
    public String start() {
        String operationId = "holding-" + System.currentTimeMillis();
        HoldingWorkflowParallelExecutionV1 workflow = workflowClient.newWorkflowStub(
                HoldingWorkflowParallelExecutionV1.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(operationId)
                        .setTaskQueue(HoldingWorkflowV1.TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                        .build()
        );

        WorkflowClient.start(workflow::holdAmount, operationId);

        return operationId;
    }

    /**
     * Blocks until the workflow finishes. {@code timeoutMillis} stands in for the caller's deadline
     * and {@code sourceId} for the request id a client would retry with.
     */
    @PostMapping("/sync")
    public String startAndWait(@RequestParam(required = false) String sourceId,
                               @RequestParam(required = false) Long timeoutMillis) {

        return holdingAmountOperationService.holdAmount(
                sourceId == null ? String.valueOf(System.currentTimeMillis()) : sourceId
        );
    }

}
