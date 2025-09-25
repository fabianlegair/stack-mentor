package io.stackmentor.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // Model
    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Size(min = 3, max = 16)
    @Column(name = "username", nullable = false, unique = true, length = 16)
    private String username;

    @Email(message = "Please provide a valid email address")
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Please provide a valid phone number")
    @Size(min = 10, max = 15)
    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 30)
    private String firstName;

    @Column(name = "last_name", length = 30)
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "city", length = 26)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "age")
    private Integer age;

    @Column(name = "profile_picture_url", length = 255)
    private String profilePictureUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "mentor_status")
    private boolean mentorStatus;

    @Column(name = "mentee_status")
    private boolean menteeStatus;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "interests", columnDefinition = "TEXT")
    private String interests;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    // Method to calculate age from dateOfBirth
    public int calculateAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
