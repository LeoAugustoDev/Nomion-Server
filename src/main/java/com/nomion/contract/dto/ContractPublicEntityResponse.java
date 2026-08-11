package com.nomion.contract.dto;

import java.util.UUID;

public record ContractPublicEntityResponse(

        UUID id,

        String name,

        String cnpj

) {
}
