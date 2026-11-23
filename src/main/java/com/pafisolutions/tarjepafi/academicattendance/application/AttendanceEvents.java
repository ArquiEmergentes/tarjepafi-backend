package com.pafisolutions.tarjepafi.academicattendance.application;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import java.util.LinkedHashMap;
import java.util.Map;

/** The facts of the attendance integration events, as plain JSON types. */
final class AttendanceEvents {

  private AttendanceEvents() {}

  /** Data of {@code attendance.recorded}; {@code readerId} is null for records not made by a tap. */
  static Map<String, Object> recorded(AttendanceRecord record, String readerId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("attendanceRecordId", record.getId());
    data.put("sessionId", record.getSessionId());
    data.put("cardHolderId", record.getCardHolderId());
    data.put("role", record.getRole().name());
    data.put("status", record.getStatus().name());
    data.put("source", record.getSource().name());
    if (record.getTapEventId() != null) {
      data.put("tapEventId", record.getTapEventId());
    }
    if (readerId != null) {
      data.put("readerId", readerId);
    }
    if (record.getCheckInAt() != null) {
      data.put("checkInAt", record.getCheckInAt().toString());
    }
    return data;
  }
}
