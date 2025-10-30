package io.stackmentor.dto.user;

import io.stackmentor.enums.PositionType;
import io.stackmentor.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String city;
    private String state;
    private String gender;
    private Integer age;
    private String profilePictureUrl;
    private String bio;
    private RoleType role;
    private String jobTitle;
    private Integer yearsOfExperience;
    private String industry;
    private List<String> skills;
    private List<String> interests;
    private LocalDateTime createdAt;
    private PositionType position;
    private boolean isVerified;

}
