package com.pafisolutions.tarjepafi.datamanagement.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores the payload of a data record as JSON text. */
@Converter
public class PayloadConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, Object> payload) {
    return MAPPER.writeValueAsString(payload == null ? Map.of() : payload);
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    return MAPPER.readValue(json, TYPE);
  }
}
