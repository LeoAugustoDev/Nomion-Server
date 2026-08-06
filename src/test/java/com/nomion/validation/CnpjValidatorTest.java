package com.nomion.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CnpjValidatorTest {


    private final CnpjValidator validator = new CnpjValidator();


    @Test
    void shouldAcceptNumericAndAlphanumericCnpj() {

        assertTrue(validator.isValid("11.222.333/0001-81", null));
        assertTrue(validator.isValid("12.ABC.345/01DE-35", null));
        assertTrue(validator.isValid("12abc34501de35", null));
        assertTrue(validator.isValid(null, null));
    }


    @Test
    void shouldRejectInvalidCheckDigitsAndFormats() {

        assertFalse(validator.isValid("12.ABC.345/01DE-34", null));
        assertFalse(validator.isValid("12ABC34501DE3A", null));
        assertFalse(validator.isValid("12.ABC345/01DE-35", null));
        assertFalse(validator.isValid("00000000000000", null));
    }
}
