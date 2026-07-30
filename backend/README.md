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
