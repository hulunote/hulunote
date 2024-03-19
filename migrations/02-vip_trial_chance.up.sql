CREATE TABLE IF NOT EXISTS vip_trial_chances (
    "id" uuid NOT NULL DEFAULT uuid_generate_v1mc(),
    "account_id" BIGSERIAL NOT NULL,
    "kind" TEXT NOT NULL,
    "chances" int4 NOT NULL DEFAULT 0,
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "vip_trial_chances_pkey" PRIMARY KEY ("id")
);
