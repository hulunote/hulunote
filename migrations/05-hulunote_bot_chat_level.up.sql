-- 用于保存bot的短时间内发送消息时的层级信息，保存上一次创建的navid信息
CREATE TABLE hulunote_bot_chat_level (
  -- 这个是直接使用bot_uuid、group_uuid来做pk
  id VARCHAR(36) PRIMARY KEY,
  nav_id VARCHAR(36) NOT NULL,
  platform TEXT NOT NULL,
  noted_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
