package com.skyro.saga.temporal;

import com.skyro.saga.temporal.activity.HoldingActivity;
import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.*;

import java.time.Duration;

@WorkflowImpl(taskQueues = HoldingWorkflowV1.TASK_QUEUE)
public class HoldingWorkflowParallelExecutionV1Impl implements HoldingWorkflowParallelExecutionV1 {

    private final HoldingActivity activity = Workflow.newActivityStub(
            HoldingActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(1))
                    .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
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

        saga.addCompensation(activity::unholdCredit, operationId);
        saga.addCompensation(activity::unholdDebit, operationId);

        Promise<String> holdCreditPromise = Async.function(activity::holdCredit, operationId, 1_000L);
        Promise<String> holdDebitPromise = Async.function(activity::holdDebit, operationId, 5_000L);

        try {
            // You can use also cancellableGet, but it throws exception immediately
            // ActivityCancellationType for activities is still important here
            Promise.allOf(holdCreditPromise, holdDebitPromise).get();

            CancellationScope.throwCanceled();

        } catch (Exception e) {
            Workflow.newDetachedCancellationScope(saga::compensate).run();
            throw Workflow.wrap(e);
        }
    }

}
