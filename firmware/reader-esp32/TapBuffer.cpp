#include "TapBuffer.h"

bool TapBuffer::push(const BufferedTap &tap) {
  bool kept = true;
  if (count_ == settings::TAP_BUFFER_CAPACITY) {
    head_ = (head_ + 1) % settings::TAP_BUFFER_CAPACITY;  // drop the oldest
    --count_;
    ++dropped_;
    kept = false;
  }
  items_[(head_ + count_) % settings::TAP_BUFFER_CAPACITY] = tap;
  ++count_;
  return kept;
}

const BufferedTap *TapBuffer::peek() const {
  return count_ == 0 ? nullptr : &items_[head_];
}

void TapBuffer::pop() {
  if (count_ == 0) {
    return;
  }
  head_ = (head_ + 1) % settings::TAP_BUFFER_CAPACITY;
  --count_;
}
