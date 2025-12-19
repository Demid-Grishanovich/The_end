# Overview

DataCrowd Lab is a crowdsourcing platform for dataset creation and labeling
using human-in-the-loop workflows.

The system provides a REST API used by frontend applications and internal
services.

## User roles

- WORKER — performs labeling tasks, submits answers, may review others’ work
- CLIENT — creates projects and datasets, pays for task generation, exports results
- ADMIN — manages users and roles

## High-level architecture

The backend is split into several services:

- Auth Service  
  Handles user registration, login and JWT issuance.

- Core Service  
  Main business logic: projects, datasets, tasks, answers, reviews, exports.

- Payments Service  
  Integrates with Stripe, creates checkout sessions and processes webhooks.

- Runner (Go service)  
  Performs background jobs such as splitting datasets into task batches.

## Public vs Internal API

- Public API  
  Used by frontend clients. Protected with JWT authentication.

- Internal API  
  Used for service-to-service communication.
  Protected by a shared `X-Internal-Token` header.
