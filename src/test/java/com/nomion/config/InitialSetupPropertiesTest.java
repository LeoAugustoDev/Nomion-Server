package com.nomion.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitialSetupPropertiesTest {


    @Test
    void shouldRequireStrongTokenWhenSetupIsEnabled() {

        assertThrows(IllegalArgumentException.class, () ->
                new InitialSetupProperties(true, "short-token"));
    }


    @Test
    void shouldAllowEmptyTokenWhenSetupIsDisabled() {

        assertDoesNotThrow(() -> new InitialSetupProperties(false, ""));
    }
}
