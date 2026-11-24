#include "ClockUtil.h"

#include <sys/time.h>
#include <time.h>

namespace clockutil {

static const time_t EARLIEST_VALID_TIME = 1700000000;

bool synced() {
  return time(nullptr) >= EARLIEST_VALID_TIME;
}

uint64_t nowMs() {
  struct timeval now;
  gettimeofday(&now, nullptr);
  return static_cast<uint64_t>(now.tv_sec) * 1000ULL + static_cast<uint64_t>(now.tv_usec / 1000);
}

void formatIso(uint64_t epochMs, char *out, size_t size) {
  time_t seconds = static_cast<time_t>(epochMs / 1000ULL);
  struct tm utc;
  gmtime_r(&seconds, &utc);
  size_t written = strftime(out, size, "%Y-%m-%dT%H:%M:%S", &utc);
  snprintf(out + written, size - written, ".%03uZ", static_cast<unsigned>(epochMs % 1000ULL));
}

}  // namespace clockutil
