package com.datacrowd.core.api;

import com.datacrowd.core.dto.WorkerStatsResponse;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.WorkerStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/core/workers")
public class WorkerStatsController {

    private final WorkerStatsService workerStatsService;

    public WorkerStatsController(WorkerStatsService workerStatsService) {
        this.workerStatsService = workerStatsService;
    }

    @GetMapping("/me/stats")
    public WorkerStatsResponse myStats() {
        UUID workerId = AuthContext.getUserIdOrThrow();
        return workerStatsService.getStats(workerId);
    }
}