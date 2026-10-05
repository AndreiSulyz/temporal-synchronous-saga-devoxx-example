package com.skyro.saga.temporal;

import com.skyro.saga.temporal.activity.HoldingActivity;
import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

@WorkflowImpl(taskQueues = HoldingOperationWorkflowV1.TASK_QUEUE)
public class ProdHoldingOperationWorkflowV1Impl implements HoldingOperationWorkflowV1 {

    private static final Logger log = Workflow.getLogger(ProdHoldingOperationWorkflowV1Impl.class);

    private final HoldingActivity activity = Workflow.newActivityStub(
            HoldingActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(1))
                    .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build());

    @Override
    public void holdAmount(String operationId) {
        Saga saga = new Saga(
                new Saga.Options.Builder()
                        .setParallelCompensation(true)
                        .build()
        );

        try {
            saga.addCompensation(activity::unholdCredit, operationId);
            activity.holdCredit(operationId, 1_000);

            saga.addCompensation(activity::unholdDebit, operationId);
            activity.holdDebit(operationId, 5_000);

            CancellationScope.throwCanceled();

        } catch (CanceledFailure e) {
            runCompensation(saga);
            throw e;

        } catch (Exception e) {
            runCompensation(saga);

            throw ApplicationFailure.newFailureWithCause(
                    "Holding operation failed",
                    HOLDING_FAILED,
                    e,
                    HOLDING_FAILED_CODE
            );
        }
    }

    private void runCompensation(Saga saga) {
        Workflow.newDetachedCancellationScope(() -> {
            try {
                saga.compensate();
            } catch (Exception e) {
                log.error("Compensation failed", e);
                // throw unexpected error for Temporal to make it wait
            }
        }).run();
    }

}
