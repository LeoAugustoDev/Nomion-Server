package com.nomion.publicentity.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PublicEntityPageResponse(

        List<PublicEntityResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {

    public static PublicEntityPageResponse from(Page<PublicEntityResponse> result) {

        return new PublicEntityPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }
}
