package com.datacrowd.core.api;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.dto.ProjectResponse;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/core/projects")
public class ProjectsController {

    private final ProjectService projectService;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.create(userId, req);
        return toResponse(p);
    }

    @GetMapping
    public List<ProjectResponse> my() {
        UUID userId = AuthContext.getUserIdOrThrow();
        return projectService.myProjects(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.getOwnedOrThrow(id, userId);
        return toResponse(p);
    }

    private ProjectResponse toResponse(ProjectEntity p) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId();
        r.ownerUserId = p.getOwnerUserId();
        r.name = p.getName();
        r.description = p.getDescription();
        r.dataType = p.getDataType();
        r.status = p.getStatus();
        r.reviewersCount = p.getReviewersCount();
        r.rewardPoints = p.getRewardPoints();
        r.billingStatus = p.getBillingStatus();
        r.taskQuota = p.getTaskQuota();
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }
}
