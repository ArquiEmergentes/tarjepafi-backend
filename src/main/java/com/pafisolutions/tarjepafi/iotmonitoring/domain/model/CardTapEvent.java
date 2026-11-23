package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A card read by a reader. The {@code id} is the {@code eventId} of the device message, so an event is
 * never stored twice. {@code cursor} is assigned by the database and orders events for polling.
 */
@Entity
@Table(name = "card_tap_events")
public class CardTapEvent {

  /** A card UID has 4 to 7 bytes in upper-case hexadecimal separated by ':'. */
  private static final Pattern UID_FORMAT = Pattern.compile("^[0-9A-F]{2}(:[0-9A-F]{2}){3,6}$");

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long cursor;

  @Column(name = "reader_id", nullable = false)
  private String readerId;

  @Column(name = "card_uid", nullable = false)
  private String cardUid;

  @Column(name = "tapped_at", nullable = false)
  private LocalDateTime tappedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TapOutcome outcome;

  private String tone;

  @Column(name = "message_key")
  private String messageKey;

  @Column(name = "message_params")
  private String messageParams;

  @Column(name = "display_message", nullable = false)
  private String displayMessage = "";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TapSource source;

  protected CardTapEvent() {}

  public CardTapEvent(
      String id, String readerId, String cardUid, LocalDateTime tappedAt, TapSource source) {
    this.id = id;
    this.readerId = readerId;
    this.cardUid = cardUid;
    this.tappedAt = tappedAt;
    this.source = source;
    this.outcome = TapOutcome.PENDING;
  }

  /** Seed and history rows come with their result already known. */
  public CardTapEvent(
      String id,
      String readerId,
      String cardUid,
      LocalDateTime tappedAt,
      TapOutcome outcome,
      String displayMessage,
      TapSource source) {
    this(id, readerId, cardUid, tappedAt, source);
    this.outcome = outcome;
    this.displayMessage = displayMessage;
  }

  /** Upper case, bytes separated by ':' ({@code 043d9b128f} becomes {@code 04:3D:9B:12:8F}). */
  public static String normalizeUid(String raw) {
    String hex = raw == null ? "" : raw.toUpperCase().replaceAll("[^0-9A-F]", "");
    StringBuilder uid = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      if (i > 0) {
        uid.append(':');
      }
      uid.append(hex, i, Math.min(i + 2, hex.length()));
    }
    return uid.toString();
  }

  public static boolean isValidUid(String uid) {
    return uid != null && UID_FORMAT.matcher(uid).matches();
  }

  public boolean isPending() {
    return outcome == TapOutcome.PENDING;
  }

  public boolean wasRejected() {
    return outcome == TapOutcome.REJECTED;
  }

  /** Stores the result and the text shown on the reader. Only a PENDING event can be resolved. */
  public void resolve(
      TapOutcome outcome, String tone, String messageKey, String messageParams, String displayMessage) {
    if (!isPending()) {
      throw new IllegalStateException("The tap event " + id + " is already " + this.outcome);
    }
    this.outcome = outcome;
    this.tone = tone;
    this.messageKey = messageKey;
    this.messageParams = messageParams;
    this.displayMessage = displayMessage;
  }

  public String getId() {
    return id;
  }

  public Long getCursor() {
    return cursor;
  }

  public String getReaderId() {
    return readerId;
  }

  public String getCardUid() {
    return cardUid;
  }

  public LocalDateTime getTappedAt() {
    return tappedAt;
  }

  public TapOutcome getOutcome() {
    return outcome;
  }

  public String getTone() {
    return tone;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getMessageParams() {
    return messageParams;
  }

  public String getDisplayMessage() {
    return displayMessage;
  }

  public TapSource getSource() {
    return source;
  }
}
