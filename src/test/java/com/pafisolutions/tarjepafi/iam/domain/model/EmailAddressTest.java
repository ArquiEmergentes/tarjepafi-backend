package com.pafisolutions.tarjepafi.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

  @Test
  void parse_institutionalAddressWithSpacesAndUpperCase_returnsNormalisedAddress() {
    assertThat(EmailAddress.parse("  Admin@UPC.edu.pe "))
        .contains(new EmailAddress("admin@upc.edu.pe"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", "admin", "admin@gmail.com", "@upc.edu.pe", "a b@upc.edu.pe", "admin@upc.edu.pe.evil.com"})
  void parse_notAnInstitutionalAddress_returnsEmpty(String raw) {
    assertThat(EmailAddress.parse(raw)).isEmpty();
  }

  @Test
  void constructor_otherDomain_throws() {
    assertThatThrownBy(() -> new EmailAddress("someone@gmail.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
