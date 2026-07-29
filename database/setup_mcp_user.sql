-- FoodRush: create the project database and a dedicated, scoped MySQL user for the Claude Code MCP server.
-- Run this as root: mysql -u root -p < setup_mcp_user.sql

CREATE DATABASE IF NOT EXISTS foodrush
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'foodrush_mcp'@'localhost' IDENTIFIED BY 'ZOTBApsRVEkb0Ua4KIlG';
CREATE USER IF NOT EXISTS 'foodrush_mcp'@'127.0.0.1' IDENTIFIED BY 'ZOTBApsRVEkb0Ua4KIlG';

GRANT SELECT ON foodrush.* TO 'foodrush_mcp'@'localhost';
GRANT SELECT ON foodrush.* TO 'foodrush_mcp'@'127.0.0.1';

FLUSH PRIVILEGES;
