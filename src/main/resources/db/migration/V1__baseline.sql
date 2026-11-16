-- Baseline. Each bounded context adds its own migrations from V2 onwards (plan, phase 1).
-- Conventions (report 7.1.3): snake_case, plural table names, primary key "id",
-- foreign keys "<entity>_id", constraint prefixes pk_, fk_, uk_, idx_.
-- A bounded context never references another context's tables with a foreign key.
SELECT 1;
