package com.lineless.auth_service.dto.response;

import com.lineless.auth_service.entity.User;

public record RefreshTokenResult(
        String refreshToken,
        User user
) {}