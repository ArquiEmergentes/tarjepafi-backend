package com.pafisolutions.tarjepafi.shared.infrastructure.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The demo data set ({@code seed/db.json}, a copy of the web app's {@code server/db.json}). Each
 * bounded context has its own seeder that reads only its own collections from here.
 */
@Component
@Profile(DemoSeed.PROFILE)
public class DemoSeed {

  public static final String PROFILE = "demo";

  private final JsonNode root;

  public DemoSeed(ObjectMapper mapper) {
    try (InputStream input = new ClassPathResource("seed/db.json").getInputStream()) {
      this.root = mapper.readTree(input);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /** A single object of the data set, for example {@code settings}; null when it is absent. */
  public JsonNode object(String name) {
    JsonNode node = root.path(name);
    return node.isObject() ? node : null;
  }

  /** The rows of one collection, for example {@code users}. Empty when the collection is absent. */
  public Iterable<JsonNode> collection(String name) {
    JsonNode node = root.path(name);
    return node.isArray() ? node : List.of();
  }
}
