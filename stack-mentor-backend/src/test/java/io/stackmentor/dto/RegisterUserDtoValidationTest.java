package io.stackmentor.dto;

import io.stackmentor.dto.user.RegisterUserDto;
import jakarta.validation.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;

public class RegisterUserDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setValidatior() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenNameIsBlank_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setName("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Full name is required")));
    }

    @Test
    void whenEmailIsBlank_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Email is required")));
    }

    @Test
    void whenEmailIsInvalid_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail("admin,com");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Please provide a valid email address")));
    }

    @Test
    void whenPasswordIsBlank_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setPassword("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Password is required")));
    }

    @Test
    void whenPasswordIsTooShort_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setPassword("1234567");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Password must be at least 8 characters long")));
    }

    @Test
    void whenDateOfBirthIsNull_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setDateOfBirth(null);

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Date of birth is required")));
    }

    @Test
    void whenDateOfBirthIsInPast_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setDateOfBirth(LocalDate.of(2030, 1, 1));

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Date of birth must be in the past")));
    }

    @Test
    void whenRoleIsNull_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setRole(null);

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("Role is required")));
    }

    @Test
    void whenCityIsNull_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setCity("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("City is required")));
    }

    @Test
    void whenStateIsNull_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setState("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("State is required")));
    }

    @Test
    void whenStateIsTooLong_validationFails() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setState("");

        Set<ConstraintViolation<RegisterUserDto>> violations =
                validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v ->
                        v.getMessage().equals("State must be 2 characters")));
    }
}
