# Getting Started

This guide explains how to run the system locally and make your first API calls.

## Prerequisites

- Docker and Docker Compose
- (Optional) Postman or curl

## Run locally

1. Copy environment variables:
   `.env.example` → `.env`

2. Start the system:
   docker compose up --build

3. Verify health:
- Backend services: GET /actuator/health
- Runner: GET /healthz

## First API call

### Register
POST /auth/register

Request body:
{
"email": "user@example.com",
"username": "demo_user",
"password": "StrongPassword123!"
}

### Login
POST /auth/login

Response contains a JWT token.

### Authorized request
Add header:
Authorization: Bearer <JWT>

Example:
GET /projects
