package com.pafisolutions.tarjepafi.iam.interfaces.rest;

import com.pafisolutions.tarjepafi.iam.application.AuthenticationService;
import com.pafisolutions.tarjepafi.shared.application.security.AuthenticatedUser;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  public record SignInResource(@NotBlank String email, @NotBlank String password) {}

  public record AuthenticatedUserResource(String token, UserResource user) {}

  private final AuthenticationService authentication;

  public AuthController(AuthenticationService authentication) {
    this.authentication = authentication;
  }

  @PostMapping("/sign-in")
  @Operation(
      summary = "Sign in with an institutional e-mail and password",
      description =
          "Errors carry a `code`: 400 INVALID_EMAIL, 401 INVALID_CREDENTIALS, 403 ACCOUNT_DISABLED, 429 "
              + "TOO_MANY_ATTEMPTS (5 failed attempts for an e-mail, or 30 from one address, lock it for 5 minutes; "
              + "the `Retry-After` header and `retryAfterSeconds` say how long).")
  public AuthenticatedUserResource signIn(
      @Valid @RequestBody SignInResource body, HttpServletRequest request) {
    AuthenticationService.SignedIn signedIn =
        authentication.signIn(body.email(), body.password(), request.getRemoteAddr());
    return new AuthenticatedUserResource(signedIn.token(), UserResource.from(signedIn.user()));
  }

  @GetMapping("/me")
  @Operation(summary = "The user the bearer token belongs to")
  public UserResource me(@AuthenticationPrincipal AuthenticatedUser caller) {
    return authentication
        .findEnabledById(caller.id())
        .map(UserResource::from)
        .orElseThrow(
            () -> new BusinessRuleException(Kind.UNAUTHORIZED, "INVALID_TOKEN", "Unknown user"));
  }
}
