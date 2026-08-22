package com.personalfinance.personfinancerest.user;

import java.util.UUID;

/**
 * Provides the identity of the user associated with the current request.
 *
 * <p>The initial implementation returns the single user created by the first
 * database migration. Authentication can replace that implementation without
 * coupling feature services to the security mechanism.</p>
 */
public interface CurrentUserProvider {

    UUID userId();
}
