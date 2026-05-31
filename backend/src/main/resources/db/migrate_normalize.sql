-- One-time migration: normalize category / priority / sentiment to uppercase.
-- Run once against the dev database after upgrading to the classify-only pipeline.

-- category: uppercase + rename legacy values
UPDATE processing_results SET category = CASE
  WHEN LOWER(category) = 'work'       THEN 'WORK'
  WHEN LOWER(category) = 'personal'   THEN 'PERSONAL'
  WHEN LOWER(category) = 'social'     THEN 'PERSONAL'
  WHEN LOWER(category) = 'finance'    THEN 'FINANCE'
  WHEN LOWER(category) = 'newsletter' THEN 'PROMOTIONS'
  WHEN LOWER(category) = 'promotions' THEN 'PROMOTIONS'
  WHEN LOWER(category) = 'other'      THEN 'OTHER'
  ELSE UPPER(category)
END
WHERE category IS NOT NULL AND category != UPPER(category);

-- priority: uppercase
UPDATE processing_results SET priority = UPPER(priority)
WHERE priority IS NOT NULL AND priority != UPPER(priority);

-- sentiment: uppercase
UPDATE processing_results SET sentiment = UPPER(sentiment)
WHERE sentiment IS NOT NULL AND sentiment != UPPER(sentiment);
