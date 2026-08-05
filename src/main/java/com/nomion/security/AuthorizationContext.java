package com.nomion.security;

import java.util.Set;

public record AuthorizationContext(

        Set<String> roles,

        Set<String> permissions

) {
}
