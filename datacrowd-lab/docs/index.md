EUROPEAN HUMANITIES UNIVERSITY
Academic Department of Informatics and Computer Science
Study program: Software Engineering and Distributed Systems

Demid Grishanovich
4th year student

DATACROWD LAB: HUMAN-IN-THE-LOOP DATASET PLATFORM
BACHELOR PROJECT

Supervisor: Oleksii Leunenko

Vilnius, 2024

---

# ABSTRACT

The present bachelor project addresses the problem of inefficient, unscalable, and unauditable manual dataset annotation in the context of modern machine learning pipelines. As the demand for high-quality labeled training data grows across industries, organizations continue to rely on ad-hoc coordination of human annotators through messaging tools and spreadsheets, resulting in poor quality control, data inconsistency, and an inability to reproduce labeling decisions.

The object of research is the end-to-end lifecycle of crowdsourced dataset annotation, from raw data ingestion to verified, exportable labeled datasets, within a multi-tenant distributed software system.

The goal of the project is to design, implement, and validate a microservices-based Human-in-the-Loop (HITL) dataset annotation platform — DataCrowd Lab — that automates the coordination of annotation tasks, enforces quality through a consensus review mechanism, and provides full auditability of every labeling decision.

The following tasks were defined to achieve the goal: analysis of existing annotation tools and identification of their architectural shortcomings; design of a microservices architecture decomposed into Auth, Core, Payments, and an asynchronous Go-based Runner service; implementation of a state-machine-driven task lifecycle with database-level concurrency controls; development of a role-based access control system covering Client, Worker, Reviewer, and Admin personas; integration of an anti-fraud subsystem based on dynamic Trust Scores, honeypot tasks, and bot detection via minimum response time; implementation of ML-assisted pre-annotation via the HuggingFace Inference API; and validation of the system through automated tests, load benchmarking, and a containerized demonstration environment.

The research methodology combines software architecture design patterns (microservices, event-driven state machines, dead letter queues), relational database modeling with PostgreSQL, test-driven development using JUnit 5 and Testcontainers, and DevOps practices including GitHub Actions CI/CD pipelines and Docker Compose orchestration.

The principal conclusions of the project are as follows. The implemented platform demonstrates that the full annotation lifecycle — upload, splitting, assignment, review, consensus, and export — can be automated without sacrificing quality control. The use of the PostgreSQL `FOR UPDATE SKIP LOCKED` primitive eliminates data race conditions under concurrent worker load. The Trust Score subsystem effectively detects and gates low-quality contributors. The system achieves a throughput of approximately 1000 requests per second on a single Core service instance with a synchronous API latency below 50 milliseconds. The architecture is horizontally scalable by design and is prepared for migration to Kubernetes orchestration.

*Keywords: Human-in-the-Loop, crowdsourced annotation, microservices, dataset labeling, trust score*

---

# АННОТАЦИЯ

Настоящий бакалаврский проект посвящён проблеме неэффективной, немасштабируемой и неаудируемой ручной разметки датасетов в контексте современных конвейеров машинного обучения. По мере роста спроса на качественные размеченные обучающие данные организации по-прежнему полагаются на нескоординированное взаимодействие разметчиков через мессенджеры и таблицы, что приводит к низкому качеству контроля, несогласованности данных и невозможности воспроизвести решения о разметке.

Объектом исследования является сквозной жизненный цикл краудсорсинговой разметки датасетов — от загрузки сырых данных до верифицированных, готовых к экспорту размеченных датасетов — в рамках мультитенантной распределённой программной системы.

Цель проекта — спроектировать, реализовать и валидировать основанную на микросервисной архитектуре платформу для разметки датасетов с участием человека (Human-in-the-Loop) — DataCrowd Lab — которая автоматизирует координацию задач разметки, обеспечивает качество через механизм консенсусной проверки и предоставляет полную аудируемость каждого решения о разметке.

