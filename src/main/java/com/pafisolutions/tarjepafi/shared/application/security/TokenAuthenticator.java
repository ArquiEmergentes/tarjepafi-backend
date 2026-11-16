package com.pafisolutions.tarjepafi.shared.application.security;

import java.util.Optional;

/** Port implemented by IAM: turns a bearer token into the caller, or nothing if it is not acceptable. */
public interface TokenAuthenticator {

  Optional<AuthenticatedUser> authenticate(String token);
}
