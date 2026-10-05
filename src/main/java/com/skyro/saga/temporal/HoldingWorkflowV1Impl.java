package com.skyro.saga.temporal;

import com.skyro.saga.temporal.activity.HoldingActivity;
import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

@WorkflowImpl(taskQueues = HoldingWorkflowV1.TASK_QUEUE)
public class HoldingWorkflowV1Impl implements HoldingWorkflowV1 {

    private final HoldingActivity activity = Workflow.newActivityStub(
            HoldingActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(1))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build()
    );

    @Override
    public void holdAmount(String operationId) {
        Saga saga = new Saga(
                new Saga.Options.Builder()
                        .setParallelCompensation(true)
                        .build()
        );

        try {
            activity.holdCredit(operationId, 1_000);
            saga.addCompensation(activity::unholdCredit, operationId);

            activity.holdDebit(operationId, 5_000);
            saga.addCompensation(activity::unholdDebit, operationId);

        } catch (Exception e) {
            saga.compensate();
            throw Workflow.wrap(e);
        }
    }

}
