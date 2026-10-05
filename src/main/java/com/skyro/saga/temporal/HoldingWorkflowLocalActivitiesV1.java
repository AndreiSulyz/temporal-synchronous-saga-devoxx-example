package com.skyro.saga.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HoldingWorkflowLocalActivitiesV1 {

    String TASK_QUEUE = "saga-demo-local-holding-queue-v1";

    @WorkflowMethod
    void holdAmount(String operationId);

}
