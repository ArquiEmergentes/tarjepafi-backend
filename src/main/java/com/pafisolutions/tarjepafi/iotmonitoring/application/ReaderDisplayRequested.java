package com.pafisolutions.tarjepafi.iotmonitoring.application;

import java.util.List;

/**
 * Something to show on a reader's screen. Published as a Spring event; once the transaction commits the
 * infrastructure sends it to {@code tarjepafi.devices} (routing key {@code tarjepafi.readers.<id>.display}).
 *
 * @param eventId the tap event the reply belongs to
 * @param tone {@code success}, {@code info} or {@code error}
 * @param lines plain ASCII text lines
 */
public record ReaderDisplayRequested(
    String readerId, String eventId, String tone, List<String> lines) {}
