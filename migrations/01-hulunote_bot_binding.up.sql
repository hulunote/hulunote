-- 机器人绑定记录表
CREATE TABLE IF NOT EXISTS hulunote_bot_binding (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "platform" TEXT NOT NULL,
    "bot_uuid" TEXT NOT NULL,
    "is_delete" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_bot_binding_pkey" PRIMARY KEY ("id")
);

-- 机器人群绑定记录表
CREATE TABLE IF NOT EXISTS hulunote_bot_group_binding (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "platform" TEXT NOT NULL,
    "bot_uuid" TEXT NOT NULL,
    "group_uuid" TEXT NOT NULL,
    "database_uuid" varchar(36) NOT NULL,
    "is_delete" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_bot_group_binding_pkey" PRIMARY KEY ("id")
);

-- 机器人群聊天记录
CREATE TABLE IF NOT EXISTS hulunote_bot_group_record (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "platform" TEXT NOT NULL,
    "group_uuid" TEXT NOT NULL,
    "group_name" TEXT NOT NULL,
    "talker_name" TEXT NOT NULL,
    "talker_uuid" TEXT NOT NULL,
    "mentions" TEXT,
    "quote" TEXT,
    "content" TEXT NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_bot_group_record_pkey" PRIMARY KEY ("id")
);
