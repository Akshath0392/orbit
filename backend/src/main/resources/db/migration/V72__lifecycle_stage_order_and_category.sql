-- Lifecycle mapping enrichment: stable ordering + colour-category so
-- frontend can render stage pills consistently without substring heuristics.

ALTER TABLE lifecycle_mappings
    ADD COLUMN IF NOT EXISTS display_order INT,
    ADD COLUMN IF NOT EXISTS category      VARCHAR(20);
-- category values: backlog | in-progress | qa | uat | blocked | ready | released | closed

-- Backfill default order + category for known stages (idempotent).
UPDATE lifecycle_mappings SET display_order = 10,  category = 'backlog'     WHERE gauge_stage = 'Received'             AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 15,  category = 'backlog'     WHERE gauge_stage = 'More Information'     AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 20,  category = 'backlog'     WHERE gauge_stage = 'Validated'            AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 25,  category = 'in-progress' WHERE gauge_stage = 'Business Solutioning' AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 30,  category = 'in-progress' WHERE gauge_stage = 'FSD Approval'         AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 35,  category = 'in-progress' WHERE gauge_stage = 'Solutioning'          AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 40,  category = 'in-progress' WHERE gauge_stage = 'Effort Estimation'    AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 45,  category = 'in-progress' WHERE gauge_stage = 'Approval'             AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 50,  category = 'in-progress' WHERE gauge_stage = 'To Do'                AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 55,  category = 'in-progress' WHERE gauge_stage = 'In Progress'          AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 60,  category = 'uat'         WHERE gauge_stage = 'UAT Released'         AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 65,  category = 'uat'         WHERE gauge_stage = 'Customer Validation'  AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 70,  category = 'ready'       WHERE gauge_stage = 'Ready for Production' AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 75,  category = 'released'    WHERE gauge_stage = 'Released'             AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 80,  category = 'closed'      WHERE gauge_stage = 'Closed'               AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 90,  category = 'blocked'     WHERE gauge_stage = 'Hold'                 AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 95,  category = 'blocked'     WHERE gauge_stage = 'Client Hold'          AND display_order IS NULL;
UPDATE lifecycle_mappings SET display_order = 100, category = 'closed'      WHERE gauge_stage = 'Invalid'              AND display_order IS NULL;

-- Anything else: default to 'in-progress' so the page still renders.
UPDATE lifecycle_mappings SET category = 'in-progress' WHERE category IS NULL;
UPDATE lifecycle_mappings SET display_order = 50      WHERE display_order IS NULL;
