CREATE TABLE IF NOT EXISTS hulunote_bot_group_summary_cache (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v1mc(),
  platform TEXT NOT NULL,
  group_uuid TEXT NOT NULL,
  day_str TEXT NOT NULL,
  summary TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
