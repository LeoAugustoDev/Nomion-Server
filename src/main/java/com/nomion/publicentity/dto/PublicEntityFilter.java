package com.nomion.publicentity.dto;

import com.nomion.publicentity.domain.PublicEntityType;

public record PublicEntityFilter(

        String name,

        String cnpj,

        PublicEntityType type,

        String municipality,

        String state,

        Boolean active

) {
}
