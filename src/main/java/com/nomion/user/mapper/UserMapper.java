package com.nomion.user.mapper;

import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.mapper.TenantMembershipMapper;
import com.nomion.user.domain.User;
import com.nomion.user.dto.UserInfoResponse;
import com.nomion.user.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {


    private final TenantMembershipMapper membershipMapper;


    public UserMapper(TenantMembershipMapper membershipMapper) {
        this.membershipMapper = membershipMapper;
    }


    public User toEntity(
            String name,
            String email,
            String encodedPassword
    ) {

        return User.builder()
                .name(name)
                .email(email)
                .password(encodedPassword)
                .build();
    }


    public UserResponse toResponse(
            User user,
            List<TenantMembership> memberships
    ) {

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                memberships.stream()
                        .map(membershipMapper::toResponse)
                        .toList()
        );
    }


    public UserInfoResponse toInfo(User user) {

        return new UserInfoResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
