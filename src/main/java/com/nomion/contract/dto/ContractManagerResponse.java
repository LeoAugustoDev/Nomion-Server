package com.nomion.contract.dto;

import java.util.UUID;

public record ContractManagerResponse(

        UUID membershipId,

        UUID userId,

        String name,

        String email

) {
}
