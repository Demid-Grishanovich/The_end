package com.datacrowd.core.api;

import com.datacrowd.core.config.CacheConfig;
import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.dto.DataTypeGuideResponse;
import com.datacrowd.core.dto.ProjectResponse;
import com.datacrowd.core.entity.BillingStatus;
import com.datacrowd.core.entity.DataType;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.DataTypeFormatGuide;
import com.datacrowd.core.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Projects", description = "Client project management")
@RestController
@RequestMapping("/core/projects")
public class ProjectsController {

    private final ProjectService    projectService;
    private final TaskRepository    taskRepository;
    private final ProjectRepository projectRepository;

    public ProjectsController(ProjectService projectService,
                              TaskRepository taskRepository,
                              ProjectRepository projectRepository) {
        this.projectService    = projectService;
        this.taskRepository    = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Operation(summary = "Create project")
    @PostMapping
    @CacheEvict(value = CacheConfig.MY_PROJECTS, allEntries = true)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.create(userId, req);
        return toResponse(p);
    }

    @Operation(summary = "List my projects")
    @GetMapping
    public Page<ProjectResponse> my(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId   = AuthContext.getUserIdOrThrow();
        var  pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return projectService.myProjects(userId, pageable).map(this::toResponse);
    }

    @Operation(summary = "Get project by ID")
    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.getOwnedOrThrow(id, userId);
        return toResponse(p);
    }

    @Operation(summary = "Get data type format guides")
    @GetMapping("/data-type-guides")
    @Cacheable(value = CacheConfig.DATA_TYPE_GUIDES, key = "'all'")
    public Map<String, DataTypeGuideResponse> dataTypeGuides() {
        Map<String, DataTypeGuideResponse> result = new LinkedHashMap<>();
        for (DataType type : DataType.values()) {
            result.put(type.name(), new DataTypeGuideResponse(
                    DataTypeFormatGuide.getDescription(type),
                    DataTypeFormatGuide.getInputFormat(type),
                    DataTypeFormatGuide.getOutputFormat(type)
            ));
        }
        return result;
    }

    @Operation(summary = "List projects available for workers")
    @GetMapping("/available")
    @Cacheable(value = CacheConfig.AVAILABLE_PROJECTS, key = "'all'")
    public List<ProjectResponse> availableForWorkers() {
        return projectRepository.findAll().stream()
                .filter(p -> p.getBillingStatus() == BillingStatus.PAID)
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED)
                .map(p -> {
                    long available = taskRepository.countAvailableByProject(p.getId());
                    if (available == 0) return null;
                    ProjectResponse r = toResponse(p);
                    r.availableTasks = available;
                    return r;
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get project public info (for workers)")
    @GetMapping("/{id}/public")
    @Cacheable(value = CacheConfig.PROJECT_PUBLIC, key = "#id")
    public ProjectResponse getPublic(@PathVariable UUID id) {
        ProjectEntity p = projectRepository.findById(id)
                .orElseThrow(() -> new ApiNotFoundException("Project not found: " + id));
        return toResponse(p);
    }

    private ProjectResponse toResponse(ProjectEntity p) {
        ProjectResponse r = new ProjectResponse();
        r.id             = p.getId();
        r.ownerUserId    = p.getOwnerUserId();
        r.name           = p.getName();
        r.description    = p.getDescription();
        r.dataType       = p.getDataType();
        r.status         = p.getStatus();
        r.reviewersCount = p.getReviewersCount();
        r.rewardPoints   = p.getRewardPoints();
        r.billingStatus  = p.getBillingStatus();
        r.taskQuota      = p.getTaskQuota();
        r.createdAt      = p.getCreatedAt();
        r.updatedAt      = p.getUpdatedAt();

        long total     = taskRepository.countAllByProject(p.getId());
        long completed = taskRepository.countApprovedByProject(p.getId());
        r.totalTasks     = total;
        r.completedTasks = completed;
        r.progress       = total > 0 ? (int) (completed * 100 / total) : 0;

        return r;
    }
}