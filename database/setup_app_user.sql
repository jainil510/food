-- FoodRush: create a dedicated, write-capable MySQL user for the Spring Boot
-- backend (separate from the read-only foodrush_mcp user used by the MCP
-- server — see setup_mcp_user.sql). Run as root: mysql -u root -p < database/setup_app_user.sql

CREATE DATABASE IF NOT EXISTS foodrush
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'foodrush_app'@'localhost' IDENTIFIED BY 'XLhZkNbQSKJ0TIeBpHj2';
CREATE USER IF NOT EXISTS 'foodrush_app'@'127.0.0.1' IDENTIFIED BY 'XLhZkNbQSKJ0TIeBpHj2';

GRANT ALL PRIVILEGES ON foodrush.* TO 'foodrush_app'@'localhost';
GRANT ALL PRIVILEGES ON foodrush.* TO 'foodrush_app'@'127.0.0.1';

FLUSH PRIVILEGES;
