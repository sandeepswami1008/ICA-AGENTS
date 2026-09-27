-- ============================================================================
-- ICA-Agents mobile app — additive migration
-- Safe to run on the shared DMS/EMS database. Adds columns/tables only.
-- Nothing here removes or renames existing columns, so DMS and EMS keep
-- working exactly as they do today even before the app is deployed.
-- Run this ONCE against the shared database (same one both portals use).
-- ============================================================================

-- 1. KYC + contact fields on the existing users table
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS mobile_number VARCHAR(20) NULL AFTER email,
  ADD COLUMN IF NOT EXISTS pan_enc VARBINARY(512) NULL AFTER mobile_number,
  ADD COLUMN IF NOT EXISTS pan_last4 VARCHAR(4) NULL AFTER pan_enc,
  ADD COLUMN IF NOT EXISTS aadhaar_enc VARBINARY(512) NULL AFTER pan_last4,
  ADD COLUMN IF NOT EXISTS aadhaar_last4 VARCHAR(4) NULL AFTER aadhaar_enc,
  ADD COLUMN IF NOT EXISTS bank_account_enc VARBINARY(512) NULL AFTER aadhaar_last4,
  ADD COLUMN IF NOT EXISTS bank_ifsc VARCHAR(20) NULL AFTER bank_account_enc,
  ADD COLUMN IF NOT EXISTS bank_account_last4 VARCHAR(4) NULL AFTER bank_ifsc,
  ADD COLUMN IF NOT EXISTS bank_holder_name VARCHAR(120) NULL AFTER bank_account_last4,
  ADD COLUMN IF NOT EXISTS registration_source VARCHAR(20) NULL DEFAULT 'admin' AFTER bank_holder_name,
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'approved' AFTER registration_source,
  -- approval_status: 'pending' | 'approved' | 'rejected'  (existing admin-created users default to 'approved')
  ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64) NULL AFTER approval_status,
  ADD COLUMN IF NOT EXISTS approved_at DATETIME NULL AFTER approved_by,
  ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255) NULL AFTER approved_at;

-- 2. KYC document store — never a public URL, served only via kyc-doc.php
CREATE TABLE IF NOT EXISTS agent_kyc_documents (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  doc_type VARCHAR(20) NOT NULL,          -- 'pan' | 'aadhaar_front' | 'aadhaar_back' | 'photo'
  storage_path VARCHAR(255) NOT NULL,     -- path outside webroot, see PATCH-NOTES.md
  original_name VARCHAR(255) NULL,
  mime_type VARCHAR(80) NULL,
  uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_username (username),
  CONSTRAINT fk_kyc_username FOREIGN KEY (username) REFERENCES users(username)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Shared long-lived mobile bearer tokens (DMS + EMS both read this table)
CREATE TABLE IF NOT EXISTS mobile_auth_tokens (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  token_hash CHAR(64) NOT NULL,           -- SHA-256 of the actual token; raw token never stored
  device_label VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at DATETIME NULL,
  expires_at DATETIME NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uq_token_hash (token_hash),
  INDEX idx_username (username),
  CONSTRAINT fk_token_username FOREIGN KEY (username) REFERENCES users(username)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Audit trail for KYC/PII access (who viewed a masked/unmasked doc, when)
CREATE TABLE IF NOT EXISTS agent_kyc_access_log (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  accessed_by VARCHAR(64) NOT NULL,
  subject_username VARCHAR(64) NOT NULL,
  doc_type VARCHAR(20) NULL,
  action VARCHAR(20) NOT NULL,            -- 'view' | 'download' | 'approve' | 'reject'
  ip_address VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_subject (subject_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing users are untouched (approval_status defaults to 'approved'), so
-- nobody currently active gets locked out by this migration.
