--- functions

-- uuid
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE CAST ( VARCHAR AS UUID )
WITH INOUT AS IMPLICIT;

CREATE FUNCTION uuid_timestamp(id UUID)
  RETURNS TIMESTAMPTZ AS $$
SELECT TIMESTAMP WITH TIME ZONE 'epoch' +
       (((('x' || lpad(split_part(id :: TEXT, '-', 1), 16, '0')) :: BIT(64) :: BIGINT) +
         (('x' || lpad(split_part(id :: TEXT, '-', 2), 16, '0')) :: BIT(64) :: BIGINT << 32) +
         ((('x' || lpad(split_part(id :: TEXT, '-', 3), 16, '0')) :: BIT(64) :: BIGINT & 4095) << 48) -
         122192928000000000) / 10000000) * INTERVAL '1 second';
$$ LANGUAGE SQL
IMMUTABLE
RETURNS NULL ON NULL INPUT;

---

--- tables

-- accounts
CREATE TABLE "public"."accounts" (
  "id" BIGSERIAL PRIMARY KEY,
  "username" text COLLATE "pg_catalog"."default" NOT NULL,
  "nickname" text COLLATE "pg_catalog"."default",
  "password" text COLLATE "pg_catalog"."default",
  "mail" text COLLATE "pg_catalog"."default",
  "address" text COLLATE "pg_catalog"."default",
  "introduction" text COLLATE "pg_catalog"."default",
  "avatar" text COLLATE "pg_catalog"."default",
  "info" text COLLATE "pg_catalog"."default",
  "need_update_password" bool DEFAULT false,
  "invitation_code" text COLLATE "pg_catalog"."default",
  "state" text COLLATE "pg_catalog"."default",
  -- 手机号，因为是外服的，记录时记录区号
  "cell_number" text COLLATE "pg_catalog"."default",
  "show_link" bool DEFAULT false,
  "is_new_user" bool NOT NULL DEFAULT true,
  -- 这里不再使用openid, unonid, ios_key 之类的，直接使用oauth_key来直接指代
  "oauth_key" text COLLATE "pg_catalog"."default",
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "accounts_mail_key" UNIQUE ("mail"),
  CONSTRAINT "accounts_username_key" UNIQUE ("username"),
  CONSTRAINT "accounts_invitation_code_key" UNIQUE ("invitation_code"),
  CONSTRAINT "accounts_cell_number_key" UNIQUE ("cell_number")
);

ALTER TABLE "public"."accounts" OWNER TO "postgres";

-- account_login_log 用户登录使用的记录
CREATE TABLE "public"."account_login_log" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "login_times" int4 NOT NULL,
  "day_str" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "account_login_log_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "idx_account_day_unique" UNIQUE ("account_id", "day_str")
);

ALTER TABLE "public"."account_login_log" OWNER TO "postgres";

-- app_error_messages app端的错误记录
CREATE TABLE "public"."app_error_messages" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "device" text COLLATE "pg_catalog"."default" NOT NULL,
  "err_message" text COLLATE "pg_catalog"."default",
  "err_stack" text COLLATE "pg_catalog"."default",
  "ignore_or_fixed" bool DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "app_error_messages_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."app_error_messages" OWNER TO "postgres";

-- chatgpt_message_record ChatGPT消息记录
CREATE TABLE "public"."chatgpt_message_record" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "day_str" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "account_id" BIGSERIAL NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "platform" text COLLATE "pg_catalog"."default" NOT NULL,
  "platform_user_name" text COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "record_id" varchar(50) COLLATE "pg_catalog"."default",
  "result" text COLLATE "pg_catalog"."default",
  CONSTRAINT "chatgpt_message_record_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."chatgpt_message_record" OWNER TO "postgres";

-- chatgpt_usage ChatGPT的使用记录
CREATE TABLE "public"."chatgpt_usage" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "day_str" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "request_times" int4 NOT NULL DEFAULT 0,
  "response_times" int4 NOT NULL DEFAULT 0,
  "failed_times" int4 NOT NULL DEFAULT 0,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "chatgpt_usage_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."chatgpt_usage" OWNER TO "postgres";

-- hulunote_error_records 后端错误记录
CREATE TABLE "public"."hulunote_error_records" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "err_message" text COLLATE "pg_catalog"."default",
  "err_stack" text COLLATE "pg_catalog"."default",
  "ignore_or_fixed" bool DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_error_records_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_error_records" OWNER TO "postgres";

