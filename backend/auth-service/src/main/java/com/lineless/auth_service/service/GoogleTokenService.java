package com.lineless.auth_service.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import com.lineless.auth_service.exception.InvalidGoogleTokenException;

@Service
public class GoogleTokenService {

    private final JwtDecoder googleJwtDecoder;

    public GoogleTokenService(
            @Qualifier("googleJwtDecoder") JwtDecoder googleJwtDecoder
    ) {
        this.googleJwtDecoder = googleJwtDecoder;
    }

    public GoogleUserInfo validateAndExtractUserInfo(String idToken) {

        try {
            Jwt jwt = googleJwtDecoder.decode(idToken);

            Boolean emailVerified = jwt.getClaim("email_verified");

            if (!Boolean.TRUE.equals(emailVerified)) {
                throw new InvalidGoogleTokenException(
                        "Google email is not verified"
                );
            }

            String googleSubject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");

            if (googleSubject == null || googleSubject.isBlank()
                    || email == null || email.isBlank()
                    || name == null || name.isBlank()) {

                throw new InvalidGoogleTokenException(
                        "Google ID token is missing required user information"
                );
            }

            return new GoogleUserInfo(
                    googleSubject,
                    email.trim().toLowerCase(),
                    name.trim()
            );

        } catch (InvalidGoogleTokenException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new InvalidGoogleTokenException(
                    "Invalid Google ID token"
            );
        }
    }
}