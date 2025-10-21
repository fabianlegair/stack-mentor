package io.stackmentor.service;

import io.stackmentor.dto.RegisterUserDto;
import io.stackmentor.dto.UserDto;
import io.stackmentor.enums.RoleType;
import io.stackmentor.model.User;
import io.stackmentor.repository.UserRepository;
import io.stackmentor.repository.VerificationTokenRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.IsInstanceOf.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    public void registerUser_createsNewMentorSucessfully() {

        //Arrange
        RegisterUserDto dto = new RegisterUserDto();
        dto.setName("Master Admin");
        dto.setEmail("admin@admin.com");
        dto.setPassword("MasterAdmin");
        dto.setDateOfBirth(LocalDate.parse("11-27-1999"));
        dto.setRole(RoleType.MENTOR);
        dto.setYearsOfExperience(12);
        dto.setSkillsOrInterests(List.of("Java", "Spring Boot"));
        dto.setCity("Columbus");
        dto.setState("OH");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);

        //Act
        UserDto result = userService.registerUser(dto);

        //Assert
        Assert.assertNotNull(result);
        Assert.assertEquals("Master", result.getFirstName());
        Assert.assertEquals("Admin", result.getLastName());
        Assert.assertEquals("admin@admin.com", result.getEmail());
        Assert.assertEquals(LocalDate.parse("11-27-1999"), result.getDateOfBirth());
        Assert.assertEquals(RoleType.MENTOR, result.getRole());
        Assert.assertEquals(Optional.of(12), result.getYearsOfExperience());
        Assert.assertEquals("Java, Spring Boot", result.getSkills());
        Assert.assertEquals("Columbus", result.getCity());
        Assert.assertEquals("OH", result.getState());
    }
}
