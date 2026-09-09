# Scalable Event-Driven Notification Engine

A production-grade, asynchronous notification pipeline built with Spring Boot to ingest, route, and dispatch multi-channel communications like Email and SMS. It prevents blocked client threads by offloading slow third-party API calls to background workers, while guaranteeing reliable delivery through strict idempotency controls and fault-tolerant retry mechanisms.

## Features

* **Decoupled Messaging Infrastructure:** Employs CloudAMQP / RabbitMQ topic and fanout routing to broadcast business triggers across channel-specific queues without blocking the ingestion thread.
* **Distributed Idempotency:** Guarantees zero duplicate deliveries by enforcing atomic `SET NX EX` distributed locking in Upstash Redis (24-hour TTL) alongside database-level unique constraints.
* **Resilience & Dead-Letter Queues (DLQ):** Background consumer workers use manual acknowledgments (`ACK`/`NACK`), exponential backoff retry policies (2s, 4s, 8s), and automatic poison-pill rerouting into dedicated Dead-Letter Queues.
* **Dynamic Template Hydration:** Decouples content rendering by binding dynamic JSON event metadata into Thymeleaf HTML templates before external dispatch.
* **Full Audit & Lifecycle Tracking:** Logs state transitions (`PENDING` $\rightarrow$ `DELIVERED` / `FAILED`), retry counters, recipient metadata, and error stack traces in Supabase PostgreSQL.

## Tech Stack

* Java 21
* Spring Boot 3
* Spring AMQP / Spring Data JPA / Hibernate
* PostgreSQL (Supabase)
* Redis (Upstash)
* RabbitMQ (CloudAMQP)
* Twilio SDK / Resend API / Mailtrap
* Thymeleaf

* 
## Architecture / How It Works

The system leverages an asynchronous publish-subscribe pattern to decouple request ingestion from external message dispatching.

Client → REST API → Idempotency Check (Redis) → Database (Pending Log) → RabbitMQ Exchange
↓
Channel Specific Queues (`email_queue`, `sms_queue`)
↓
Background Workers (External APIs)

## Key Engineering Concepts

* Asynchronous processing
* Idempotency
* Caching
* Exception handling
* Database persistence
* Scalability
* Clean architecture


## API Endpoints

 Method | Endpoint | Description |

POST | `/api/v1/notifications` | Ingests a single-channel notification request and queues it for asynchronous delivery.

POST | `/api/v1/notifications/multi-channel` | Ingests multi-channel notification events and routes them dynamically based on enum configurations.

POST | `/api/v1/notifications/broadcast` | Broadcasts an event across all subscribed channels via a fanout exchange.


## Database

* **`notification_logs`**: Tracks the complete lifecycle of every notification attempt (`PENDING` $\rightarrow$ `DELIVERED`/`FAILED`), including idempotency keys, retry counts, JSON metadata, and error stack traces.

* **`notification_templates`**: Maps specific business event types to their corresponding Thymeleaf HTML template paths and subject lines.

## Setup & Installation

Ensure you have configured your environment variables for Supabase, Redis, CloudAMQP, Resend, and Twilio before running the application locally.

```bash
git clone <repository-url>
cd notification_system
mvn spring-boot:run

```
