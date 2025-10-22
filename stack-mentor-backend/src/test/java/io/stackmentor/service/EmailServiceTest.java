package io.stackmentor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    public void sendVerificationEmail_sendsCorrectMessage() {

        String recipient = "admin@admin.com";
        String token = "12345678";

        emailService.sendVerificationEmail(recipient, token);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals(recipient, Objects.requireNonNull(sent.getTo())[0]);
        assertEquals("Email Verification for StackMentor.io", sent.getSubject());
        assertTrue(Objects.requireNonNull(sent.getText()).contains("12345678"));
    }
}
