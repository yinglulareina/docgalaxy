# Microservices Architecture

Microservices decompose an application into small, independently deployable services, each responsible for a single business capability. This contrasts with the monolith, where all functionality runs in a single process.

## Benefits

**Independent deployment** – teams ship their service without coordinating with others. A bug fix in the payment service doesn't require redeploying the entire application.

**Technology diversity** – each service chooses the language and data store best suited to its task. The recommendation engine can use Python and a graph database while the authentication service uses Go and PostgreSQL.

**Scalability** – services experiencing high load are scaled independently. No need to replicate the entire monolith to handle a spike in one feature.

## Costs

**Distributed systems complexity** – network calls fail; services must handle timeouts, retries, and partial failures. Distributed transactions are much harder than local ones.

**Operational overhead** – each service needs its own CI/CD pipeline, monitoring, and configuration. The infrastructure burden grows with the number of services.

**Data management** – each service owns its data, making cross-service queries difficult. Eventual consistency replaces ACID guarantees.

## When to Adopt

Start with a monolith. Microservices make sense when a team is large enough that coordination overhead dominates, or when independent scaling or deployment is a genuine business need – not before.
