# Task 1 Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the FoodRush backend (Spring Boot) and frontend (React/Vite) projects and implement the full MySQL database schema, satisfying Task 1 in `.taskmaster/tasks/tasks.json`.

**Architecture:** Spring Boot 4.1.0 (Java 21, Maven wrapper) with a layered `controller → service → repository → entity` package structure, Flyway-managed MySQL schema, and JPA entities validated against that schema (`ddl-auto: validate`). React 18+ (Vite-generated, currently ships React 19) with `react-router-dom`, `axios`, and Tailwind CSS v4, using the folder layout specified in the PRD (`components`, `pages`, `services`, `contexts`, `utils`, `hooks`).

**Tech Stack:** Spring Boot 4.1.0, Spring Data JPA, Spring Security (classpath only — no config yet), Flyway, MySQL 8, jjwt 0.12.7, Lombok, Java 21 · React 19 (Vite), react-router-dom 7, axios 1, Tailwind CSS 4.

## Global Constraints

- **Spring Boot 4.1.0**, not 3.x — the PRD/tasks.json say "3.x", but Spring Initializr now rejects anything below 4.0.0 (3.x is past its support window as of 2026-07-30). Confirmed with the user: proceed on 4.1.0. Note this deviation in `CLAUDE.md` when done.
- **Java 21** (installed locally; Boot 4.1.0's Initializr default). No global Maven — every backend command runs through the generated `./mvnw` wrapper.
- **groupId `com.foodrush`, artifactId `backend`, base package `com.foodrush.backend`.**
- **jjwt 0.12.7** (task spec says "0.12.x"; 0.12.7 is the latest patch on that line — 0.13.0 exists but is a new minor line, skip it to stay within spec intent).
- **Tailwind CSS v4**, not the v3-style `postcss`+`autoprefixer`+`tailwind.config.js` setup the task text describes — v4's Vite plugin (`@tailwindcss/vite`) supersedes that toolchain. Functionally equivalent (custom breakpoints, utility classes all still work), just configured via `@theme` in CSS instead of a JS config file. Note this deviation in `CLAUDE.md` when done.
- **Cart single-restaurant rule (FR-14):** not enforced in this task (that's Task 5's business logic), but `Cart.restaurantId` must stay nullable in the schema so an empty cart has no restaurant yet — don't make it `NOT NULL`.
- **MySQL user separation:** the existing `foodrush_mcp` user (`database/setup_mcp_user.sql`) is `SELECT`-only, for the MCP server. The backend needs its own read/write user — this plan creates `foodrush_app` for that, kept distinct from the MCP credentials in `.env`.
- Table names `users` and `orders` (not `user`/`order`) — both are reserved words in MySQL 8.
- No dotenv library added to the backend. Local dev env vars (`DB_USERNAME`, `DB_PASSWORD`) are exported in the shell before running `./mvnw`, documented in `backend/README.md`. (The frontend's `.env.development`/`.env.production` are plain Vite env files per spec — those *are* auto-loaded by Vite.)

---

### Task 1: Provision the app-level MySQL user

**Files:**
- Create: `database/setup_app_user.sql`
- Modify: `.env` (add `DB_USERNAME`, `DB_PASSWORD`)
- Modify: `.env.example` (add placeholder keys)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a MySQL user `foodrush_app` with full DML+DDL privileges on the `foodrush` schema, and `DB_USERNAME`/`DB_PASSWORD` env var names that every later backend task's `application-*.yml` reads.

- [ ] **Step 1: Write the user-provisioning SQL script**

Create `database/setup_app_user.sql`:

```sql
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
```

- [ ] **Step 2: Run it as root (manual — needs credentials this session doesn't have)**

```bash
mysql -u root -p < database/setup_app_user.sql
```

This is a manual, one-time step for whoever holds the local MySQL root password (root login isn't available to the automated session — `mysql -u root` with no password was tried and rejected). If subsequent tasks are executed by a subagent, this step must be confirmed done by the user *before* dispatching Task 3 onward, since those tasks need a working DB connection to verify.

- [ ] **Step 3: Add the new credentials to the env files**

Edit `.env` (already gitignored), add two lines:

```
DB_USERNAME=foodrush_app
DB_PASSWORD=XLhZkNbQSKJ0TIeBpHj2
```

Edit `.env.example`, add two lines with placeholders (no real secret):

```
DB_USERNAME=foodrush_app
DB_PASSWORD=your_password_here
```

- [ ] **Step 4: Verify the user works**

```bash
export PATH="$PATH:/c/Program Files/MySQL/MySQL Server 8.0/bin"
mysql -u foodrush_app -p'XLhZkNbQSKJ0TIeBpHj2' -h 127.0.0.1 -e "SELECT 1;" foodrush
```

Expected: prints a `1` row, no access-denied error.

- [ ] **Step 5: Commit**

```bash
git add database/setup_app_user.sql .env.example
git commit -m "chore: add app-level MySQL user provisioning script"
```

(`.env` itself is gitignored — don't add it.)

---

### Task 2: Scaffold the Spring Boot backend project

**Files:**
- Create: `backend/` (entire generated Maven project — `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `src/main/java/com/foodrush/backend/BackendApplication.java`, `src/test/...`)
- Modify: `backend/pom.xml` (add jjwt dependencies)
- Create: `backend/src/main/java/com/foodrush/backend/{controller,service,repository,dto,config,security,exception,entity}/package-info.java`
- Modify: `backend/README.md` (replace 3.x stub wording)

**Interfaces:**
- Consumes: nothing from Task 1 directly (this is pure scaffolding, no DB touched yet).
- Produces: a compilable Maven project at `backend/`, base package `com.foodrush.backend`, with all 8 layered packages present for later tasks to add classes into.

- [ ] **Step 1: Generate the project via Spring Initializr**

Run from the FoodRush repo root:

```bash
curl -sG "https://start.spring.io/starter.zip" \
  --data-urlencode "type=maven-project" \
  --data-urlencode "language=java" \
  --data-urlencode "bootVersion=4.1.0" \
  --data-urlencode "baseDir=backend" \
  --data-urlencode "groupId=com.foodrush" \
  --data-urlencode "artifactId=backend" \
  --data-urlencode "name=backend" \
  --data-urlencode "description=FoodRush backend API" \
  --data-urlencode "packageName=com.foodrush.backend" \
  --data-urlencode "packaging=jar" \
  --data-urlencode "javaVersion=21" \
  --data-urlencode "dependencies=web,data-jpa,security,mysql,validation,lombok,flyway" \
  -o backend.zip
unzip -o backend.zip -d .
rm backend.zip
```

This merges the generated files into the existing `backend/` directory (which currently only has `README.md` — no name collision). Confirmed working: this exact command was dry-run during planning and produced a valid 29-file Maven project with `mvnw`/`mvnw.cmd`, `pom.xml`, `BackendApplication.java`, and an empty `src/main/resources/db/migration/` folder (Flyway's default location — Task 4 will drop the migration file there).

- [ ] **Step 2: Verify the wrapper works**

```bash
cd backend
chmod +x mvnw
./mvnw -v
```

Expected: prints `Apache Maven 3.9.x`, `Java version: 21.0.12`. (Confirmed working during planning — the wrapper downloads its own Maven distribution on first run, no system Maven needed.)

- [ ] **Step 3: Add jjwt dependencies**

In `backend/pom.xml`, add a `jjwt.version` property. Find:

```xml
	<properties>
		<java.version>21</java.version>
	</properties>
```

Replace with:

```xml
	<properties>
		<java.version>21</java.version>
		<jjwt.version>0.12.7</jjwt.version>
	</properties>
```

Then find the `mysql-connector-j` dependency block:

```xml
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<scope>runtime</scope>
		</dependency>
```

Add immediately after it:

```xml
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-api</artifactId>
			<version>${jjwt.version}</version>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-impl</artifactId>
			<version>${jjwt.version}</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-jackson</artifactId>
			<version>${jjwt.version}</version>
			<scope>runtime</scope>
		</dependency>
```

- [ ] **Step 4: Create the layered package structure**

Create each of these files (one-line real Javadoc each — these are structural packages later tasks populate, not stub code):

`backend/src/main/java/com/foodrush/backend/controller/package-info.java`:
```java
/**
 * REST controllers exposing FoodRush's HTTP API.
 */
package com.foodrush.backend.controller;
```

`backend/src/main/java/com/foodrush/backend/service/package-info.java`:
```java
/**
 * Business logic and transactional orchestration.
 */
package com.foodrush.backend.service;
```

`backend/src/main/java/com/foodrush/backend/repository/package-info.java`:
```java
/**
 * Spring Data JPA repositories for persistence access.
 */
package com.foodrush.backend.repository;
```

`backend/src/main/java/com/foodrush/backend/dto/package-info.java`:
```java
/**
 * Request/response payloads exchanged with clients.
 */
package com.foodrush.backend.dto;
```

`backend/src/main/java/com/foodrush/backend/config/package-info.java`:
```java
/**
 * Spring configuration beans (security, CORS, etc.).
 */
package com.foodrush.backend.config;
```

`backend/src/main/java/com/foodrush/backend/security/package-info.java`:
```java
/**
 * JWT authentication and Spring Security wiring.
 */
package com.foodrush.backend.security;
```

`backend/src/main/java/com/foodrush/backend/exception/package-info.java`:
```java
/**
 * Custom exceptions and centralized error handling.
 */
package com.foodrush.backend.exception;
```

`backend/src/main/java/com/foodrush/backend/entity/package-info.java`:
```java
/**
 * JPA entities mapping to the FoodRush MySQL schema.
 */
package com.foodrush.backend.entity;
```

- [ ] **Step 5: Verify it compiles**

```bash
cd backend
./mvnw compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Update the backend README stub**

Replace `backend/README.md` (currently says "Spring Boot 3.x") with:

```markdown
# FoodRush Backend

Spring Boot 4.1.0 API (Java 21) — MySQL, Spring Security + JWT, layered package
structure (controller, service, repository, entity, dto, config, security, exception).

## Local development

Requires the `foodrush_app` MySQL user (see `../database/setup_app_user.sql`) and
`DB_USERNAME`/`DB_PASSWORD` exported in the shell (values in `../.env`):

    export DB_USERNAME=foodrush_app DB_PASSWORD=<see ../.env>
    ./mvnw spring-boot:run

## Build

    ./mvnw clean package
```

- [ ] **Step 7: Commit**

```bash
git add backend/
git commit -m "feat: scaffold Spring Boot 4.1.0 backend project"
```

---

### Task 3: Configure Spring profiles and datasource wiring

**Files:**
- Delete: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/application-prod.yml`

**Interfaces:**
- Consumes: `DB_USERNAME`/`DB_PASSWORD` env vars from Task 1; the `foodrush_app` MySQL user must exist (Task 1 Step 2 must have been run manually).
- Produces: a booting Spring context connected to local MySQL, `dev` as the active profile by default. Later tasks (Flyway migration in Task 4, JPA entities in Task 5) rely on `spring.jpa.hibernate.ddl-auto: validate` already being set here.

- [ ] **Step 1: Replace the default properties file with YAML**

```bash
rm backend/src/main/resources/application.properties
```

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```

- [ ] **Step 2: Add the dev profile**

Create `backend/src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:foodrush}?useSSL=false&serverTimezone=UTC
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
```

- [ ] **Step 3: Add the prod profile**

Create `backend/src/main/resources/application-prod.yml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

(`DATABASE_URL` matches the env var name `CLAUDE.md` already documents for the Render deployment in Task 17 — kept consistent so that task doesn't need renaming.)

- [ ] **Step 4: Verify the app boots and connects to MySQL**

Requires Task 1 Step 2 (the `foodrush_app` user) to already exist.

```bash
cd backend
export DB_USERNAME=foodrush_app
export DB_PASSWORD='XLhZkNbQSKJ0TIeBpHj2'
nohup ./mvnw spring-boot:run > /tmp/backend-boot.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started BackendApplication" /tmp/backend-boot.log && break
  grep -q "APPLICATION FAILED TO START" /tmp/backend-boot.log && break
  sleep 2
done
tail -50 /tmp/backend-boot.log
kill $BOOT_PID
```

Expected: log contains `Started BackendApplication`, no `APPLICATION FAILED TO START`. (With zero `@Entity` classes and zero migrations at this point, `ddl-auto: validate` and Flyway both trivially succeed — this step is really testing that the datasource URL/credentials are correct.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/
git commit -m "feat: configure dev/prod Spring profiles and MySQL datasource"
```

---

### Task 4: Write the Flyway schema migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__initial_schema.sql`

**Interfaces:**
- Consumes: the datasource config from Task 3 (needs a booting app to actually apply the migration).
- Produces: all 9 tables in the `foodrush` MySQL schema. Task 5's JPA entities must map onto exactly these table/column names — see the mapping table in Task 5's description.

- [ ] **Step 1: Write the migration SQL**

Create `backend/src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(15),
    role VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    address VARCHAR(255) NOT NULL,
    cuisine_type VARCHAR(50),
    rating DECIMAL(2,1),
    image_url VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_restaurants_name ON restaurants (name);

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE food_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(500),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_food_items_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_food_items_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    restaurant_id BIGINT,
    CONSTRAINT uq_carts_user_id UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_carts_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    food_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_food_item FOREIGN KEY (food_item_id) REFERENCES food_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    label VARCHAR(50),
    full_address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    pincode VARCHAR(6) NOT NULL,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    food_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price_at_order DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_food_item FOREIGN KEY (food_item_id) REFERENCES food_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

(No explicit `CREATE INDEX` on plain foreign-key columns: InnoDB auto-creates an index for every FK constraint. `idx_restaurants_name` is added explicitly because `name` isn't a FK but is queried via `findByNameContainingIgnoreCase` in Task 3 of the product plan. `users.email` and `categories.name` get their index for free from the `UNIQUE` constraint.)

- [ ] **Step 2: Apply the migration by booting the app**

```bash
cd backend
export DB_USERNAME=foodrush_app
export DB_PASSWORD='XLhZkNbQSKJ0TIeBpHj2'
nohup ./mvnw spring-boot:run > /tmp/backend-boot.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started BackendApplication" /tmp/backend-boot.log && break
  grep -q "APPLICATION FAILED TO START" /tmp/backend-boot.log && break
  sleep 2
done
tail -50 /tmp/backend-boot.log
kill $BOOT_PID
```

Expected: log shows Flyway applying `V1__initial_schema.sql`, then `Started BackendApplication`.

- [ ] **Step 3: Verify all 9 tables exist**

```bash
export PATH="$PATH:/c/Program Files/MySQL/MySQL Server 8.0/bin"
mysql -u foodrush_app -p'XLhZkNbQSKJ0TIeBpHj2' -h 127.0.0.1 foodrush -e "SHOW TABLES;"
```

Expected: `addresses, cart_items, carts, categories, flyway_schema_history, food_items, order_items, orders, restaurants, users` (10 rows — Flyway's own history table plus the 9 domain tables).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/
git commit -m "feat: add Flyway migration for initial database schema"
```

---

### Task 5: Add JPA entities and verify schema mapping

**Files:**
- Create: `backend/src/main/java/com/foodrush/backend/entity/Role.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/OrderStatus.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/User.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/Restaurant.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/Category.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/FoodItem.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/Cart.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/CartItem.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/Order.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/OrderItem.java`
- Create: `backend/src/main/java/com/foodrush/backend/entity/Address.java`

**Interfaces:**
- Consumes: the schema from Task 4 (`ddl-auto: validate` in Task 3's `application-dev.yml` checks every field below against those exact tables/columns).
- Produces: entity classes that Task 2 of the *product* plan (`.taskmaster` Task 2, auth) and Task 3 (restaurant/category APIs) will inject repositories for. Field names/types below are the contract those tasks build on — e.g. `User.role` is the `Role` enum, `Order.status` is `OrderStatus`, `FoodItem.isAvailable` is a primitive `boolean`.

- [ ] **Step 1: Create the enums**

`backend/src/main/java/com/foodrush/backend/entity/Role.java`:
```java
package com.foodrush.backend.entity;

public enum Role {
    USER, ADMIN
}
```

`backend/src/main/java/com/foodrush/backend/entity/OrderStatus.java`:
```java
package com.foodrush.backend.entity;

public enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
}
```

- [ ] **Step 2: Create `User`**

`backend/src/main/java/com/foodrush/backend/entity/User.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create `Restaurant`**

`backend/src/main/java/com/foodrush/backend/entity/Restaurant.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "cuisine_type", length = 50)
    private String cuisineType;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create `Category`**

`backend/src/main/java/com/foodrush/backend/entity/Category.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
```

- [ ] **Step 5: Create `FoodItem`**

`backend/src/main/java/com/foodrush/backend/entity/FoodItem.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "food_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean isAvailable = true;
}
```

- [ ] **Step 6: Create `Cart` and `CartItem`**

`backend/src/main/java/com/foodrush/backend/entity/Cart.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
}
```

`backend/src/main/java/com/foodrush/backend/entity/CartItem.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(nullable = false)
    private Integer quantity;
}
```

- [ ] **Step 7: Create `Address`**

`backend/src/main/java/com/foodrush/backend/entity/Address.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String label;

    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 6)
    private String pincode;
}
```

- [ ] **Step 8: Create `Order` and `OrderItem`**

`backend/src/main/java/com/foodrush/backend/entity/Order.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
```

`backend/src/main/java/com/foodrush/backend/entity/OrderItem.java`:
```java
package com.foodrush.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;
}
```

- [ ] **Step 9: Verify entities validate against the schema**

```bash
cd backend
export DB_USERNAME=foodrush_app
export DB_PASSWORD='XLhZkNbQSKJ0TIeBpHj2'
nohup ./mvnw spring-boot:run > /tmp/backend-boot.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 60); do
  grep -q "Started BackendApplication" /tmp/backend-boot.log && break
  grep -q "APPLICATION FAILED TO START" /tmp/backend-boot.log && break
  sleep 2
done
tail -80 /tmp/backend-boot.log
kill $BOOT_PID
```

Expected: `Started BackendApplication`, no `SchemaManagementException` (that's the error Hibernate throws when `ddl-auto: validate` finds a mismatch between an entity and its table).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/foodrush/backend/entity/
git commit -m "feat: add JPA entities for all 9 domain tables"
```

---

### Task 6: Scaffold the Vite + React frontend project

**Files:**
- Create: `frontend/` (entire generated Vite project — `package.json`, `vite.config.js`, `index.html`, `src/`, `public/`)
- Modify: `frontend/README.md`

**Interfaces:**
- Consumes: nothing (independent of the backend).
- Produces: a buildable Vite React project at `frontend/`, ready for Task 7 to add Tailwind and the PRD folder structure.

- [ ] **Step 1: Scaffold with create-vite**

Run from the FoodRush repo root:

```bash
npx create-vite@latest frontend --template react --no-interactive --overwrite
```

`--overwrite` removes the existing one-line `frontend/README.md` stub (it gets replaced with a real one in Step 3) — confirmed during planning that without `--overwrite`, create-vite just cancels silently when the target directory isn't empty.

- [ ] **Step 2: Install dependencies**

```bash
cd frontend
npm install
npm install react-router-dom axios
```

- [ ] **Step 3: Verify it builds**

```bash
cd frontend
npm run build
```

Expected: `vite v8.x building for production...` then a `dist/` output with no errors.

- [ ] **Step 4: Replace the generated README**

Vite's generated `frontend/README.md` is generic boilerplate. Replace it with:

```markdown
# FoodRush Frontend

React 18+ (Vite) — react-router-dom, axios, Tailwind CSS v4.
Folder layout: `src/components`, `src/pages`, `src/services`, `src/contexts`,
`src/utils`, `src/hooks`.

## Local development

    npm install
    npm run dev

## Build

    npm run build
```

- [ ] **Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold Vite + React frontend project"
```

---

### Task 7: Configure Tailwind CSS v4 and the PRD folder structure

**Files:**
- Modify: `frontend/vite.config.js`
- Modify: `frontend/src/index.css`
- Create: `frontend/src/components/.gitkeep`
- Create: `frontend/src/pages/.gitkeep`
- Create: `frontend/src/contexts/.gitkeep`
- Create: `frontend/src/utils/.gitkeep`
- Create: `frontend/src/hooks/.gitkeep`

**Interfaces:**
- Consumes: the project from Task 6.
- Produces: Tailwind utility classes available app-wide, plus `mobile:`/`tablet:`/`desktop:` breakpoint variants (360px/768px/1440px) that Task 10 onward (restaurant browsing, cart, admin panel — all responsive-layout tasks) will use directly.

- [ ] **Step 1: Install Tailwind v4's Vite plugin**

```bash
cd frontend
npm install tailwindcss @tailwindcss/vite
```

- [ ] **Step 2: Wire the plugin into Vite**

Replace `frontend/vite.config.js`:

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
})
```

- [ ] **Step 3: Import Tailwind and declare the custom breakpoints**

Replace the contents of `frontend/src/index.css` with:

```css
@import "tailwindcss";

@theme {
  --breakpoint-mobile: 360px;
  --breakpoint-tablet: 768px;
  --breakpoint-desktop: 1440px;
}
```

(This adds `mobile:`, `tablet:`, `desktop:` variants alongside Tailwind's default `sm`/`md`/`lg`/`xl`/`2xl` scale — it doesn't replace them.)

- [ ] **Step 4: Create the remaining PRD folders**

```bash
cd frontend/src
mkdir -p components pages contexts utils hooks
touch components/.gitkeep pages/.gitkeep contexts/.gitkeep utils/.gitkeep hooks/.gitkeep
```

(`services/` isn't created here — Task 8 populates it directly with `api.js`, so the folder appears naturally.)

- [ ] **Step 5: Verify Tailwind compiles**

```bash
cd frontend
npm run build
```

Expected: build succeeds; check `dist/assets/*.css` contains compiled Tailwind output (e.g. `grep -l "tailwind" dist/assets/*.css` or just confirm the CSS file size is non-trivial, not empty).

- [ ] **Step 6: Commit**

```bash
git add frontend/vite.config.js frontend/src/index.css frontend/src/components/.gitkeep frontend/src/pages/.gitkeep frontend/src/contexts/.gitkeep frontend/src/utils/.gitkeep frontend/src/hooks/.gitkeep
git commit -m "feat: configure Tailwind CSS v4 with custom breakpoints"
```

---

### Task 8: Axios client, env files, and final integration check

**Files:**
- Create: `frontend/src/services/api.js`
- Create: `frontend/.env.development`
- Create: `frontend/.env.production`
- Modify: `CLAUDE.md` (replace "planned architecture" section with verified specifics)
- Modify: `.taskmaster/tasks/tasks.json` (via `task-master` CLI, not hand-edited)

**Interfaces:**
- Consumes: everything from Tasks 2–7.
- Produces: `api.js` default-exports a configured axios instance — `import api from '../services/api'` — that Task 13 (auth pages) and every later data-fetching frontend task import directly. `VITE_API_BASE_URL` is the env var name those same tasks read indirectly through `api.js`.

- [ ] **Step 1: Create the axios instance**

`frontend/src/services/api.js`:
```js
import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

export default api;
```

- [ ] **Step 2: Create the env files**

`frontend/.env.development`:
```
VITE_API_BASE_URL=http://localhost:8080/api
```

`frontend/.env.production`:
```
VITE_API_BASE_URL=https://REPLACE_WITH_RENDER_URL/api
```

(The production URL is a real unknown until Task 17 deploys the backend to Render — `REPLACE_WITH_RENDER_URL` is an intentional marker to update then, not a scope gap in this task.)

- [ ] **Step 3: Boot both servers together and sanity-check**

```bash
cd backend
export DB_USERNAME=foodrush_app
export DB_PASSWORD='XLhZkNbQSKJ0TIeBpHj2'
nohup ./mvnw spring-boot:run > /tmp/backend-boot.log 2>&1 &
BACKEND_PID=$!

cd ../frontend
nohup npm run dev > /tmp/frontend-dev.log 2>&1 &
FRONTEND_PID=$!

sleep 15
echo "--- backend ---"; tail -20 /tmp/backend-boot.log
echo "--- frontend ---"; tail -20 /tmp/frontend-dev.log

kill $BACKEND_PID $FRONTEND_PID
```

Expected: backend log shows `Started BackendApplication`; frontend log shows a `Local: http://localhost:5173/` line with no compile errors.

- [ ] **Step 4: Update `CLAUDE.md` with verified specifics**

In `CLAUDE.md`, replace the "Project status" paragraph (currently says pre-implementation, stub READMEs) and the "Planned architecture" section's backend/frontend version numbers with the real ones: Spring Boot 4.1.0 (not 3.x — note *why*: 3.x is past Initializr's support window as of 2026-07-30), Java 21, `com.foodrush.backend` base package; Tailwind CSS v4 via `@tailwindcss/vite` (not the v3 postcss/autoprefixer/tailwind.config.js setup the PRD describes) with breakpoints declared via `@theme` in `src/index.css`. Keep the rest of the file (Task Master workflow, MCP DB access notes, cart single-restaurant rule, non-goals) as-is — still accurate.

- [ ] **Step 5: Mark Task 1 done in Task Master**

```bash
cd "c:/Users/Admin/Desktop/personal project/FoodRush"
./node_modules/.bin/task-master set-status --id=1 --status=done
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/services/api.js frontend/.env.development frontend/.env.production CLAUDE.md
git commit -m "feat: wire frontend axios client to backend API"
git add .taskmaster/tasks/tasks.json
git commit -m "chore: mark Task 1 done in Task Master"
```
