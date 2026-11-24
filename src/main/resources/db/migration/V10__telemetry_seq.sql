-- Telemetry from live readers is stamped with the campus clock, which can repeat a second: keep the arrival order.
ALTER TABLE telemetry ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;
