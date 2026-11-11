
# LineLess

A smart queue and waitlist management system built using
Spring Boot Microservices, Apache Kafka, Redis, PostgreSQL,
and Next.js.

## Overview

LineLess helps businesses manage customer queues digitally.

Customers can join queues using a QR code or a link,
receive a digital token, and track their waiting position
in real time.

Administrators can manage queues, call customers, skip
customers, and complete service sessions.

## Main Features

### Customer Features
- Register and log in.
- Sign in using Google.
- Discover available queues.
- Join a queue.
- Receive a digital token.
- Track live queue position.
- Receive notifications when their turn approaches.

### Admin Features
- Create and manage queues.
- View waiting customers.
- Call the next customer.
- Skip customers.
- Complete customer sessions.
- View queue statistics.

## Technology Stack

### Frontend
- Next.js
- React.js
- TypeScript
- Tailwind CSS

### Backend
- Java 21
- Spring Boot
- Spring Cloud Gateway
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Spring for Apache Kafka

### Infrastructure
- PostgreSQL
- Redis
- Apache Kafka
- Docker
- AWS EC2

## Architecture

LineLess uses a microservices architecture.

The main services are:

1. Auth Service
   - User registration and authentication.
   - Google OAuth 2.0 / OpenID Connect.
   - JWT generation and refresh-token management.
   - Role-based authorization.

2. API Gateway
   - Single public entry point.
   - Request routing.
   - JWT validation.
   - CORS and request filtering.

3. Queue Service
   - Queue management.
   - Customer registration in queues.
   - Token allocation.
   - Redis-based live queue positions.
   - Kafka event publishing.

4. Notification Service
   - Kafka event consumption.
   - Customer email notifications.

## Project Structure

```text
lineless/
├── frontend/
├── backend/
│   ├── auth-service/
│   ├── api-gateway/
│   ├── queue-service/
│   └── notification-service/
├── infrastructure/
├── docs/
├── .github/
├── .gitignore
├── .dockerignore
├── .env.example
├── docker-compose.yml
└── docker-compose.prod.yml
```

## Getting Started

### Prerequisites

Install the following:

- Java 21
- Node.js
- npm
- Docker Desktop
- Git
- Maven (or use the Maven Wrapper)

### Clone the repository

```bash
git clone YOUR_REPOSITORY_URL
cd lineless
```

### Configure environment variables

Copy the example environment file:

```bash
cp .env.example .env
```

Update the values in `.env`.

### Start infrastructure and services

```bash
docker compose up -d --build
```

## Development

Each backend service can also be developed and run
independently using its own Spring Boot configuration.

The frontend communicates with the backend through
the API Gateway.

## Production Deployment

Production deployment uses the additional Compose
configuration:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d --build
```

## Security

- Passwords are stored as secure hashes.
- JWTs use asymmetric RSA signing.
- The private signing key is only available to Auth Service.
- Public keys are used by services to verify JWTs.
- Refresh tokens are stored securely as hashes.
- Admin privileges are assigned through controlled provisioning.
- Secrets must never be committed to Git.

## Project Status

Under active development.

## License

To be decided.
```

---

## 5. `docker-compose.yml`

This is the main Docker configuration for local development.

We will initially create the infrastructure containers. We can add the application services as their Dockerfiles become ready.

<CodeBlock language="yaml" editable>
name: lineless

services:

  # ==========================================
  # POSTGRESQL
  # ==========================================

  postgres:
    image: postgres:17
    container_name: lineless-postgres
    restart: unless-stopped

    environment:
      POSTGRES_USER: ${POSTGRES_USER:-lineless}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-devpassword}
      POSTGRES_DB: ${POSTGRES_DB:-lineless}

    ports:
      - "5432:5432"

    volumes:
      - postgres_data:/var/lib/postgresql/data

    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-lineless}"]
      interval: 10s
      timeout: 5s
      retries: 5

    networks:
      - lineless-network


  # ==========================================
  # REDIS
  # ==========================================

  redis:
    image: redis:7
    container_name: lineless-redis
    restart: unless-stopped

    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD:-devredispassword}

    ports:
      - "6379:6379"

    volumes:
      - redis_data:/data

    healthcheck:
      test:
        [
          "CMD",
          "redis-cli",
          "-a",
          "${REDIS_PASSWORD:-devredispassword}",
          "ping"
        ]
      interval: 10s
      timeout: 5s
      retries: 5

    networks:
      - lineless-network


  # ==========================================
  # KAFKA
  # ==========================================

  kafka:
    image: apache/kafka:4.1.0
    container_name: lineless-kafka
    restart: unless-stopped

    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller

      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093

      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT

      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1

      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      CLUSTER_ID: ${KAFKA_CLUSTER_ID:?Set KAFKA_CLUSTER_ID in .env}

    ports:
      - "9092:9092"

    volumes:
      - kafka_data:/var/lib/kafka/data

    networks:
      - lineless-network


# ==========================================
# VOLUMES
# ==========================================

volumes:
  postgres_data:
  redis_data:
  kafka_data:


# ==========================================
# NETWORKS
# ==========================================

networks:
  lineless-network:
    driver: bridge
