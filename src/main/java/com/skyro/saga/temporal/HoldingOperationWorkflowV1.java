package com.skyro.saga.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HoldingOperationWorkflowV1 {

    String TASK_QUEUE = HoldingWorkflowV1.TASK_QUEUE;
    String HOLDING_FAILED = "HOLDING_FAILED";
    String HOLDING_FAILED_CODE = "201231";

    @WorkflowMethod
    void holdAmount(String operationId);

}
