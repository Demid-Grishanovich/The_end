package com.datacrowd.core.api;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.dto.ProjectResponse;
import com.datacrowd.core.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/core/projects")
public class ProjectsController {

    private final ProjectService projectService;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping
    public List<ProjectResponse> listMine() {
        return projectService.listMine();
    }
}