-- hulunote_database_actions 笔记库的操作记录
CREATE TABLE "public"."hulunote_database_actions" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "database_version" BIGSERIAL NOT NULL,
  "action_kind" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_database_actions_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_database_actions" OWNER TO "postgres";

-- hulunote_note_grammer_analyze 笔记使用的语法分析记录
CREATE TABLE "public"."hulunote_note_grammer_analyze" (
  "nav_id" uuid NOT NULL,
  "feature_grammer" text COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::text,
  "markdown_grammer" text COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::text,
  "account_id" BIGSERIAL NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_note_grammer_analyze_pkey" PRIMARY KEY ("nav_id")
);

ALTER TABLE "public"."hulunote_note_grammer_analyze" OWNER TO "postgres";

-- hulunote_note_tags 笔记标签
CREATE TABLE "public"."hulunote_note_tags" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "owner_id" BIGSERIAL NOT NULL,
  "is_public" bool NOT NULL,
  "is_note_delete" bool NOT NULL,
  "tag_name" text COLLATE "pg_catalog"."default" NOT NULL,
  "tag_id" BIGSERIAL NOT NULL,
  "is_delete" bool NOT NULL DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_note_tags_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_note_tags" OWNER TO "postgres";

-- hulunote_note_tag_likes 用户对笔记标签的操作
CREATE TABLE "public"."hulunote_note_tag_likes" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "tag_name" text COLLATE "pg_catalog"."default" NOT NULL,
  "tag_id" BIGSERIAL NOT NULL,
  "is_read" int2 NOT NULL DEFAULT 0,
  "is_like" int2 NOT NULL DEFAULT 0,
  "is_comment" int2 NOT NULL DEFAULT 0,
  "is_forward" int2 NOT NULL DEFAULT 0,
  "is_collect" int2 NOT NULL DEFAULT 0,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_note_tag_likes_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_note_tag_likes" OWNER TO "postgres";

-- hulunote_notes_uvs 笔记的uv数据记录
CREATE TABLE "public"."hulunote_notes_uvs" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "account_id" int8,
  "date_sign" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_notes_uvs_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "hulunote_notes_uvs_account_id_fkey" FOREIGN KEY ("account_id") REFERENCES "public"."accounts" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT "idx_notes_uvs_single" UNIQUE ("note_id", "account_id", "date_sign")
);

ALTER TABLE "public"."hulunote_notes_uvs" OWNER TO "postgres";

-- hulunote_seed_transaction 葫芦籽的交易记录
CREATE TABLE "public"."hulunote_seed_transaction" (
  "id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "account_id" BIGSERIAL NOT NULL,
  "trade_type" int4 NOT NULL,
  "trade_info" text COLLATE "pg_catalog"."default" NOT NULL,
  "fee" int8 NOT NULL DEFAULT 0,
  "extra_text" text COLLATE "pg_catalog"."default",
  "extra_int" int8,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "hulunote_seed_transaction_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_seed_transaction" OWNER TO "postgres";

-- hulunote_seed_balance 葫芦籽的存储情况
-- 这里也是不做从头计算，葫芦籽的交易会比会员交易要快要多，容易造成过多计算
CREATE TABLE "public"."hulunote_seed_balance" (
    "account_id" BIGSERIAL PRIMARY KEY,
    "balance" int8 NOT NULL DEFAULT 0,
    "updated_at" timestamptz(6) NOT NULL DEFAULT now()
);

ALTER TABLE "public"."hulunote_seed_balance" OWNER TO "postgres";

-- import_export_using_record 文件导入导出记录
CREATE TABLE "public"."import_export_using_record" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "is_import" bool NOT NULL,
  "kind" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "data_id" text COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "import_export_using_record_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."import_export_using_record" OWNER TO "postgres";

-- invitation_codes 邀请码
CREATE TABLE "public"."invitation_codes" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "invite_account" int8,
  "invitee_account" int8,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "invitation_codes_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "invitation_codes_invite_account_fkey" FOREIGN KEY ("invite_account") REFERENCES "public"."accounts" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT "invitation_codes_invitee_account_fkey" FOREIGN KEY ("invitee_account") REFERENCES "public"."accounts" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT "invitation_codes_invitee_account_key" UNIQUE ("invitee_account")
);

ALTER TABLE "public"."invitation_codes" OWNER TO "postgres";

