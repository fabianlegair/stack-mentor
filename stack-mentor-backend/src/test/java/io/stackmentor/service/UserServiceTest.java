package io.stackmentor.service;

import io.stackmentor.dto.RegisterUserDto;
import io.stackmentor.dto.UserDto;
import io.stackmentor.enums.RoleType;
import io.stackmentor.model.User;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    public void registerUser_createsNewMentorSuccessfully() {

        //Arrange
        RegisterUserDto dto = new RegisterUserDto();
        dto.setName("Master Admin");
        dto.setEmail("admin@admin.com");
        dto.setPassword("MasterAdmin");
        dto.setDateOfBirth(LocalDate.of(1999, 11, 27));
        dto.setRole(RoleType.MENTOR);
        dto.setYearsOfExperience(12);
        dto.setSkillsOrInterests(List.of("Java", "Spring Boot"));
        dto.setCity("Columbus");
        dto.setState("OH");

        // Mock user to be returned after save
        UUID userId = UUID.randomUUID();
        User savedUser = new User();
        savedUser.setUserId(userId);
        savedUser.setFirstName("Master");
        savedUser.setLastName("Admin");
        savedUser.setEmail("admin@admin.com");
        savedUser.setDateOfBirth(LocalDate.of(1999, 11, 27));
        savedUser.setRole(RoleType.MENTOR);
        savedUser.setYearsOfExperience(12);
        savedUser.setSkills("Java, Spring Boot");
        savedUser.setCity("Columbus");
        savedUser.setState("OH");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doNothing().when(emailService).sendVerificationEmail(any(), any());

        //Act
        UserDto result = userService.registerUser(dto);

        //Assert
        assertNotNull(result);
        assertNotNull(result.getUserId());
        assertEquals("Master", result.getFirstName());
        assertEquals("Admin", result.getLastName());
        assertEquals("admin@admin.com", result.getEmail());
        assertEquals(LocalDate.of(1999, 11, 27), result.getDateOfBirth());
        assertEquals(RoleType.MENTOR, result.getRole());
        assertEquals(12, result.getYearsOfExperience());
        assertThat(result.getSkills()).containsExactlyInAnyOrder("Java", "Spring Boot");
        assertEquals("Columbus", result.getCity());
        assertEquals("OH", result.getState());

        // Verify interactions
        verify(userRepository).existsByEmail("admin@admin.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    public void registerUser_nameWithExtraPartsThrowsException() {

        // Arrange
        RegisterUserDto dto = new RegisterUserDto();
        dto.setName("Master Ad Min");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);

        //Act & Assert
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(dto)
        );

        assertEquals("Full name must not include middle names",
                e.getMessage());
    }

    @Test
    public void registerUser_nameWithFewerPartsThrowsException() {

        // Arrange
        RegisterUserDto dto = new RegisterUserDto();
        dto.setName("MasterAdmin");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);

        //Act & Assert
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(dto)
        );

        assertEquals("Only  include your first and last name, separated by a space",
                e.getMessage());
    }

    @Test
    public void registerUser_withUsedEmailThrowsException() {
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail("admin@admin.com");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);

        //Act & Assert
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser(dto)
        );

        assertEquals("Email already in use",
                e.getMessage());
    }
}
