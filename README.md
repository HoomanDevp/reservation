# Reservation System

This is a Spring Boot-based reservation system.

## Features
- User authentication with JWT
- Reservation management
- API documentation (Swagger/OpenAPI)
- Exception handling

## Requirements
- Java 17+
- Maven
- (Optional) Docker for database

## Setup
1. Clone the repository:
   ```sh
   git clone <your-repo-url>
   cd reservation
   ```
2. Configure your database in `src/main/resources/application.yml`.
3. Build the project:
   ```sh
   ./mvnw clean install
   ```
4. Run the application:
   ```sh
   ./mvnw spring-boot:run
   ```

## API Documentation
After running, access Swagger UI at: `http://localhost:8080/swagger-ui.html`

## Testing
Run tests with:
```sh
./mvnw test
```

## Project Structure
- `config/` - Configuration classes
- `controller/` - REST controllers
- `dto/` - Data transfer objects
- `entity/` - JPA entities
- `repository/` - Spring Data repositories
- `security/` - Security filters and utilities
- `service/` - Business logic

---