-- ot_action ot的操作记录
CREATE TABLE "public"."ot_action" (
  "id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "version" int8 NOT NULL,
  "message_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "messages" text COLLATE "pg_catalog"."default" NOT NULL,
  "auth" int8 NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_action_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_action" OWNER TO "postgres";

-- ot_chat_active OT聊天的启动记录
CREATE TABLE "public"."ot_chat_active" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_chat_active_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_chat_active" OWNER TO "postgres";

-- ot_chat_admin_record OT与管理员的聊天记录
CREATE TABLE "public"."ot_chat_admin_record" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "is_admin" bool NOT NULL DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_chat_admin_record_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_chat_admin_record" OWNER TO "postgres";

-- ot_chat_admin_state OT与管理员的聊天状态
CREATE TABLE "public"."ot_chat_admin_state" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "is_admin_read" bool NOT NULL,
  "is_user_read" bool NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_chat_admin_state_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_chat_admin_state" OWNER TO "postgres";

-- ot_chat_record OT聊天的记录
CREATE TABLE "public"."ot_chat_record" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "parid" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "account_id" BIGSERIAL NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "has_tag" bool NOT NULL DEFAULT false,
  "has_ref_block" bool NOT NULL DEFAULT false,
  "has_link" bool NOT NULL DEFAULT false,
  "has_mention" bool NOT NULL DEFAULT false,
  "mentions" text COLLATE "pg_catalog"."default" NOT NULL DEFAULT '[]'::text,
  "is_updated" bool NOT NULL DEFAULT false,
  "origin_content" text COLLATE "pg_catalog"."default",
  "is_delete" bool NOT NULL DEFAULT false,
  "is_resave" bool NOT NULL DEFAULT false,
  "resave_note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_chat_record_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_chat_record" OWNER TO "postgres";

-- ot_chat_user_state OT聊天的用户状态
CREATE TABLE "public"."ot_chat_user_state" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "has_unread" bool NOT NULL DEFAULT false,
  "unread_time" timestamptz(6) NOT NULL DEFAULT now(),
  "has_mention" bool NOT NULL DEFAULT false,
  "mention_in" varchar(36) COLLATE "pg_catalog"."default",
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_chat_user_state_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "idx_account_database_unique" UNIQUE ("account_id", "database_id")
);

ALTER TABLE "public"."ot_chat_user_state" OWNER TO "postgres";

-- ot_db_invites OT笔记库邀请记录
CREATE TABLE "public"."ot_db_invites" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "inviter_id" BIGSERIAL NOT NULL,
  "invitee_id" BIGSERIAL NOT NULL,
  "is_new_sign_up" bool NOT NULL DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_db_invites_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_db_invites" OWNER TO "postgres";

-- ot_error_msg_tmp OT消息错误记录
CREATE TABLE "public"."ot_error_msg_tmp" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "database_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "ot_message" text COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_error_msg_tmp_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."ot_error_msg_tmp" OWNER TO "postgres";

-- ot_note_invites OT笔记邀请记录
CREATE TABLE "public"."ot_note_invites" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "note_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "note_title" text COLLATE "pg_catalog"."default" NOT NULL,
  "inviter_id" BIGSERIAL NOT NULL,
  "invitee_id" BIGSERIAL NOT NULL,
  "invite_code" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "is_delete" bool NOT NULL DEFAULT false,
  "is_active" bool NOT NULL DEFAULT false,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
  CONSTRAINT "ot_note_invites_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "idx_invite_code_unique" UNIQUE ("invite_code")
);

ALTER TABLE "public"."ot_note_invites" OWNER TO "postgres";

-- user_usage_reports 用户使用记录报告
CREATE TABLE "public"."user_usage_reports" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
  "account_id" BIGSERIAL NOT NULL,
  "bot_uuid" varchar(36) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'NOT_BINDED'::character varying,
  "login_times" int4 NOT NULL DEFAULT 0,
  "note_create" int4 NOT NULL DEFAULT 0,
  "nav_edit" int4 NOT NULL DEFAULT 0,
  "markdown_analyze" text COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::text,
  "feature_analyze" text COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::text,
  "invite_count" int4 NOT NULL DEFAULT 0,
  "mention_count" int4 NOT NULL DEFAULT 0,
  "day_str" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
  "nav_words" int4 NOT NULL DEFAULT 0,
  CONSTRAINT "user_usage_reports_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."user_usage_reports" OWNER TO "postgres";

