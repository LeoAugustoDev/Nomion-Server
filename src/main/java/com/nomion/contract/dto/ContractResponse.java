package com.nomion.contract.dto;

import com.nomion.contract.domain.ContractStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(

        UUID id,

        ContractPublicEntityResponse publicEntity,

        ContractManagerResponse manager,

        String number,

        String object,

        LocalDate startDate,

        LocalDate endDate,

        BigDecimal amount,

        ContractStatus status,

        UUID createdByUserId,

        UUID updatedByUserId,

        Instant createdAt,

        Instant updatedAt

) {
}
