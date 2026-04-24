package com.datacrowd.core.api;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.dto.DataTypeGuideResponse;
import com.datacrowd.core.dto.ProjectResponse;
import com.datacrowd.core.entity.DataType;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.DataTypeFormatGuide;
import com.datacrowd.core.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Projects", description = "Client project management")
@RestController
@RequestMapping("/core/projects")
public class ProjectsController {

    private final ProjectService  projectService;
    private final TaskRepository  taskRepository;

    public ProjectsController(ProjectService projectService,
                              TaskRepository taskRepository) {
        this.projectService = projectService;
        this.taskRepository = taskRepository;
    }

    @Operation(
            summary     = "Create project",
            description = "Creates a new labeling project. dataType defines the format of uploaded dataset and worker task UI."
    )
    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.create(userId, req);
        return toResponse(p);
    }

    @Operation(
            summary     = "List my projects",
            description = "Returns paginated list of projects owned by the current user. " +
                    "Each project includes progress: completedTasks / totalTasks."
    )
    @GetMapping
    public Page<ProjectResponse> my(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId   = AuthContext.getUserIdOrThrow();
        var  pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return projectService.myProjects(userId, pageable).map(this::toResponse);
    }

    @Operation(
            summary     = "Get project by ID",
            description = "Returns a single project. Only the owner can access it."
    )
    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ProjectEntity p = projectService.getOwnedOrThrow(id, userId);
        return toResponse(p);
    }

    @Operation(
            summary     = "Get data type format guides",
            description = "Returns input and output format descriptions for each DataType " +
                    "(TEXT, IMAGE, AUDIO, CODE, MATH). " +
                    "Frontend uses this to show hints when creating a project."
    )
    @GetMapping("/data-type-guides")
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

        // Прогресс задач для прогресс-бара на фронте
        long total     = taskRepository.countAllByProject(p.getId());
        long completed = taskRepository.countApprovedByProject(p.getId());
        r.totalTasks     = total;
        r.completedTasks = completed;
        r.progress       = total > 0 ? (int) (completed * 100 / total) : 0;

        return r;
    }
}