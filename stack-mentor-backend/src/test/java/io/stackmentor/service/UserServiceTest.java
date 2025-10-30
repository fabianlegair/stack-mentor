package io.stackmentor.service;

import io.stackmentor.dto.user.RegisterUserDto;
import io.stackmentor.dto.user.UserDto;
import io.stackmentor.enums.RoleType;
import io.stackmentor.model.User;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
import io.stackmentor.specification.UserSpecificationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

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
    private UserSpecificationBuilder specBuilder;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_createsNewUserSuccessfully() {

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
    void registerUser_nameWithExtraPartsThrowsException() {

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
    void registerUser_nameWithFewerPartsThrowsException() {

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
    void registerUser_withUsedEmailThrowsException() {
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

    @Test
    void searchUsers_callsSpecBuilderAndRepository() {
        Specification<User> mockSpec = mock(Specification.class);
        when(specBuilder.searchWithFilters(any(), any(), any(), any(), any())).thenReturn(mockSpec);
        when(userRepository.findAll(mockSpec)).thenReturn(List.of());

        userService.searchUsers(" Admin ", "MENTEE", "5+", List.of("Tech"));

        verify(specBuilder).searchWithFilters(
                eq("Admin"), eq("MENTEE"), eq(5), eq(null), eq(List.of("Tech"))
        );
        verify(userRepository).findAll(mockSpec);
    }

    @Test
    void searchUsers_callsRepositoryWithCorrectSpecification() {

        // Arrange
        String searchText = " Master ";
        String role = "MENTOR";
        String experienceRange = "2-5";
        List<String> industries = List.of("Tech", "Finance");

        Specification<User> mockSpec = mock(Specification.class);
        when(specBuilder.searchWithFilters(anyString(), anyString(), any(), any(), anyList()))
                .thenReturn(mockSpec);
        when(userRepository.findAll(eq(mockSpec))).thenReturn(List.of(new User(), new User()));

        // Act
        List<User> result = userService.searchUsers(searchText, role, experienceRange, industries);

        // Assert
        // Verify specBuilder called with trimmed text and correct experience range
        verify(specBuilder).searchWithFilters(
                eq("Master"),  // trimmed
                eq(role),
                eq(2),         // minExp
                eq(5),         // maxExp
                eq(industries)
        );

        // Verify repository is called with the specification returned from specBuilder
        verify(userRepository).findAll(eq(mockSpec));
        assertEquals(2, result.size());
    }

    @Test
    void searchUsers_trimsSearchText() {

        // Arrange
        String searchText = "  Admin  ";
        String role = "MENTEE";
        String experienceRange = null;
        List<String> industries = List.of();

        Specification<User> mockSpec = mock(Specification.class);
        when(specBuilder.searchWithFilters(anyString(), anyString(), any(), any(), anyList()))
                .thenReturn(mockSpec);
        when(userRepository.findAll(eq(mockSpec))).thenReturn(List.of());

        // Act
        userService.searchUsers(searchText, role, experienceRange, industries);

        // Assert
        verify(specBuilder).searchWithFilters(
                eq("Admin"),   // trimmed
                eq(role),
                eq(null),
                eq(null),
                eq(industries)
        );

        verify(userRepository).findAll(eq(mockSpec));
    }

    @Test
    void searchUsers_parsesExperienceRangeWithPlus() {

        // Arrange
        String searchText = "  Admin  ";
        String role = "MENTEE";
        String experienceRange = "10+";
        List<String> industries = List.of();

        Specification<User> mockSpec = mock(Specification.class);
        when(specBuilder.searchWithFilters(anyString(), anyString(), any(), any(), anyList()))
                .thenReturn(mockSpec);
        when(userRepository.findAll(eq(mockSpec))).thenReturn(List.of());

        // Act
        userService.searchUsers(searchText, role, experienceRange, industries);

        // Assert --
        verify(specBuilder).searchWithFilters(
                eq("Admin"),
                eq(role),
                eq(10),    // minExp parsed correctly
                eq(null),  // maxExp is null for "10+"
                eq(industries)
        );

        verify(userRepository).findAll(eq(mockSpec));
    }

    @Test
    void searchUsers_throwsExceptionForInvalidExperienceRange() {
        // Arrange
        String invalidRange = "abc-xyz";

        // Act + Assert
        assertThrows(IllegalArgumentException.class, () ->
                userService.searchUsers("Master", "MENTOR", invalidRange, null)
        );
    }

    @Test
    void searchUsers_callsUserSpecificationsWithParsedExperienceRange() {
        // Arrange
        String searchText = " Master ";
        String role = "MENTOR";
        String experienceRange = "3-7";
        List<String> industries = List.of("Tech", "Finance");

        Specification<User> mockSpec = mock(Specification.class);
        when(specBuilder.searchWithFilters(anyString(), anyString(), any(), any(), anyList()))
                .thenReturn(mockSpec);
        when(userRepository.findAll(eq(mockSpec))).thenReturn(List.of());

        // Act
        userService.searchUsers(searchText, role, experienceRange, industries);

        // Assert
        verify(specBuilder).searchWithFilters(
                eq("Master"), // trimmed
                eq(role),
                eq(3),        // minExp
                eq(7),        // maxExp
                eq(industries)
        );

        verify(userRepository).findAll(eq(mockSpec));
    }
}
