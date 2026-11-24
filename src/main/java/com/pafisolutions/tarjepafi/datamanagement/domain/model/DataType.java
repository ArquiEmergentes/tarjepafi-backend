package com.pafisolutions.tarjepafi.datamanagement.domain.model;

/** What a data record describes, and which context may produce it. */
public enum DataType {
  CARD_READING(SourceContext.IOT_MONITORING),
  DEVICE_TELEMETRY(SourceContext.IOT_MONITORING),
  ATTENDANCE(SourceContext.ACADEMIC_ATTENDANCE),
  ACCESS(SourceContext.SPACE_AND_FACILITY),
  WORK_SHIFT(SourceContext.SPACE_AND_FACILITY),
  CREDENTIAL_CHANGE(SourceContext.CREDENTIALS);

  private final SourceContext source;

  DataType(SourceContext source) {
    this.source = source;
  }

  public boolean acceptsSource(SourceContext context) {
    return source == context;
  }
}
