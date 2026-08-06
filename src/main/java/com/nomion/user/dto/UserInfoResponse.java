package com.nomion.user.dto;

import java.util.UUID;

public record UserInfoResponse(

        UUID id,

        String name,

        String email

) {
}
