package com.datacrowd.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final Counter tasksSubmittedCounter;
    private final Counter tasksApprovedCounter;
    private final Counter tasksRejectedCounter;
    private final Counter honeypotFailedCounter;
    private final Counter botDetectedCounter;
    private final Counter projectsCreatedCounter;
    private final AtomicLong activeLocksGauge = new AtomicLong(0);

    public MetricsService(MeterRegistry registry) {

        this.tasksSubmittedCounter = Counter.builder("datacrowd_tasks_submitted_total")
                .description("Total tasks submitted by workers")
                .register(registry);

        this.tasksApprovedCounter = Counter.builder("datacrowd_tasks_approved_total")
                .description("Total tasks approved by reviewers")
                .register(registry);

        this.tasksRejectedCounter = Counter.builder("datacrowd_tasks_rejected_total")
                .description("Total tasks rejected by reviewers")
                .register(registry);

        this.honeypotFailedCounter = Counter.builder("datacrowd_honeypot_failed_total")
                .description("Total honeypot failures (workers who failed quality checks)")
                .register(registry);

        this.botDetectedCounter = Counter.builder("datacrowd_bot_detected_total")
                .description("Total suspicious bot-like activity detected via time-tracking")
                .register(registry);

        this.projectsCreatedCounter = Counter.builder("datacrowd_projects_created_total")
                .description("Total projects created by clients")
                .register(registry);

        Gauge.builder("datacrowd_tasks_active_locks", activeLocksGauge, AtomicLong::get)
                .description("Currently locked tasks (tasks in progress)")
                .register(registry);
    }

    public void incrementTasksSubmitted()  { tasksSubmittedCounter.increment(); }
    public void incrementTasksApproved()   { tasksApprovedCounter.increment(); }
    public void incrementTasksRejected()   { tasksRejectedCounter.increment(); }
    public void incrementHoneypotFailed()  { honeypotFailedCounter.increment(); }
    public void incrementBotDetected()     { botDetectedCounter.increment(); }
    public void incrementProjectsCreated() { projectsCreatedCounter.increment(); }
    public void incrementActiveLocks()     { activeLocksGauge.incrementAndGet(); }
    public void decrementActiveLocks()     { activeLocksGauge.decrementAndGet(); }
}