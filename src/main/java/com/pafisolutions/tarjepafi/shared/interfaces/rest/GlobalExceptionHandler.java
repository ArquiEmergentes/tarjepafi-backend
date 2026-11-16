package com.pafisolutions.tarjepafi.shared.interfaces.rest;

import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as an RFC 9457 problem document with an extra {@code code} property.
 * The web app maps {@code code} to its own translated messages, so codes are part of the contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  public static final String CODE = "code";

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException exception) {
    HttpStatus status =
        switch (exception.kind()) {
          case INVALID -> HttpStatus.BAD_REQUEST;
          case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case CONFLICT -> HttpStatus.CONFLICT;
          case UNPROCESSABLE -> HttpStatus.valueOf(422);
        };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setProperty(CODE, exception.code());
    return ResponseEntity.status(status).body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> fields =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is not valid");
    problem.setProperty(CODE, "VALIDATION_FAILED");
    problem.setProperty("fields", fields);
    return ResponseEntity.badRequest().body(problem);
  }
}
