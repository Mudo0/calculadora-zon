---
name: java-springboot
description: Comprehensive best practices for developing high-quality Spring Boot 3.x applications with production-ready patterns.
license: Apache-2.0
metadata:
  author: mateodellungo
  version: "2.0"
---

## When to Use

- Developing Spring Boot applications
- Need best practices for Spring Boot
- Configuring Spring Boot projects
- Working with Spring framework components

## Core Principles (TL;DR)
1. **Constructor Injection:** Always use it for testability. Never `@Autowired` on fields.
2. **DTOs:** Never expose JPA entities to the API layer. Use Java `records` or projection interfaces.
3. **Package by Feature:** Group by domain (e.g., `order/`, `user/`), not by layer.
4. **Validation:** Use Bean Validation (`@Valid`, `@NotBlank`) on request DTOs.
5. **Transactions:** Keep them short and at the service layer with `@Transactional`.
6. **Logging:** Use parameterized logging (`{}`), never string concatenation.
7. **Security:** Never hardcode secrets. Use env vars and `@ConfigurationProperties`.
8. **Testing:** Use the narrowest test slice that gives you confidence.
9. **Configuration:** Externalize everything via `application.yml`.
10. **Monitoring:** Add Actuator and health checks to every service.


## Reference Index

| Topic | Reference |
|---|---|
| Build tool, starters, package structure | [references/project-setup.md](references/project-setup.md) |
| Constructor injection, Lombok, stereotypes | [references/dependency-injection.md](references/dependency-injection.md) |
| application.yml, @ConfigurationProperties, profiles, secrets | [references/configuration.md](references/configuration.md) |
| REST controllers, DTOs, validation, exception handler | [references/web-layer.md](references/web-layer.md) |
| Business logic, @Transactional | [references/service-layer.md](references/service-layer.md) |
| Repositories, queries, projections | [references/data-layer.md](references/data-layer.md) |
| SLF4J logging patterns | [references/logging.md](references/logging.md) |
| Spring Security, JWT, password encoding | [references/security.md](references/security.md) |
| Async, caching, API versioning, pagination | [references/advanced.md](references/advanced.md) |
| Actuator, health indicators | [references/monitoring.md](references/monitoring.md) |
| Test slice quick reference | [references/testing.md](references/testing.md) |


## Project Setup & Structure
- **Build Tool:** Use Maven (`pom.xml`) or Gradle (`build.gradle`).
- **Starters:** Use standard starters (`spring-boot-starter-web`, `spring-boot-starter-webflux`, `spring-boot-starter-data-jpa`).
- **Development Tools:** Use Spring Boot DevTools for hot reload during local development.

## Web, Service and Data Layer
- **REST APIs:** Design clear RESTful endpoints using Spring MVC or WebFlux.
- **DTOs:** Use Java `records` to expose and consume data. 
- **Error Handling:** Implement a global exception handler using `@ControllerAdvice` and `@ExceptionHandler`.
- **Business Logic:** Encapsulate all stateless business logic within `@Service` classes.
- **Spring Data JPA:** Use repositories extending `JpaRepository`.
- **Advanced Data Patterns:** Implement pagination, sorting, and database auditing (`@CreatedDate`, `@LastModifiedDate`, `@EnableJpaAuditing`).
- **Projections:** Use DTO projections to fetch only the necessary data from the database.

## Dependency Injection & Components
- **Constructor Injection:** Declare dependency fields as `private final` and use constructor injection.
- **Component Stereotypes:** Use `@Component`, `@Service`, `@Repository`, and `@RestController` to define beans and manage their lifecycle.

## Testing
Write unit tests using JUnit 5 and Mockito. Use Testcontainers for reliable integration tests with real databases.

### Test Slice Quick Reference
| Test Type | Annotation | Use For |
|-----------|------------|---------|
| Unit | `@ExtendWith(MockitoExtension.class)` | Service logic |
| Web | `@WebMvcTest` | Controllers (Spring MVC) |
| Web Reactive | `@WebFluxTest` | Controllers (WebFlux) |
| Data | `@DataJpaTest` | Repositories |
| Integration | `@SpringBootTest` | Full context flow |
| Slice | `@RestClientTest` | REST clients |

## Security
- **Authentication & Tokens:** Configure OAuth2 and JWT for stateless authentication.
- **Authorization:** Implement method-level authorization (`@PreAuthorize`, `@Secured`).
- **Password Encoding:** Always use a strong hashing algorithm like BCrypt.
- **Protection:** Implement CORS configuration and CSRF protection. Prevent SQL injection and XSS.

## Configuration and Logging
- **Externalized Configuration:** Use `application.yml` and bind to strongly-typed Java objects via `@ConfigurationProperties`.
- **Profiles:** Use Spring Profiles (`application-dev.yml`, `application-prod.yml`).
- **SLF4J:** Use the SLF4J API. Declare as: `private static final Logger logger = LoggerFactory.getLogger(MyClass.class);`
- **Parameterized Logging:** Use `logger.info("Processing user {}...", userId);` instead of string concatenation.

## Critical Patterns & Extras

### Naming Conventions
- **PascalCase** for classes (`UserController`).
- **camelCase** for methods/variables (`findUserById`).
- **ALL_CAPS** for constants (`MAX_RETRY_ATTEMPTS`).

### Performance and Scalability
- Implement caching using Spring Cache abstraction.
- Use `@Async` or reactive programming (WebFlux) for non-blocking operations.

### Metrics and Monitoring
- Use Spring Boot Actuator for health indicators and custom endpoints.
- Integrate Micrometer metrics and Prometheus monitoring for production readiness.

### API Documentation
- Use Springdoc OpenAPI for API documentation.


## Context7 Integration (Docs Lookup)
When you need up-to-date information on Spring Boot:
1. **Resolve the libraryId** — Use `context7_resolve-library-id` with:
   - `libraryName`: "spring boot" or "spring framework"
   - `query`: what you're going to do.
2. **Query the docs** — Use `context7_query-docs` with:
   - `libraryId`: the ID from the previous step.
   - `query`: your specific question.
**Before answering** about APIs or configurations, consult Context7.

## Resources
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Framework Documentation](https://spring.io/projects/spring-framework)
- [Spring Security Documentation](https://spring.io/projects/spring-security)

## Commands
```bash
mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn clean package
mvn test
mvn test -Dtest=OrderServiceTest

```




