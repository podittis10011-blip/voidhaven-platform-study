-- VOIDHAVEN 当前库终态（V1～V24 合并，不含迁移过程）
-- 引擎 InnoDB，字符集 utf8mb4 / utf8mb4_0900_ai_ci，时间 TIMESTAMP(6) + 应用层 UTC
-- 这不是 Flyway 迁移，不要对已有业务库执行。

CREATE DATABASE IF NOT EXISTS voidhaven
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE voidhaven;

-- =============================================================================
-- 1. 账号与凭据
-- =============================================================================

CREATE TABLE accounts (
  id CHAR(26) PRIMARY KEY,
  login_id VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  password_changed_at TIMESTAMP(6) NULL,
  qualification_status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_account_qualification
    CHECK (qualification_status IN ('PENDING','ACTIVE','SUSPENDED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_roles (
  account_id CHAR(26) NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (account_id, role_id),
  CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_account_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE partner_profiles (
  id CHAR(26) PRIMARY KEY,
  account_id CHAR(26) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  content_quota INT NOT NULL DEFAULT 3,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_partner_profile_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_partner_content_quota CHECK (content_quota BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activation_tokens (
  id CHAR(26) PRIMARY KEY,
  account_id CHAR(26) NOT NULL,
  token_type VARCHAR(32) NOT NULL DEFAULT 'ACTIVATION',
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_activation_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_activation_token_type
    CHECK (token_type IN ('ACTIVATION','PASSWORD_RESET','PASSWORD_RESET_SELF_SERVICE','RECOVERY_EMAIL_VERIFY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- V18 留下的空表：V19 已清数据并关闭自助找回，表本身未 DROP
CREATE TABLE account_recovery_emails (
  account_id CHAR(26) PRIMARY KEY,
  verified_email_ciphertext TEXT NULL,
  verified_email_key_id VARCHAR(100) NULL,
  verified_at TIMESTAMP(6) NULL,
  pending_email_ciphertext TEXT NULL,
  pending_email_key_id VARCHAR(100) NULL,
  pending_requested_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_recovery_email_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_recovery_email_verified_pair CHECK (
    (verified_email_ciphertext IS NULL AND verified_email_key_id IS NULL AND verified_at IS NULL)
    OR
    (verified_email_ciphertext IS NOT NULL AND verified_email_key_id IS NOT NULL AND verified_at IS NOT NULL)
  ),
  CONSTRAINT chk_recovery_email_pending_pair CHECK (
    (pending_email_ciphertext IS NULL AND pending_email_key_id IS NULL AND pending_requested_at IS NULL)
    OR
    (pending_email_ciphertext IS NOT NULL AND pending_email_key_id IS NOT NULL AND pending_requested_at IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 2. 内容内核（候选修订 / 已发布修订拆开）
-- =============================================================================

CREATE TABLE content_items (
  id CHAR(26) PRIMARY KEY,
  kind VARCHAR(32) NOT NULL,
  module_key VARCHAR(64) NOT NULL,
  owner_partner_profile_id CHAR(26) NULL,
  workflow_status VARCHAR(32) NOT NULL,
  publication_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
  operational_status VARCHAR(16) NULL,
  visibility VARCHAR(16) NOT NULL,
  record_state VARCHAR(16) NOT NULL,
  current_revision_id CHAR(26) NULL,
  published_revision_id CHAR(26) NULL,
  closed_at TIMESTAMP(6) NULL,
  archived_at TIMESTAMP(6) NULL,
  submitted_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_content_owner FOREIGN KEY (owner_partner_profile_id) REFERENCES partner_profiles(id),
  CONSTRAINT chk_content_kind CHECK (kind IN ('COMPETITION','RESOURCE','RECRUITMENT')),
  CONSTRAINT chk_content_workflow
    CHECK (workflow_status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','PUBLISHED','CLOSED','REJECTED')),
  CONSTRAINT chk_content_publication_status
    CHECK (publication_status IN ('NONE','ACTIVE','CLOSED')),
  CONSTRAINT chk_content_operational_status
    CHECK (operational_status IS NULL OR operational_status IN ('OPEN','PAUSED','FILLED','EXPIRED','CANCELLED')),
  CONSTRAINT chk_content_visibility CHECK (visibility IN ('PUBLIC','HIDDEN')),
  CONSTRAINT chk_content_record_state CHECK (record_state IN ('ACTIVE','ARCHIVED','DELETED')),
  INDEX idx_content_items_module_public (module_key, visibility, record_state, id),
  INDEX idx_content_management_page (workflow_status, record_state, id),
  INDEX idx_content_operational_status (kind, operational_status, visibility, record_state),
  INDEX idx_content_items_kind_updated (kind, visibility, record_state, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_revisions (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  revision_number INT NOT NULL,
  title VARCHAR(240) NOT NULL,
  original_title VARCHAR(240) NULL,
  summary VARCHAR(1000) NOT NULL,
  body_json JSON NOT NULL,
  markdown_source MEDIUMTEXT NULL,
  document_version INT NOT NULL DEFAULT 1,
  schema_version INT NOT NULL DEFAULT 1,
  schema_snapshot_json JSON NULL,
  language_code VARCHAR(16) NOT NULL,
  source_url VARCHAR(2048) NULL,
  source_name VARCHAR(240) NOT NULL,
  rights_basis VARCHAR(120) NOT NULL,
  author_display_name VARCHAR(120) NOT NULL DEFAULT '未公开署名',
  reprint_permission VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED',
  adaptation_permission VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED',
  details_json JSON NOT NULL,
  metadata_json JSON NULL,
  created_by_account_id CHAR(26) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (content_item_id, revision_number),
  CONSTRAINT fk_revision_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_revision_author FOREIGN KEY (created_by_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE content_items
  ADD CONSTRAINT fk_content_current_revision FOREIGN KEY (current_revision_id) REFERENCES content_revisions(id);
ALTER TABLE content_items
  ADD CONSTRAINT fk_content_published_revision FOREIGN KEY (published_revision_id) REFERENCES content_revisions(id);

CREATE TABLE competition_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  deadline_at TIMESTAMP(6) NULL,
  deadline_date DATE NULL,
  official_rules_url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_competition_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  INDEX idx_competition_deadline_date (deadline_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recruitment_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  deadline_at TIMESTAMP(6) NULL,
  deadline_date DATE NULL,
  related_competition_id CHAR(26) NULL,
  follow_up_url VARCHAR(2048) NULL,
  CONSTRAINT fk_recruitment_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_recruitment_detail_competition FOREIGN KEY (related_competition_id) REFERENCES content_items(id),
  INDEX idx_recruitment_deadline_date (deadline_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  resource_type VARCHAR(64) NOT NULL,
  CONSTRAINT fk_resource_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revision_filter_values (
  revision_id CHAR(26) NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  value_type VARCHAR(16) NOT NULL,
  value_text VARCHAR(512) NULL,
  value_number DECIMAL(20, 6) NULL,
  value_date DATE NULL,
  PRIMARY KEY (revision_id, field_key),
  CONSTRAINT fk_filter_value_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  INDEX idx_filter_value_text (field_key, value_text),
  INDEX idx_filter_value_number (field_key, value_number),
  INDEX idx_filter_value_date (field_key, value_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revision_content_relations (
  revision_id CHAR(26) NOT NULL,
  target_content_item_id CHAR(26) NOT NULL,
  relation_key VARCHAR(64) NOT NULL,
  PRIMARY KEY (revision_id, target_content_item_id, relation_key),
  CONSTRAINT fk_relation_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_relation_target FOREIGN KEY (target_content_item_id) REFERENCES content_items(id),
  INDEX idx_relation_target (target_content_item_id, relation_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE external_links (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  label VARCHAR(240) NOT NULL,
  url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_external_link_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE source_attributions (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  source_name VARCHAR(240) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_attribution_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rights_declarations (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  declaration VARCHAR(500) NOT NULL,
  CONSTRAINT fk_rights_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE review_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  revision_id CHAR(26) NOT NULL,
  reviewer_account_id CHAR(26) NULL,
  decision VARCHAR(32) NOT NULL,
  reason VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_review_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_review_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_ownership_transfers (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  from_partner_profile_id CHAR(26) NULL,
  to_partner_profile_id CHAR(26) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_transfer_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_transfer_to_profile FOREIGN KEY (to_partner_profile_id) REFERENCES partner_profiles(id),
  CONSTRAINT fk_transfer_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE partner_quota_overrides (
  id CHAR(26) PRIMARY KEY,
  partner_profile_id CHAR(26) NOT NULL,
  quota INT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_quota_profile FOREIGN KEY (partner_profile_id) REFERENCES partner_profiles(id),
  CONSTRAINT fk_quota_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_quota_override_range CHECK (quota BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE migration_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  source_reference VARCHAR(500) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_migration_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_migration_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_verification_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  detail_summary VARCHAR(1000) NULL,
  checked_by_account_id CHAR(26) NULL,
  checked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  next_check_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_content_verification_item FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_content_verification_actor FOREIGN KEY (checked_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_content_verification_outcome CHECK (outcome IN ('VALID','INVALID','NEEDS_REVIEW')),
  INDEX idx_content_verification_due (next_check_at, outcome),
  INDEX idx_content_verification_item (content_item_id, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 3. 媒体元数据（二进制在 MinIO，不进 MySQL）
-- =============================================================================

CREATE TABLE media_assets (
  id CHAR(26) PRIMARY KEY,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  media_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width_px INT NULL,
  height_px INT NULL,
  owner_account_id CHAR(26) NOT NULL,
  lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_media_owner FOREIGN KEY (owner_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_media_lifecycle_state CHECK (lifecycle_state IN ('ACTIVE','DELETION_PENDING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revision_media (
  revision_id CHAR(26) NOT NULL,
  media_asset_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL,
  alt_text VARCHAR(240) NOT NULL DEFAULT '',
  PRIMARY KEY (revision_id, media_asset_id),
  CONSTRAINT fk_revision_media_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_revision_media_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_deletion_jobs (
  id CHAR(26) PRIMARY KEY,
  media_asset_id CHAR(26) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_error VARCHAR(500) NULL,
  locked_by VARCHAR(64) NULL,
  locked_until TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_media_deletion_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT uq_media_deletion_asset UNIQUE (media_asset_id),
  CONSTRAINT chk_media_deletion_state CHECK (state IN ('PENDING','FAILED','SUCCEEDED')),
  INDEX idx_media_deletion_due (state, next_attempt_at),
  INDEX idx_media_deletion_claim (state, next_attempt_at, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_action_media_reservations (
  media_asset_id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  owner_account_id CHAR(26) NOT NULL,
  alt_text VARCHAR(240) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_action_media_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_action_media_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_action_media_owner FOREIGN KEY (owner_account_id) REFERENCES accounts(id),
  INDEX idx_action_media_expiry (expires_at, consumed_at),
  INDEX idx_action_media_content (content_item_id, owner_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 4. 统一入口（内容动作 / 社群 / 项目加入）
-- =============================================================================

CREATE TABLE content_action_entries (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  presentation_type VARCHAR(16) NOT NULL,
  contact_channel VARCHAR(16) NOT NULL,
  access_level VARCHAR(32) NOT NULL,
  label VARCHAR(120) NOT NULL,
  public_value VARCHAR(2048) NULL,
  private_value_ciphertext TEXT NULL,
  private_value_key_id VARCHAR(100) NULL,
  media_asset_id CHAR(26) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_content_action_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_content_action_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT chk_content_action_presentation CHECK (presentation_type IN ('LINK','QR_CODE','TEXT')),
  CONSTRAINT chk_content_action_channel CHECK (contact_channel IN ('URL','EMAIL','WECHAT','QQ','PHONE','OTHER')),
  CONSTRAINT chk_content_action_access CHECK (access_level IN ('PUBLIC','COMMUNITY_GATED')),
  INDEX idx_content_action_revision (revision_id, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_entries (
  id CHAR(26) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  presentation_type VARCHAR(16) NOT NULL,
  contact_channel VARCHAR(16) NOT NULL,
  access_level VARCHAR(32) NOT NULL DEFAULT 'COMMUNITY_GATED',
  public_value VARCHAR(2048) NULL,
  private_value_ciphertext TEXT NULL,
  private_value_key_id VARCHAR(100) NULL,
  media_asset_id CHAR(26) NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  primary_slot TINYINT GENERATED ALWAYS AS (
    CASE WHEN active = TRUE AND is_primary = TRUE THEN 1 ELSE NULL END
  ) STORED,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_community_entry_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_community_entry_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_community_entry_presentation CHECK (presentation_type IN ('LINK','QR_CODE','TEXT')),
  CONSTRAINT chk_community_entry_channel CHECK (contact_channel IN ('URL','EMAIL','WECHAT','QQ','PHONE','OTHER')),
  CONSTRAINT chk_community_entry_access CHECK (access_level IN ('PUBLIC','COMMUNITY_GATED')),
  CONSTRAINT uq_community_primary UNIQUE (primary_slot),
  INDEX idx_community_entry_public (active, is_primary, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_invite_batches (
  id CHAR(26) PRIMARY KEY,
  label VARCHAR(120) NOT NULL,
  code_hmac CHAR(64) NOT NULL UNIQUE,
  valid_from TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  max_uses INT NOT NULL,
  use_count INT NOT NULL DEFAULT 0,
  state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  revoked_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_community_invite_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_community_invite_state CHECK (state IN ('ACTIVE','REVOKED','EXPIRED')),
  CONSTRAINT chk_community_invite_uses CHECK (max_uses BETWEEN 1 AND 10000 AND use_count >= 0 AND use_count <= max_uses),
  INDEX idx_community_invite_active (state, valid_from, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_access_grants (
  id CHAR(26) PRIMARY KEY,
  invite_batch_id CHAR(26) NOT NULL,
  grant_hash CHAR(64) NOT NULL UNIQUE,
  anonymous_subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_community_grant_batch FOREIGN KEY (invite_batch_id) REFERENCES community_invite_batches(id),
  INDEX idx_community_grant_lookup (grant_hash, expires_at),
  INDEX idx_community_grant_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_join_entries (
  id CHAR(26) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  presentation_type VARCHAR(16) NOT NULL,
  contact_channel VARCHAR(16) NOT NULL,
  access_level VARCHAR(32) NOT NULL DEFAULT 'COMMUNITY_GATED',
  public_value VARCHAR(2048) NULL,
  private_value_ciphertext TEXT NULL,
  private_value_key_id VARCHAR(100) NULL,
  media_asset_id CHAR(26) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_project_join_entry_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_project_join_entry_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_project_join_entry_presentation CHECK (presentation_type IN ('LINK','QR_CODE','TEXT')),
  CONSTRAINT chk_project_join_entry_channel CHECK (contact_channel IN ('URL','EMAIL','WECHAT','QQ','PHONE','OTHER')),
  CONSTRAINT chk_project_join_entry_access CHECK (access_level IN ('PUBLIC','COMMUNITY_GATED')),
  INDEX idx_project_join_entry_public (active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 5. 公开工单
-- =============================================================================

CREATE TABLE content_submissions (
  id CHAR(26) PRIMARY KEY,
  submission_type VARCHAR(32) NOT NULL,
  title VARCHAR(240) NOT NULL,
  message TEXT NOT NULL,
  link_url VARCHAR(2048) NULL,
  image_description VARCHAR(1000) NULL,
  access_code_hash CHAR(64) NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  reviewed_by_account_id CHAR(26) NULL,
  review_reason VARCHAR(1000) NULL,
  reviewed_at TIMESTAMP(6) NULL,
  assignee_account_id CHAR(26) NULL,
  due_at TIMESTAMP(6) NULL,
  public_result VARCHAR(2000) NULL,
  resolved_at TIMESTAMP(6) NULL,
  private_contact_ciphertext TEXT NULL,
  private_contact_key_id VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_submission_type CHECK (submission_type IN ('CONTENT','FEEDBACK','COPYRIGHT')),
  CONSTRAINT chk_submission_public_state
    CHECK (state IN ('RECEIVED','IN_PROGRESS','WAITING_FOR_REQUESTER','RESOLVED','REJECTED','CLOSED')),
  CONSTRAINT fk_submission_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id),
  CONSTRAINT fk_submission_assignee FOREIGN KEY (assignee_account_id) REFERENCES accounts(id),
  UNIQUE INDEX uq_submission_access_code (access_code_hash),
  INDEX idx_submission_management_page (state, created_at, id),
  INDEX idx_submission_tasks (state, due_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE partner_applications (
  id CHAR(26) PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  introduction TEXT NOT NULL,
  portfolio_url VARCHAR(2048) NULL,
  access_code_hash CHAR(64) NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  reviewed_by_account_id CHAR(26) NULL,
  review_reason VARCHAR(1000) NULL,
  reviewed_at TIMESTAMP(6) NULL,
  assignee_account_id CHAR(26) NULL,
  due_at TIMESTAMP(6) NULL,
  public_result VARCHAR(2000) NULL,
  activation_delivered_at TIMESTAMP(6) NULL,
  provisioned_account_id CHAR(26) NULL,
  activated_at TIMESTAMP(6) NULL,
  resolved_at TIMESTAMP(6) NULL,
  private_contact_ciphertext TEXT NULL,
  private_contact_key_id VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_partner_application_public_state
    CHECK (state IN ('RECEIVED','IN_REVIEW','WAITING_FOR_APPLICANT','APPROVED','ACTIVATION_PENDING','ACTIVATED','REJECTED','CLOSED')),
  CONSTRAINT fk_partner_application_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id),
  CONSTRAINT fk_partner_application_assignee FOREIGN KEY (assignee_account_id) REFERENCES accounts(id),
  CONSTRAINT fk_partner_application_account FOREIGN KEY (provisioned_account_id) REFERENCES accounts(id),
  UNIQUE INDEX uq_partner_application_access_code (access_code_hash),
  CONSTRAINT uq_partner_application_account UNIQUE (provisioned_account_id),
  INDEX idx_partner_application_management_page (state, created_at, id),
  INDEX idx_partner_application_tasks (state, due_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE public_case_status_history (
  id CHAR(26) PRIMARY KEY,
  case_type VARCHAR(32) NOT NULL,
  case_id CHAR(26) NOT NULL,
  from_state VARCHAR(32) NULL,
  to_state VARCHAR(32) NOT NULL,
  public_message VARCHAR(2000) NULL,
  actor_account_id CHAR(26) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_public_case_history_actor FOREIGN KEY (actor_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_public_case_history_type CHECK (case_type IN ('SUBMISSION','PARTNER_APPLICATION')),
  INDEX idx_public_case_history_lookup (case_type, case_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE private_contact_records (
  id CHAR(26) PRIMARY KEY,
  subject_type VARCHAR(32) NOT NULL,
  subject_id CHAR(26) NOT NULL,
  ciphertext TEXT NOT NULL,
  key_id VARCHAR(100) NOT NULL,
  delete_after TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_private_contact_cleanup (delete_after)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE manual_delivery_records (
  id CHAR(26) PRIMARY KEY,
  subject_type VARCHAR(32) NOT NULL,
  subject_id CHAR(26) NOT NULL,
  delivery_type VARCHAR(32) NOT NULL,
  delivered_by_account_id CHAR(26) NOT NULL,
  delivered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  note VARCHAR(1000) NULL,
  CONSTRAINT fk_manual_delivery_actor FOREIGN KEY (delivered_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_manual_delivery_subject CHECK (subject_type IN ('ACCOUNT','PARTNER_APPLICATION','SUBMISSION')),
  CONSTRAINT chk_manual_delivery_type CHECK (delivery_type IN ('ACTIVATION','PASSWORD_RESET','STATUS_RESULT')),
  INDEX idx_manual_delivery_subject (subject_type, subject_id, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 6. 首页与目录
-- =============================================================================

CREATE TABLE homepage_placements (
  id CHAR(26) PRIMARY KEY,
  slot VARCHAR(32) NOT NULL,
  content_item_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  starts_at TIMESTAMP(6) NULL,
  ends_at TIMESTAMP(6) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_homepage_placement_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_homepage_placement_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_homepage_placement_slot CHECK (slot IN ('FOCUS','COMPETITION','RECRUITMENT','RESOURCE')),
  UNIQUE KEY uq_homepage_slot_content (slot, content_item_id),
  INDEX idx_homepage_active (slot, active, starts_at, ends_at, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE homepage_curation (
  id CHAR(26) PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  updated_by_account_id CHAR(26) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_homepage_curation_actor FOREIGN KEY (updated_by_account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE catalog_sort_settings (
  kind VARCHAR(32) PRIMARY KEY,
  default_sort VARCHAR(16) NOT NULL DEFAULT 'DEFAULT',
  version BIGINT NOT NULL DEFAULT 0,
  updated_by_account_id CHAR(26) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_catalog_sort_actor FOREIGN KEY (updated_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_catalog_sort_kind CHECK (kind IN ('COMPETITION','RESOURCE','RECRUITMENT')),
  CONSTRAINT chk_catalog_sort_mode CHECK (default_sort IN ('DEFAULT','DEADLINE','UPDATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE catalog_order_entries (
  id CHAR(26) PRIMARY KEY,
  kind VARCHAR(32) NOT NULL,
  content_item_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_catalog_order_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_catalog_order_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_catalog_order_kind CHECK (kind IN ('COMPETITION','RESOURCE','RECRUITMENT')),
  UNIQUE KEY uq_catalog_order_kind_content (kind, content_item_id),
  INDEX idx_catalog_order_active (kind, active, sort_order, content_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 7. 会话 / 幂等 / 限流 / 审计 / 留存
-- =============================================================================

CREATE TABLE SPRING_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME BIGINT NOT NULL,
  LAST_ACCESS_TIME BIGINT NOT NULL,
  MAX_INACTIVE_INTERVAL INT NOT NULL,
  EXPIRY_TIME BIGINT NOT NULL,
  PRINCIPAL_NAME VARCHAR(100),
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES BLOB NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
    FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_records (
  id CHAR(26) PRIMARY KEY,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_status SMALLINT NOT NULL,
  response_body JSON NOT NULL,
  response_replayable BOOLEAN NOT NULL DEFAULT TRUE,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (idempotency_key),
  INDEX idx_idempotency_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rate_limit_locks (
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  PRIMARY KEY (scope, subject_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_auth_rate_limit_scope
    CHECK (scope IN ('LOGIN_ID','LOGIN_NETWORK','ACTIVATION_NETWORK','PASSWORD_RESET_LOGIN','PASSWORD_RESET_NETWORK')),
  CONSTRAINT chk_auth_rate_limit_outcome CHECK (outcome IN ('ATTEMPT','FAILURE','SUCCESS')),
  INDEX idx_auth_rate_limit_lookup (scope, subject_hash, expires_at),
  INDEX idx_auth_rate_limit_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE public_write_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_public_write_rate_limit_scope CHECK (scope IN ('PUBLIC_WRITE_NETWORK')),
  INDEX idx_public_write_rate_limit_lookup (scope, subject_hash, expires_at),
  INDEX idx_public_write_rate_limit_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE public_status_query_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_public_status_query_lookup (subject_hash, expires_at),
  INDEX idx_public_status_query_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_gate_events (
  id CHAR(26) PRIMARY KEY,
  anonymous_subject_hash CHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_community_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
  id CHAR(26) PRIMARY KEY,
  actor_account_id CHAR(26) NULL,
  actor_role VARCHAR(32) NOT NULL,
  action VARCHAR(100) NOT NULL,
  object_type VARCHAR(64) NOT NULL,
  object_id CHAR(26) NOT NULL,
  before_summary JSON NULL,
  after_summary JSON NULL,
  reason VARCHAR(1000) NULL,
  request_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_audit_object (object_type, object_id),
  INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE retention_runs (
  id CHAR(26) PRIMARY KEY,
  task_name VARCHAR(100) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  affected_count INT NOT NULL,
  detail_summary VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- 8. 空库必需种子（应用启动假定这些行存在）
-- =============================================================================

INSERT INTO roles (code) VALUES ('ADMIN'), ('SUPER_ADMIN'), ('PARTNER');

INSERT INTO homepage_curation (id, version) VALUES ('01JX000H0MEPAGESTATE000001', 0);

INSERT INTO catalog_sort_settings (kind, default_sort) VALUES
  ('COMPETITION', 'DEFAULT'),
  ('RECRUITMENT', 'DEFAULT'),
  ('RESOURCE', 'DEFAULT');
