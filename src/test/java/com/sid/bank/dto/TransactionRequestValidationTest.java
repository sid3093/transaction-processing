package com.sid.bank.dto;

import com.sid.bank.entity.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<TransactionRequest>> validate(String amount) {
        return validator.validate(
                new TransactionRequest(TransactionType.DEPOSIT, new BigDecimal(amount), null, 1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "10.5", "10.50", "999999.99"})
    void validAmountsPass(String amount) {
        assertTrue(validate(amount).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "10.001", "1.999"})
    void invalidAmountsFail(String amount) {
        Set<ConstraintViolation<TransactionRequest>> violations = validate(amount);
        assertFalse(violations.isEmpty());
        assertEquals("amount", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void missingTypeAndAmountFail() {
        Set<ConstraintViolation<TransactionRequest>> violations =
                validator.validate(new TransactionRequest(null, null, null, 1L));
        assertEquals(2, violations.size());
    }
}
