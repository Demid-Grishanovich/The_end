# 1. PROJECT OVERVIEW

DataCrowd Lab is a microservices-based, Human-in-the-Loop (HITL) dataset annotation platform that automates the full lifecycle of crowdsourced data labeling — from raw file upload through task distribution, quality-controlled review, and consensus verification, to structured dataset export. The system is designed for organizations that require scalable, auditable, and programmatically controllable annotation workflows without relying on unstructured coordination through third-party communication tools.

The platform decomposes the annotation process into atomic, stateful tasks that are distributed to Workers, reviewed by Reviewers, and owned by Clients — all within a strict role-isolation model enforced at the JWT token, API routing, and database levels. Quality is maintained through a dynamic Trust Score per Worker, honeypot task injection, minimum response time enforcement, and a configurable N-of-M consensus mechanism. The resulting verified annotations are exportable as structured JSONL or typed CSV files suitable for direct ingestion into machine learning training pipelines.

Table 1.1. Key Highlights

| Aspect | Description |
|---|---|
| Problem | Manual dataset annotation is slow, expensive, unauditable, and unprotected against low-quality contributors |
| Solution | An automated microservices platform that coordinates annotation tasks, enforces quality through consensus review, and provides full audit trails |
| Target Users | ML startups needing fast batch labeling; enterprises requiring secure in-house annotation; research labs requiring reproducible workflows |
| Key Features | State-machine task lifecycle; Trust Score anti-fraud subsystem; N-of-M consensus review; ML pre-annotation via HuggingFace; JSONL/CSV export; Stripe billing integration |
| Tech Stack | Java 17 + Spring Boot 3.4 (Auth, Core, Payments); Go 1.22 (Runner); PostgreSQL 16; MinIO; Docker Compose; GitHub Actions CI/CD |