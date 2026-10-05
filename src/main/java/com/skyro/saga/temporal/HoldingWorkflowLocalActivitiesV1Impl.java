package com.skyro.saga.temporal;

import com.skyro.saga.temporal.activity.HoldingActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = HoldingWorkflowV1.TASK_QUEUE)
public class HoldingWorkflowLocalActivitiesV1Impl implements HoldingWorkflowLocalActivitiesV1 {

    private final HoldingActivity holdActivities = Workflow.newLocalActivityStub(
            HoldingActivity.class,
            LocalActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(1))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build()
    );

    private final HoldingActivity unholdActivities = Workflow.newActivityStub(
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
            saga.addCompensation(unholdActivities::unholdCredit, operationId);
            holdActivities.holdCredit(operationId, 1_000);

            saga.addCompensation(unholdActivities::unholdDebit, operationId);
            holdActivities.holdDebit(operationId, 5_000);

            // LocalActivities don't throw any exceptions if we try to run them from a Canceled Workflow
            CancellationScope.throwCanceled();

        } catch (Exception e) {
            Workflow.newDetachedCancellationScope(saga::compensate).run();
            throw Workflow.wrap(e);
        }
    }

}
