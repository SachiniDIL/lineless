package com.lineless.auth_service.service;

public record GoogleUserInfo(
        String googleSubject,
        String email,
        String name
) {}