package com.personalfinance.personfinancerest.user;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class DefaultCurrentUserProvider implements CurrentUserProvider {

    static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public UUID userId() {
        return DEFAULT_USER_ID;
    }
}