Для достижения цели были определены следующие задачи: анализ существующих инструментов разметки и выявление их архитектурных недостатков; проектирование микросервисной архитектуры, декомпозированной на сервисы Auth, Core, Payments и асинхронный Go-Runner; реализация жизненного цикла задачи на основе машины состояний с управлением параллелизмом на уровне базы данных; разработка системы ролевого доступа для персон Client, Worker, Reviewer и Admin; интеграция антифрод-подсистемы на основе динамического Trust Score, задач-ловушек и обнаружения ботов по минимальному времени ответа; реализация ML-ассистированной предразметки через HuggingFace Inference API; валидация системы с помощью автоматических тестов, нагрузочного тестирования и контейнеризированной демонстрационной среды.

Методология исследования сочетает паттерны проектирования программной архитектуры (микросервисы, событийно-ориентированные машины состояний, очереди недоставленных сообщений), реляционное моделирование данных с PostgreSQL, разработку через тестирование с использованием JUnit 5 и Testcontainers, а также практики DevOps, включая CI/CD-конвейеры GitHub Actions и оркестрацию Docker Compose.

Основные выводы проекта состоят в следующем. Реализованная платформа демонстрирует, что полный жизненный цикл разметки — загрузка, разбиение, назначение, проверка, консенсус и экспорт — может быть автоматизирован без ущерба для контроля качества. Использование примитива `FOR UPDATE SKIP LOCKED` в PostgreSQL устраняет состояния гонки данных при параллельной нагрузке. Подсистема Trust Score эффективно обнаруживает и отсекает исполнителей с низким качеством работы. Система достигает пропускной способности около 1000 запросов в секунду на одном экземпляре сервиса Core при латентности синхронного API менее 50 миллисекунд. Архитектура горизонтально масштабируема по замыслу и подготовлена к миграции на оркестрацию Kubernetes.

*Ключевые слова: Human-in-the-Loop, краудсорсинговая разметка, микросервисы, разметка датасетов, рейтинг доверия*

---

# TABLE OF CONTENTS

Table of contents will be auto-generated.

---

# INTRODUCTION

The accelerating adoption of machine learning across industrial, scientific, and commercial domains has created an unprecedented demand for large-scale, high-quality labeled datasets. Contemporary supervised learning models — whether applied to natural language processing, computer vision, audio recognition, or code analysis — are fundamentally constrained not by algorithmic sophistication but by the volume and reliability of annotated training data available to them. This dependency has elevated dataset annotation from a supporting activity into a critical bottleneck of the AI development lifecycle.

Despite the strategic importance of annotation quality, the organizational practices surrounding it have not kept pace with the engineering sophistication of the models they serve. The dominant approach in small-to-medium organizations remains the manual coordination of human annotators through communication platforms and shared documents. This approach fails along three dimensions. First, it does not scale linearly: as annotation volume grows, coordination overhead grows superlinearly, driving up both cost and calendar time. Second, it lacks auditability: there is no canonical, queryable record of who labeled a given data point, when the decision was made, or whether two independent annotators agreed on the result. Third, it lacks quality enforcement: there is no systematic mechanism for detecting and removing low-effort or adversarial annotators before their submissions are incorporated into training data.

The goal of the present project is to address these three failure modes through the design and implementation of DataCrowd Lab — a microservices-based, Human-in-the-Loop dataset annotation platform. The platform automates the entire annotation lifecycle from raw data ingestion to verified, exportable labeled datasets, while providing strict role isolation, database-enforced state machine transitions, and a multi-layered anti-fraud subsystem.

The object of research is the software architecture and algorithmic mechanisms required to coordinate concurrent human annotators over large-scale datasets with correctness guarantees. The subject of research is the application of microservices architectural patterns, relational database concurrency primitives, and reputation-based quality control mechanisms to the crowdsourced annotation domain.

The significance of the work lies in the fact that, while commercial annotation platforms exist, their architectural principles are not publicly documented, and academic treatments of the domain focus predominantly on annotation interfaces rather than backend coordination infrastructure. DataCrowd Lab provides a fully open, rigorously designed reference implementation that demonstrates how state machines, transactional locking, and trust scoring can be composed into a production-grade annotation backend.

The project is structured as follows. Section 1 establishes the problem domain, stakeholder analysis, and project scope. Section 2 documents the technical architecture, design decisions, and implementation details across all seven evaluation criteria. Section 3 provides a user guide covering system operation for all roles. Section 4 presents a retrospective analysis of challenges, lessons learned, and directions for future development. Section 5 contains supporting appendices including the full API reference and database schema documentation.