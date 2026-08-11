package com.nomion.contract.dto;

import com.nomion.contract.domain.ContractStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ContractFilter(

        String number,

        UUID publicEntityId,

        UUID managerMembershipId,

        ContractStatus status,

        LocalDate startDateFrom,

        LocalDate endDateTo

) {
}
