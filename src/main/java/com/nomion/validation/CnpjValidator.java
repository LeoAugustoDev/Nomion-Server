package com.nomion.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {


    private static final int[] FIRST_DIGIT_WEIGHTS = {
            5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2
    };
    private static final int[] SECOND_DIGIT_WEIGHTS = {
            6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2
    };
    private static final String UNFORMATTED_PATTERN = "[A-Z0-9]{12}[0-9]{2}";
    private static final String FORMATTED_PATTERN =
            "[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2}";


    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if (value == null) {
            return true;
        }

        String uppercase = value.toUpperCase(Locale.ROOT);

        if (!uppercase.matches(UNFORMATTED_PATTERN)
                && !uppercase.matches(FORMATTED_PATTERN)) {
            return false;
        }

        String normalized = uppercase.replaceAll("[^A-Z0-9]", "");

        if (normalized.chars().allMatch(character ->
                character == normalized.charAt(0))) {
            return false;
        }

        int firstDigit = calculateDigit(
                normalized.substring(0, 12),
                FIRST_DIGIT_WEIGHTS
        );

        if (firstDigit != Character.digit(normalized.charAt(12), 10)) {
            return false;
        }

        int secondDigit = calculateDigit(
                normalized.substring(0, 13),
                SECOND_DIGIT_WEIGHTS
        );

        return secondDigit == Character.digit(normalized.charAt(13), 10);
    }


    private int calculateDigit(String value, int[] weights) {

        int sum = 0;

        for (int index = 0; index < value.length(); index++) {
            sum += (value.charAt(index) - '0') * weights[index];
        }

        int remainder = sum % 11;

        return remainder < 2 ? 0 : 11 - remainder;
    }
}
