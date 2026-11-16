package com.pafisolutions.tarjepafi.iam.application;

import com.pafisolutions.tarjepafi.iam.domain.model.User;

/** Port: issues the access token handed to a user who signed in. */
public interface TokenIssuer {

  String issue(User user);
}
