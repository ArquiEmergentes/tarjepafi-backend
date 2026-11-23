package com.pafisolutions.tarjepafi.shared.application;

import java.util.Map;

/**
 * An integration event between bounded contexts (7.1.3). A context publishes it with Spring's
 * {@code ApplicationEventPublisher}; once the surrounding transaction has committed it leaves for the
 * {@code tarjepafi.events} exchange with {@code routingKey} (for example {@code credentials.credential-blocked}).
 *
 * @param routingKey {@code <context>.<event-name>}, kebab-case
 * @param data the facts of the event; plain JSON types only
 */
public record IntegrationEvent(String routingKey, Map<String, Object> data) {}
