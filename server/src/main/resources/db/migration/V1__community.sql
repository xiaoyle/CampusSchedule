CREATE TABLE users (
  id CHAR(36) PRIMARY KEY,
  username VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(254) NULL UNIQUE,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  nickname VARCHAR(40) NOT NULL,
  role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  status ENUM('ACTIVE','BANNED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  token_epoch INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE invite_codes (
  code VARCHAR(32) PRIMARY KEY,
  max_uses INT NOT NULL,
  used_count INT NOT NULL DEFAULT 0,
  granted_role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  expires_at TIMESTAMP(3) NULL,
  created_by CHAR(36) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_invite_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE posts (
  id CHAR(36) PRIMARY KEY,
  author_id CHAR(36) NOT NULL,
  title VARCHAR(80) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  format ENUM('PLAIN','MARKDOWN') NOT NULL DEFAULT 'PLAIN',
  tags_json VARCHAR(600) NOT NULL DEFAULT '[]',
  version INT NOT NULL DEFAULT 1,
  hidden BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users(id),
  INDEX idx_posts_feed(hidden, deleted_at, created_at),
  FULLTEXT INDEX ft_posts(title, body)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE comments (
  id CHAR(36) PRIMARY KEY,
  post_id CHAR(36) NOT NULL,
  author_id CHAR(36) NOT NULL,
  body VARCHAR(500) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts(id),
  CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
  INDEX idx_comments_post(post_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE favorites (
  user_id CHAR(36) NOT NULL,
  post_id CHAR(36) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(user_id, post_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_favorite_post FOREIGN KEY (post_id) REFERENCES posts(id)
);

CREATE TABLE reports (
  id CHAR(36) PRIMARY KEY,
  reporter_id CHAR(36) NOT NULL,
  target_type ENUM('POST','COMMENT') NOT NULL,
  target_id CHAR(36) NOT NULL,
  reason VARCHAR(300) NOT NULL,
  status ENUM('OPEN','RESOLVED','DISMISSED') NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_by CHAR(36) NULL,
  resolved_at TIMESTAMP(3) NULL,
  CONSTRAINT fk_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
  INDEX idx_reports_status(status, created_at)
);

CREATE TABLE refresh_tokens (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  family_id CHAR(36) NOT NULL,
  secret_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  revoked_at TIMESTAMP(3) NULL,
  replaced_by CHAR(36) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_refresh_user(user_id), INDEX idx_refresh_family(family_id)
);

CREATE TABLE moderation_audit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id CHAR(36) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  detail VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_audit_created(created_at)
);
