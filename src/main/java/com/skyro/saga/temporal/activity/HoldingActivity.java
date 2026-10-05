package com.skyro.saga.temporal.activity;

import io.temporal.activity.ActivityInterface;

/**
 * The unhold methods return nothing on purpose: {@code Saga.addCompensation} accepts only
 * {@code Functions.Proc*}, so a compensation cannot have a return value.
 */
@ActivityInterface
public interface HoldingActivity {

    String holdCredit(String operationId, long amount);

    void unholdCredit(String operationId);

    String holdDebit(String operationId, long amount);

    void unholdDebit(String operationId);
}
