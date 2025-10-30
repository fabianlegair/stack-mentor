package io.stackmentor.service;

import io.stackmentor.dto.user.RegisterUserDto;
import io.stackmentor.dto.user.UserDto;
import io.stackmentor.enums.PositionType;
import io.stackmentor.enums.RoleType;
import io.stackmentor.model.User;
import io.stackmentor.model.VerificationToken;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
import io.stackmentor.specification.UserSpecificationBuilder;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserSpecificationBuilder specBuilder;


    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(user.getDateOfBirth())
                .city(user.getCity())
                .state(user.getState())
                .gender(user.getGender())
                .age(user.getAge())
                .profilePictureUrl(user.getProfilePictureUrl())
                .bio(user.getBio())
                .role(user.getRole())
                .jobTitle(user.getJobTitle())
                .yearsOfExperience(user.getYearsOfExperience())
                .industry(user.getIndustry())
                .skills(user.getSkills() != null ? List.of(user.getSkills().split(", ")) : null)
                .interests(user.getInterests() != null ? List.of(user.getInterests().split(", ")) : null)
                .createdAt(user.getCreatedAt())
                .position(user.getPosition())
                .isVerified(user.isVerified())
                .build();
    }

    @Transactional
    public UserDto registerUser(RegisterUserDto dto) {

        // Business logic for registering a user
        // e.g., hashing password, validating data, etc.
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        // Split name
        if (!dto.getName().trim().contains(" ")) {
            throw new IllegalArgumentException("Only  include your first and last name, separated by a space");
        }
        String[] parts = dto.getName().split("\\s+");
        if (parts.length > 2) {
            throw new IllegalArgumentException("Full name must not include middle names");
        }
        String firstNamePart = parts[0];
        String lastNamePart = parts[1];

        String hashedPassword = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());

        //Build new user
        User.UserBuilder userBuilder = User.builder()
                .email(dto.getEmail())
                .passwordHash(hashedPassword)
                .firstName(firstNamePart)
                .lastName(lastNamePart)
                .dateOfBirth(dto.getDateOfBirth())
                .city(dto.getCity())
                .state(dto.getState())
                .gender(dto.getGender())
                .yearsOfExperience(dto.getYearsOfExperience())
                .role(dto.getRole())
                .position(PositionType.MEMBER)
                .isVerified(false);

        // Set role-specific fields
        if (dto.getRole() == RoleType.MENTOR) {
            String skills = String.join(", ", dto.getSkillsOrInterests());
            userBuilder.skills(skills);
        } else {
            String interests = String.join(", ", dto.getSkillsOrInterests());
            userBuilder.interests(interests);
        }

        User newUser = userBuilder.build();

        newUser.setAge(newUser.calculateAge());

        // Save user to DB (UUID Auto-generated)
        User savedUser = userRepository.save(newUser);

        //Token generation/verification logic
        String token = UUID.randomUUID().toString();
        VerificationToken vt = new VerificationToken(
                null, token, savedUser, java.time.LocalDateTime.now().plusHours(24));  // Token valid for 24 hours
        verificationTokenRepository.save(vt);

        // Send verification email
        try {
            emailService.sendVerificationEmail(dto.getEmail(), token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }

        return convertToDto(savedUser);
    }

    public List<User> searchUsers(String searchText, String role, String experienceRange,
                                  List<String> industries) {
        // Implement search logic using specifications or custom queries
        // For simplicity, returning all users here

        //Trim search text
        String trimmedSearchText = (searchText != null) ? searchText.trim() : null;

        // Parse experienceRange
        Integer minExp = null;
        Integer maxExp = null;
        try {
            if (experienceRange != null && !experienceRange.isEmpty()) {
                if (experienceRange.endsWith("+")) {
                    minExp = Integer.parseInt(experienceRange.replace("+", "").trim());
                } else if (experienceRange.contains("-")) {
                    String[] parts = experienceRange.split("-");
                    minExp = Integer.parseInt(parts[0].trim());
                    maxExp = Integer.parseInt(parts[1].trim());
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid experience range format: " + experienceRange, e);
        }

        Specification<User> specification = specBuilder.searchWithFilters(
                trimmedSearchText, role, minExp, maxExp, industries);

        return userRepository.findAll(specification);
    }

}
