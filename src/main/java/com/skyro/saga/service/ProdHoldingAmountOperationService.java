package com.skyro.saga.service;

import com.skyro.saga.temporal.HoldingOperationWorkflowV1;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeoutException;

import static io.temporal.api.enums.v1.WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A synchronous caller on top of the workflow: the client holds an open request while the saga
 * runs, so the client's deadline, not the workflow, decides how long the operation may take. When
 * the deadline runs out the caller cancels the execution instead of leaving it to book holds
 * nobody will ever claim.
 */
@Service
public class ProdHoldingAmountOperationService {

    private static final String WORKFLOW_ID_PREFIX = "holding-v1-";
    private static final long DEFAULT_TIMEOUT = 4;

    private final WorkflowClient workflowClient;

    public ProdHoldingAmountOperationService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public String holdAmount(String sourceId) {
        //... in the real life the primary part validates the request and looks for a duplicate operation

        WorkflowStub workflow = startWorkflowAndGetStub(sourceId);

        try {
            workflow.getResult(DEFAULT_TIMEOUT, SECONDS, Void.class);
        } catch (TimeoutException e) {
            workflow.cancel();

            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Holding %s timed out after %d ms, cancellation requested".formatted(sourceId, DEFAULT_TIMEOUT)
            );

        } catch (WorkflowFailedException e) {
            // Production carries an error-code enum in ApplicationFailure.getDetails()
            if (e.getCause() != null && e.getCause() instanceof ApplicationFailure applicationFailure) {
                String internalErrorCode = applicationFailure.getDetails().get(0, String.class);

                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, internalErrorCode, applicationFailure);
            } else {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", e);
            }
        }

        // Production persists the completed operation here, keyed by the workflow id.
        return workflow.getExecution().getWorkflowId();
    }

    private WorkflowStub startWorkflowAndGetStub(String sourceId) {
        HoldingOperationWorkflowV1 workflow = workflowClient.newWorkflowStub(
                HoldingOperationWorkflowV1.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(WORKFLOW_ID_PREFIX + sourceId)
                        .setTaskQueue(HoldingOperationWorkflowV1.TASK_QUEUE)
                        .setWorkflowIdReusePolicy(WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                        .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                        .build()
        );

        try {
            WorkflowClient.start(workflow::holdAmount, sourceId);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // A caller that timed out or lost connection and retried joins the running execution.
        }

        // Get connection between the execution thread and the workflow execution
        return WorkflowStub.fromTyped(workflow);
    }

}
