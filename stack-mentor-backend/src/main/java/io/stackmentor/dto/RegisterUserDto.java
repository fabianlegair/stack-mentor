package io.stackmentor.dto;

import io.stackmentor.enums.RoleType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserDto {

    @NotBlank(message = "Full name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth; // ISO format "YYYY-MM-DD"

    @NotNull(message = "Role is required")
    private RoleType role; // "MENTOR" or "MENTEE"

    private Integer yearsOfExperience;

    private List<String> skillsOrInterests;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    @Size(min = 2, max = 2, message = "State must be 2 characters")
    private String state;

    private String gender;
}
