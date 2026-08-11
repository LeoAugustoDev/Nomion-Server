package com.nomion.contract.domain;

import java.util.Set;

public enum ContractStatus {

    DRAFT,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    TERMINATED;


    public boolean canTransitionTo(ContractStatus target) {

        if (this == target) {
            return true;
        }

        return switch (this) {
            case DRAFT -> Set.of(ACTIVE, TERMINATED).contains(target);
            case ACTIVE -> Set.of(SUSPENDED, EXPIRED, TERMINATED).contains(target);
            case SUSPENDED -> Set.of(ACTIVE, EXPIRED, TERMINATED).contains(target);
            case EXPIRED -> Set.of(ACTIVE, TERMINATED).contains(target);
            case TERMINATED -> false;
        };
    }
}
