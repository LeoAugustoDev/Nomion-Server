package com.nomion.user.mapper;

import com.nomion.tenant.domain.Tenant;
import com.nomion.user.domain.User;
import com.nomion.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(
            String name,
            String email,
            String encodedPassword,
            Tenant tenant
    ) {

        return User.builder()
                .name(name)
                .email(email)
                .password(encodedPassword)
                .tenant(tenant)
                .build();
    }


    public UserResponse toResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getTenant().getId()
        );
    }
}