--- 以下原本是datomic的模型，海外版暂时仍在考虑是否使用datomic，暂时先用pg表

-- hulunote_databases 笔记库
CREATE TABLE "public"."hulunote_databases" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "name" TEXT NOT NULL,
    "description" TEXT,
    "is_delete" bool NOT NULL DEFAULT false,
    "is_public" bool NOT NULL DEFAULT false,
    "is_offline" bool NOT NULL DEFAULT false,
    "is_default" bool NOT NULL DEFAULT false,
    "bot_group_platform" TEXT NOT NULL DEFAULT '',
    "account_id" BIGSERIAL NOT NULL,
    "favorite-notes" TEXT NOT NULL DEFAULT '[]',
    "setting" TEXT NOT NULL DEFAULT '{}',
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_databases_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_databases" OWNER TO "postgres";

-- hulunote_catalog 笔记库目录
CREATE TABLE "public"."hulunote_catalogs" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "name" TEXT NOT NULL,
    "parid" varchar(36),
    "database_id" varchar(36) NOT NULL,
    "is_delete" bool NOT NULL DEFAULT false,
    "account_id" BIGSERIAL NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_catalogs_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_catalogs" OWNER TO "postgres";

-- hulunote_note 笔记
CREATE TABLE "public"."hulunote_notes" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "title" TEXT NOT NULL,
    "database_id" varchar(36) NOT NULL,
    "root_nav_id" varchar(36) NOT NULL,
    "is_delete" bool NOT NULL DEFAULT false,
    "is_public" bool NOT NULL DEFAULT false,
    "is_shortcut" bool NOT NULL DEFAULT false,
    "current_updater" varchar(36),
    "account_id" BIGSERIAL NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    "import_uuid" varchar(36),
    "merged_to" varchar(36),
    "pv" int8 NOT NULL DEFAULT 0,
    "catalog_id" varchar(36),
    CONSTRAINT "hulunote_notes_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "idx_database_title_unique" UNIQUE ("database_id", "title")
);

ALTER TABLE "public"."hulunote_notes" OWNER TO "postgres";

-- hulunote_navs
CREATE TABLE "public"."hulunote_navs" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "parid" varchar(36) NOT NULL,
    "same_deep_order" float4 NOT NULL,
    "content" TEXT NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    "note_id" varchar(36) NOT NULL,
    "database_id" varchar(36) NOT NULL,
    "is_display" bool NOT NULL DEFAULT true,
    "is_public" bool NOT NULL DEFAULT false,
    "is_delete" bool NOT NULL DEFAULT false,
    "current_updater" varchar(36),
    "properties" TEXT NOT NULL DEFAULT '',
    "extra_id" TEXT NOT NULL DEFAULT '', -- 多一个可以搜索检索得到nav的id
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "hulunote_navs_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."hulunote_navs" OWNER TO "postgres";

-- payments
CREATE TABLE "public"."payments" (
    "order_id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    -- amount采用微信支付的方案，使用去小数点，如 $3.99 = 399，$14.99=1499 
    "amount" int4 NOT NULL,
    -- 币种，用数字指代，具体由业务代码决定
    "currency" int4 NOT NULL,
    -- 原版的没有月数，只靠amount去判断，这里加上月数判定
    "period" int4 NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    "discount" float4 NOT NULL,
    "cell_number" text COLLATE "pg_catalog"."default",
    "source" TEXT NOT NULL,
    "state" int4 NOT NULL DEFAULT 0,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "verify_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "payments_pkey" PRIMARY KEY ("order_id")
); 

ALTER TABLE "public"."payments" OWNER TO "postgres";

-- paycodes
CREATE TABLE "public"."paycodes" (
    "code_id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "order_id" uuid NOT NULL,
    "amount" int4 NOT NULL,
    "period" int4 NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    "pay_account_id" BIGSERIAL NOT NULL,
    "discount" float4 NOT NULL,
    "cell_number" text COLLATE "pg_catalog"."default",
    "source" TEXT NOT NULL,
    "state" int4 NOT NULL DEFAULT 0,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "verify_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "paycodes_pkey" PRIMARY KEY ("order_id")
);

ALTER TABLE "public"."paycodes" OWNER TO "postgres";

