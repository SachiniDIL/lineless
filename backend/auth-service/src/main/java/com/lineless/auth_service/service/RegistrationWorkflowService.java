package com.lineless.auth_service.service;

import com.lineless.auth_service.dto.request.RegisterRequest;
import com.lineless.auth_service.entity.User;
import org.springframework.stereotype.Service;

@Service
public class RegistrationWorkflowService {

    private final RegistrationService registrationService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailSenderService emailSenderService;

    public RegistrationWorkflowService(RegistrationService registrationService, EmailVerificationTokenService emailVerificationTokenService, EmailSenderService emailSenderService) {
        this.registrationService = registrationService;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.emailSenderService = emailSenderService;
    }

    public void register(RegisterRequest request) {

        // 1. Create and save the user.
        // This transaction commits before the method returns.
        User user = registrationService.register(request);

        // 2. Generate and save the verification token.
        String rawToken = emailVerificationTokenService
                .createVerificationToken(user);

        // 3. Send the verification email.
        emailSenderService.sendVerificationEmail(
                user.getEmail(),
                user.getName(),
                rawToken
        );
    }
}