-- How many points one visit is worth. A customer's points are their visits x this number,
-- and the reward target (in points) is visits_for_reward x this number. Default 1, so
-- existing programs read exactly as before.
ALTER TABLE loyalty_programs
    ADD COLUMN points_per_visit INT NOT NULL DEFAULT 1 CHECK (points_per_visit >= 1);
