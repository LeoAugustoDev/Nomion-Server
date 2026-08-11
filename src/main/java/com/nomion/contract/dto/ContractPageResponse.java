package com.nomion.contract.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ContractPageResponse(

        List<ContractResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {

    public static ContractPageResponse from(Page<ContractResponse> result) {

        return new ContractPageResponse(
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
