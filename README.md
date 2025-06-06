# Azki Reservation System

Enterprise-grade appointment reservation system built with Spring Boot.

## Features

### Core Functionality
- User authentication with secure JWT tokens
- Queue-based reservation processing for high concurrency support
- Automatic selection of nearest available time slot
- Reservation status tracking and notifications
- Reservation cancellation capability

### Performance & Scalability
- Redis-backed queue for high throughput reservation requests
- O(1) lookup optimization for email tracking using Redis Sets
- Caching of available time slots for improved performance
- Automatic TTL for Redis keys to prevent memory growth

### Security
- JWT-based authentication with configurable expiration
- Input validation for all API endpoints
- API rate limiting protection (configurable)
- Comprehensive security logging and audit trails

### Reliability
- Dead letter queue (DLQ) for failed reservation requests
- Optimistic locking for handling concurrent modifications
- Automatic retry with backoff for transient failures
- Configurable reservation expiration management

### Monitoring & Observability
- Detailed metrics for performance monitoring (Micrometer + Prometheus)
- Custom health indicators for system status monitoring
- Enhanced logging with color formatting for easier debugging
- Request tracing for all operations

### User Experience
- Improved API response structures with detailed information
- Consistent error messages with appropriate HTTP status codes
- Automatic waitlist capability for high-demand slots

## Requirements
- Java 21+
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+

## Configuration
Key application properties (configurable in `application.yml`):

```yaml
reservation:
  queue:
    batch-size: 50             # Number of requests processed per batch
    poll-interval-ms: 10       # Polling interval in milliseconds
  status:
    expiry-hours: 24           # How long to keep status keys in Redis
  rate-limiting:
    enabled: true              # Enable/disable API rate limiting
  expiry:
    hours: 24                  # Reservation expiration time
    check-minutes: 15          # How often to check for expired reservations
```

## Setup
1. Clone the repository:
   ```sh
   git clone <your-repo-url>
   cd reservation
   ```

2. Configure PostgreSQL and Redis:
   ```sh
   docker-compose up -d
   ```

3. Build the project:
   ```sh
   ./mvnw clean install
   ```

4. Run the application:
   ```sh
   ./mvnw spring-boot:run
   ```

## API Documentation
After starting the application, access the Swagger UI at:
```
http://localhost:8080/swagger-ui/index.html
```

## Testing with Postman
A Postman collection is included in the repository for easy API testing:
1. Import `Azki_Reservation_System.postman_collection.json` into Postman
2. Use the authentication flow to get a JWT token
3. Test reservation creation, status checking, and cancellation

## Architecture
The system follows a layered architecture with:

```
Controller → Service → Repository → Database
                ↓
              Redis
```

- **Controllers**: Handle HTTP requests/responses and input validation
- **Services**: Implement business logic and transaction management
- **Queue Service**: Manages asynchronous processing of reservation requests
- **Repositories**: Provide data access through JPA
- **Redis**: Used for caching, queueing, and distributed locks

## Key Components

### ReservationQueueService
Handles high-volume reservation requests through Redis-backed queues:
- Enqueues requests for asynchronous processing
- Dequeues and processes requests in batches
- Provides status tracking and idempotent processing
- Manages retries and dead letter queues

### ReservationService
Core business logic for reservations:
- Finds available time slots with optimistic locking
- Manages reservation creation and cancellation
- Handles conflicts and edge cases

### RedisCleanupService
Prevents Redis memory growth:
- Sets TTL on all Redis keys
- Performs scheduled cleanup of old keys

### RateLimitFilter & Configuration
Protects the API from abuse:
- Implements token bucket algorithm
- Configurable rate limits
- HTTP 429 responses when limits exceeded

## Project Structure
- `config/` - Application configuration classes
- `controller/` - REST controllers and response handling
- `dto/` - Data Transfer Objects for API requests/responses
- `entity/` - JPA entity classes
- `exception/` - Custom exception classes
- `filter/` - Web filters including rate limiting
- `repository/` - Spring Data repositories
- `security/` - JWT authentication and security config
- `service/` - Core business logic and services

## Monitoring
The application exposes metrics and health information through Spring Boot Actuator:
```
http://localhost:8080/actuator/health      # Health information
http://localhost:8080/actuator/metrics     # Available metrics
```

Key metrics:
- `reservation.queue.length` - Current queue size
- `reservation.dlq.length` - Dead letter queue size
- `reservation.queue.processed` - Successfully processed requests
- `reservation.queue.errors.*` - Various error counters

---

## License
Free To Use License (FTUL) - see LICENSE file for details.
## Contributors
- Hooman Yarahmadi (<knight.hooman@gmail.com>)
