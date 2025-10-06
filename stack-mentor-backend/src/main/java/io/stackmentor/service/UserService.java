package io.stackmentor.service;

import io.stackmentor.enums.RoleType;
import io.stackmentor.model.User;
import io.stackmentor.model.VerificationToken;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
import io.stackmentor.repository.specs.UserSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       VerificationTokenRepository verificationTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.emailService = emailService;
    }

    public User registerUser(RoleType role, Integer yearsOfExperience, List<String> skillsOrInterests,
                             String name, String city, String state,
                             String gender, String email, String rawPassword) {
        // Business logic for registering a user
        // e.g., hashing password, validating data, etc.
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        // Split name
        if (!name.trim().contains(" ")) {
            throw new IllegalArgumentException("Only  include your first and last name, separated by a space");
        }
        String[] parts = name.split("\\s+");
        if (parts.length > 2) {
            throw new IllegalArgumentException("Full name must not include middle names");
        }
        String firstNamePart = parts[0];
        String lastNamePart = parts[1];

        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        User newUser = new User();

        // Set role-specific fields
        if (role == RoleType.MENTOR) {
            newUser.setRole(RoleType.MENTOR);
            String skills = String.join(",", skillsOrInterests);
            newUser.setSkills(skills);
        } else {
            newUser.setRole(RoleType.MENTEE);
            String interests = String.join(",", skillsOrInterests);
            newUser.setInterests(interests);
        }

        newUser.setYearsOfExperience(yearsOfExperience);
        newUser.setFirstName(firstNamePart);
        newUser.setLastName(lastNamePart);
        newUser.setCity(city);
        newUser.setState(state);
        newUser.setGender(gender);
        newUser.setEmail(email);
        newUser.setPasswordHash(hashedPassword);
        newUser.setVerified(false);

        userRepository.save(newUser);

        //Token generation/verification logic
        String token = UUID.randomUUID().toString();
        VerificationToken vt = new VerificationToken(
                null, token, newUser, java.time.LocalDateTime.now().plusHours(24));  // Token valid for 24 hours
        verificationTokenRepository.save(vt);

        // Send verification email
        emailService.sendVerificationEmail(email, token);

        return newUser;
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

        Specification<User> specification = UserSpecifications.searchWithFilters(
                trimmedSearchText, role, minExp, maxExp, industries);

        return userRepository.findAll(specification);
    }

}