-- vip_states 
-- 这里不再使用原本的从头开始计算，这样会造成太多计算了，直接用一个表去存储用户的专业版状态
CREATE TABLE "public"."vip_states" (
    "account_id" BIGSERIAL PRIMARY KEY,
    "state" int4 NOT NULL,
    "start_at" timestamptz(6) NOT NULL DEFAULT now(),
    "expired_at" timestamptz(6) NOT NULL DEFAULT now()
);

ALTER TABLE "public"."vip_states" OWNER TO "postgres";

-- offline_codes
CREATE TABLE "public"."offline_codes" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "code" TEXT NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "verify_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "offline_codes_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."offline_codes" OWNER TO "postgres";

-- cancans 权限表，名字按照原版的名字, 直接做二维关联表
CREATE TABLE "public"."cancans" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "database_id" uuid NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    "can_read" bool NOT NULL DEFAULT false,
    "can_write" bool NOT NULL DEFAULT false,
    "is_delete" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "cancans_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."cancans" OWNER TO "postgres";

-- user_settings
CREATE TABLE "public"."user_settings" (
    "account_id" BIGSERIAL PRIMARY KEY,
    "my_api_key" TEXT NOT NULL,
    "auto_suggest_note" TEXT,
    "default_theme_mode" VARCHAR(50) NOT NULL,
    "quick_input_mode" bool NOT NULL DEFAULT false,
    "safe_mode" bool NOT NULL DEFAULT false,
    "offline_code" TEXT,
    "extra_json" TEXT
);

ALTER TABLE "public"."user_settings" OWNER TO "postgres";

-- short_links 
CREATE TABLE "public"."short_links" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "short_link_url" TEXT NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "short_links_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."short_links" OWNER TO "postgres";

-- audio_codes
CREATE TABLE "public"."audio_codes" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "code" TEXT NOT NULL,
    "is_active" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "audio_codes_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."audio_codes" OWNER TO "postgres";

-- note_share_settings
CREATE TABLE "public"."note_share_settings" (
    "note_id" uuid NOT NULL,
    "account_id" BIGSERIAL NOT NULL,
    -- 分享等级：公开:public 葫芦用户:hulunote 指定用户:designated
    "level" int4 NOT NULL,
    "designated_list_json" TEXT,
    -- 评论等级: 依分享等级:default 指定用户:designated 关闭:close
    "comment_setting" int4 NOT NULL,
    "comment_designated_list_json" TEXT,
    -- 点赞等级：开启:open 关闭:close
    "like_setting" int4 NOT NULL,
    -- 转发设置：开启:open 关闭:close 指定用户:designated
    "forward_setting" int4 NOT NULL,
    "forward_designated_list_json" TEXT,
    "short_link" TEXT, 
    -- 分享的状态：审核中:verifying 审核通过:pass 审核不通过:deny 已取消:cancel
    "state" int4 NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "note_share_settings_pkey" PRIMARY KEY ("note_id")
);

ALTER TABLE "public"."note_share_settings" OWNER TO "postgres";

-- note_comments
CREATE TABLE "public"."note_comments" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "note_id" uuid NOT NULL,
    "parent_id" uuid NOT NULL,
    "child_count" int4 NOT NULL DEFAULT 0,
    "account_id" BIGSERIAL NOT NULL,
    "reply_id" uuid,
    "content" TEXT NOT NULL,
    "is_first" bool NOT NULL,
    "is_updated" bool NOT NULL DEFAULT false,
    "is_deleted" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "note_comments_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."note_comments" OWNER TO "postgres";

-- note_likes
CREATE TABLE "public"."note_likes" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "note_id" uuid NOT NULL,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "note_likes_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "idx_account_note_unique" UNIQUE ("account_id", "note_id")
);

ALTER TABLE "public"."note_likes" OWNER TO "postgres";

-- note_forwards
CREATE TABLE "public"."note_forwards" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "note_id" uuid NOT NULL,
    "is_active" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "note_forwards_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."note_forwards" OWNER TO "postgres";

-- note_share_favorites （分享的）笔记收藏表
CREATE TABLE "public"."note_share_favorites" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "note_id" uuid NOT NULL,
    "short_link" TEXT NOT NULL,
    "is_deleted" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "note_share_favorites_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."note_share_favorites" OWNER TO "postgres";

-- database_share_settings
CREATE TABLE "public"."database_share_settings" (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "database_id" uuid NOT NULL,
    "short_link" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "is_deleted" bool NOT NULL DEFAULT false,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "database_share_settings_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."database_share_settings" OWNER TO "postgres";
