CREATE TABLE hulunote_bot_notification (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v1mc(),
  account_id BIGSERIAL NOT NULL,
  bot_uuid VARCHAR(36) NOT NULL,
  platform TEXT NOT NULL,
  remind_text TEXT NOT NULL,
  remind_at VARCHAR(20) NOT NULL,
  is_group BOOL NOT NULL DEFAULT FALSE, 
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
