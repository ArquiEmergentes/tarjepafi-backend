package com.pafisolutions.tarjepafi.iam.application;

import com.pafisolutions.tarjepafi.iam.domain.model.EmailAddress;
import com.pafisolutions.tarjepafi.iam.domain.model.User;
import com.pafisolutions.tarjepafi.iam.infrastructure.persistence.UserRepository;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in rules (TS02): e-mail format, then credentials, then account status, in that order. */
@Service
public class AuthenticationService {

  public record SignedIn(String token, User user) {}

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final SignInThrottle throttle;
  private final String decoyHash;

  public AuthenticationService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokenIssuer,
      SignInThrottle throttle) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
    this.throttle = throttle;
    // Compared against when the e-mail is unknown, so both failures take the same time.
    this.decoyHash = passwordEncoder.encode("decoy-password-never-matches");
  }

  @Transactional(readOnly = true)
  public SignedIn signIn(String rawEmail, String password, String client) {
    Optional<EmailAddress> parsed = EmailAddress.parse(rawEmail);
    String normalized = parsed.map(EmailAddress::value).orElse(null);
    throttle.check(normalized, client);
    if (parsed.isEmpty()) {
      throttle.failed(null, client);
      throw new BusinessRuleException(
          Kind.INVALID, "INVALID_EMAIL", "An institutional e-mail is required");
    }
    EmailAddress email = parsed.get();

    Optional<User> found = users.findByEmail(email.value());
    boolean matches =
        passwordEncoder.matches(
            password == null ? "" : password, found.map(User::getPasswordHash).orElse(decoyHash));
    if (found.isEmpty() || !matches) {
      throttle.failed(email.value(), client);
      throw new BusinessRuleException(
          Kind.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail or password is incorrect");
    }

    User user = found.get();
    throttle.succeeded(email.value());
    if (!user.isEnabled()) {
      throw new BusinessRuleException(
          Kind.FORBIDDEN, "ACCOUNT_DISABLED", "The account is " + user.getStatus());
    }
    return new SignedIn(tokenIssuer.issue(user), user);
  }

  @Transactional(readOnly = true)
  public Optional<User> findEnabledById(String id) {
    return users.findById(id).filter(User::isEnabled);
  }
}
