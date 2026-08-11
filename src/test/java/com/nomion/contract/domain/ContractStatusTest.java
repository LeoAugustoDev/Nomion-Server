package com.nomion.contract.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractStatusTest {


    @Test
    void shouldAllowExpectedTransitions() {

        assertTrue(ContractStatus.DRAFT.canTransitionTo(ContractStatus.ACTIVE));
        assertTrue(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.SUSPENDED));
        assertTrue(ContractStatus.SUSPENDED.canTransitionTo(ContractStatus.ACTIVE));
        assertTrue(ContractStatus.EXPIRED.canTransitionTo(ContractStatus.ACTIVE));
        assertTrue(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.TERMINATED));
        assertTrue(ContractStatus.DRAFT.canTransitionTo(ContractStatus.DRAFT));
    }


    @Test
    void shouldRejectInvalidOrTerminalTransitions() {

        assertFalse(ContractStatus.DRAFT.canTransitionTo(ContractStatus.SUSPENDED));
        assertFalse(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.DRAFT));
        assertFalse(ContractStatus.TERMINATED.canTransitionTo(ContractStatus.ACTIVE));
    }
}
