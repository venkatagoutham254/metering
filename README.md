# Metering Service

## Overview

The Metering Service counts billable events from the `ingestion_event` table (managed by the newingestion service) and calculates costs based on pricing configurations from the Product Rate Plan service.

## Important: Data Model Change

**The `ingestion_event` table no longer has a `quantity` field**. Each row represents one billable event with `status='SUCCESS'`. The metering service **COUNTS events** instead of summing quantity values.

## Architecture

- **Framework**: Spring Boot 3.3.4
- **Language**: Java 21  
- **Database**: PostgreSQL (read-only access to newingestion database)
- **Security**: OAuth2 Resource Server with JWT authentication
- **Multi-tenancy**: Organization-based isolation using JWT claims or X-Organization-Id header
- **Scheduling**: Automatic hourly metering processing
- **Async Processing**: Supports asynchronous metering after ingestion

## Key Features

- **Event Counting**: Counts billable events from `ingestion_event` where `status='SUCCESS'`
- **Cost Calculation**: Calculate costs using the same pricing logic as the Revenue Estimator:
  - Flat Fee with overage
  - Usage-Based pricing  
  - Tiered pricing
  - Volume-Based pricing
  - Stair-Step pricing
- **Automatic Processing**: 
  - Scheduled hourly processing of new events
  - Webhook endpoint for post-ingestion triggering
  - Batch processing for multiple rate plans

## API Endpoints

### Calculate Metering
`POST /api/meter`

Calculate cost for a specific time range and filters.

Request body:
```json
{
  "from": "2025-10-01T00:00:00Z",
  "to": "2025-11-01T00:00:00Z",
  "ratePlanId": 777,
  "subscriptionId": 9101,
  "productId": 42,
  "billableMetricId": 5555
}
```

### Trigger Metering (Called by Ingestion Service)
`POST /api/meter/trigger`

Triggers async metering calculation after ingestion completes.

Request body:
```json
{
  "subscriptionId": 9101,
  "ratePlanId": 777,
  "from": "2025-10-01T00:00:00Z",
  "to": "2025-11-01T00:00:00Z"
}
```

### Batch Processing
`POST /api/meter/batch`

Process metering for multiple rate plans.

Request body:
```json
{
  "ratePlanIds": [777, 888, 999],
  "from": "2025-10-01T00:00:00Z",
  "to": "2025-11-01T00:00:00Z"
}
```

## Configuration

The service is configured through `application.yml`:

- **Server Port**: 8092
- **Database**: Connects to the newingestion PostgreSQL database
- **External Services**:
  - Billable Metrics Service: http://18.182.19.181:8081
  - Product Rate Plan Service: http://54.238.204.246:8080
  - Subscription Service: http://localhost:8083
  - Customer Service: http://44.203.171.98:8082

## Running the Service

### Prerequisites

1. Java 21
2. Maven 3.6+
3. Access to the newingestion PostgreSQL database
4. Network access to external services

### Build and Run

```bash
# Build the application
mvn clean package

# Run the application
java -jar target/metering.jar

# Or run with Maven
mvn spring-boot:run
```

### Environment Variables

- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL (default: jdbc:postgresql://localhost:5436/data_ingestion_db)
- `SPRING_DATASOURCE_USERNAME`: Database username (default: postgres)
- `SPRING_DATASOURCE_PASSWORD`: Database password (default: postgres)
- `JWT_SECRET`: Secret key for JWT validation

## Security

All API endpoints require JWT authentication. The JWT must contain an organization ID claim (one of: organizationId, orgId, tenantId, organization_id, org_id, tenant).

For testing, you can also use the `X-Organization-Id` header to specify the organization.

## Swagger Documentation

When the service is running, access the API documentation at:
- Swagger UI: http://localhost:8092/swagger-ui.html
- OpenAPI JSON: http://localhost:8092/v3/api-docs

## Architecture Highlights

### Pricing Engine
The service contains a pricing engine that mirrors the logic from the Revenue Estimator service, ensuring consistent cost calculations across the platform.

### Tenant Isolation
All queries are automatically scoped to the current organization ID extracted from the JWT token, ensuring data isolation in a multi-tenant environment.

### External Service Integration
The service integrates with other microservices to fetch:
- Billable metric definitions
- Rate plan configurations
- Product and subscription details

## Development

### Project Structure

```
src/main/java/aforo/metering/
├── client/           # External service clients
├── config/           # Spring configuration
├── controller/       # REST controllers
├── dto/              # Data Transfer Objects
├── pricing/          # Pricing engine logic
├── repository/       # Data access layer
├── security/         # Security filters
├── service/          # Business logic
└── tenant/           # Multi-tenancy support
```

### Testing

```bash
# Run unit tests
mvn test

# Run with test profile
mvn spring-boot:run -Dspring.profiles.active=test
```

## Monitoring

The service exposes health endpoints:
- `/actuator/health` - Health status

## License

Apache 2.0
