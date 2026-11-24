-- Phase 6: a reader that was offline keeps its taps and sends them later with buffered = true.
ALTER TABLE card_tap_events ADD COLUMN buffered BOOLEAN NOT NULL DEFAULT FALSE;
