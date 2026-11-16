package com.pafisolutions.tarjepafi.shared.application.security;

/** The caller of the current request, as every bounded context sees it. */
public record AuthenticatedUser(String id, String email, String role, String cardHolderId) {}
