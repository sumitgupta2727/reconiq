-- =====================================================================
-- ReconIQ - database schema (MySQL 8)
--
-- Run this file once in MySQL Workbench or with:
--   mysql -u root -p < database/schema.sql
--
-- WARNING: this script drops the existing ReconIQ tables so that it can
-- be re-run safely during development.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS reconiq_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE reconiq_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS reconciliation;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS upload_batches;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- USERS ---------------------------------------------------------------
CREATE TABLE users (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  employee_id  VARCHAR(30)  NOT NULL,
  name         VARCHAR(100) NOT NULL,
  email        VARCHAR(150) NOT NULL,
  password     VARCHAR(100) NOT NULL,          -- BCrypt hash, never plain text
  role         VARCHAR(20)  NOT NULL DEFAULT 'ANALYST',   -- ADMIN / ANALYST
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_employee_id UNIQUE (employee_id),
  CONSTRAINT uq_users_email       UNIQUE (email)
) ENGINE=InnoDB;

-- ORDERS (internal records) -------------------------------------------
CREATE TABLE orders (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       VARCHAR(30)   NOT NULL,       -- business key, e.g. ORD1001
  customer_name  VARCHAR(100)  NOT NULL,
  order_amount   DECIMAL(12,2) NOT NULL,
  order_date     DATE          NOT NULL,
  gateway        VARCHAR(30)   NULL,           -- expected gateway
  status         VARCHAR(20)   NOT NULL DEFAULT 'CREATED',  -- CREATED / PAID / CANCELLED
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_orders_order_id UNIQUE (order_id),
  INDEX idx_orders_date (order_date),
  INDEX idx_orders_gateway (gateway)
) ENGINE=InnoDB;

-- UPLOAD BATCHES ------------------------------------------------------
CREATE TABLE upload_batches (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_name      VARCHAR(255) NOT NULL,
  file_type      VARCHAR(10)  NOT NULL,        -- CSV
  total_records  INT          NOT NULL DEFAULT 0,
  status         VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING / COMPLETED / PARTIAL / FAILED
  error_message  VARCHAR(500) NULL,
  uploaded_by    BIGINT       NULL,
  uploaded_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_batches_user FOREIGN KEY (uploaded_by)
    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- TRANSACTIONS (payment gateway records) ------------------------------
-- transaction_id is NOT unique on purpose: duplicates must be storable
-- so the engine can detect them.
CREATE TABLE transactions (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_id      VARCHAR(30)   NOT NULL,
  order_id            VARCHAR(30)   NULL,      -- gateway may send an unknown/empty order ref
  customer_name       VARCHAR(100)  NULL,
  gateway             VARCHAR(30)   NOT NULL,
  transaction_amount  DECIMAL(12,2) NOT NULL,
  transaction_date    DATETIME      NOT NULL,
  payment_status      VARCHAR(20)   NOT NULL,  -- SUCCESS / FAILED / PENDING
  upload_batch_id     BIGINT        NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_txn_batch FOREIGN KEY (upload_batch_id)
    REFERENCES upload_batches(id) ON DELETE SET NULL,
  INDEX idx_txn_transaction_id (transaction_id),
  INDEX idx_txn_order_id (order_id),
  INDEX idx_txn_gateway (gateway),
  INDEX idx_txn_date (transaction_date),
  INDEX idx_txn_status (payment_status)
) ENGINE=InnoDB;

-- RECONCILIATION RESULTS ----------------------------------------------
CREATE TABLE reconciliation (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id               VARCHAR(30)   NULL,
  transaction_id         VARCHAR(30)   NULL,
  order_pk               BIGINT        NULL,
  transaction_pk         BIGINT        NULL,
  gateway                VARCHAR(30)   NULL,
  order_amount           DECIMAL(12,2) NULL,
  transaction_amount     DECIMAL(12,2) NULL,
  difference             DECIMAL(12,2) NOT NULL DEFAULT 0,
  reconciliation_status  VARCHAR(20)   NOT NULL,
  reconciled_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recon_order FOREIGN KEY (order_pk)
    REFERENCES orders(id) ON DELETE SET NULL,
  CONSTRAINT fk_recon_txn FOREIGN KEY (transaction_pk)
    REFERENCES transactions(id) ON DELETE SET NULL,
  INDEX idx_recon_status (reconciliation_status),
  INDEX idx_recon_order_id (order_id),
  INDEX idx_recon_txn_id (transaction_id),
  INDEX idx_recon_gateway (gateway),
  INDEX idx_recon_date (reconciled_at)
) ENGINE=InnoDB;
