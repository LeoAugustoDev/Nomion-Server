package com.nomion.contract.dto;

import com.nomion.contract.domain.ContractStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateContractRequest(

        @NotNull(message = "Órgão público é obrigatório")
        UUID publicEntityId,


        UUID managerMembershipId,


        @NotBlank(message = "Número é obrigatório")
        @Size(max = 50, message = "Número deve ter no máximo 50 caracteres")
        String number,


        @NotBlank(message = "Objeto é obrigatório")
        @Size(
                min = 5,
                max = 2000,
                message = "Objeto deve ter entre 5 e 2000 caracteres"
        )
        String object,


        @NotNull(message = "Data inicial é obrigatória")
        LocalDate startDate,


        @NotNull(message = "Data final é obrigatória")
        LocalDate endDate,


        @DecimalMin(value = "0.00", message = "Valor não pode ser negativo")
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Valor deve ter até 17 inteiros e 2 casas decimais"
        )
        BigDecimal amount,


        @NotNull(message = "Situação é obrigatória")
        ContractStatus status

) {

    public UpdateContractRequest {
        number = normalizeRequired(number);
        object = normalizeRequired(object);
    }


    private static String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }
}
