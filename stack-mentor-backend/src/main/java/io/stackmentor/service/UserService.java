package io.stackmentor.service;

import io.stackmentor.model.User;
import io.stackmentor.model.VerificationToken;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
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

    public User registerUser(String email, String rawPassword) {
        // Business logic for registering a user
        // e.g., hashing password, validating data, etc.
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        User newUser = new User();
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

}
