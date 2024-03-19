CREATE TABLE hulunote_bot_user_setting (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v1mc(),
    platform TEXT NOT NULL,
    key_type VARCHAR(50) NOT NULL, -- 描述key的类型，有group和single
    key_id VARCHAR(100) NOT NULL, -- key，group的时候是群名，single的时候是bot_uuid
    setting_context TEXT NOT NULL DEFAULT '{}', -- 记录设置的json，后续可能还会有别的设置，这里直接用json记录
    on_schedule BOOLEAN NOT NULL DEFAULT FALSE, -- 是否需要定时调度的，这里要使用字段而不是在context里，方便定时任务的计算查询
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
