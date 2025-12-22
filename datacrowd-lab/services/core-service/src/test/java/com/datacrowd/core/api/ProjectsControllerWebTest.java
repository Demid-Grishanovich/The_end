package com.datacrowd.core.api;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.security.JwtPrincipal;
import com.datacrowd.core.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectsControllerWebTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    ProjectService projectService;

    UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        projectService = mock(ProjectService.class);

        ProjectsController controller = new ProjectsController(projectService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();

        userId = UUID.randomUUID();
        var principal = new JwtPrincipal(userId.toString(), "u", "CLIENT");
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_returns200_andProjectResponse() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("P1");
        req.setDescription("Desc");
        req.setDataType("TEXT");
        req.setReviewersCount(2);
        req.setRewardPoints(10);

        UUID projectId = UUID.randomUUID();
        ProjectEntity saved = new ProjectEntity();
        saved.setId(projectId);
        saved.setOwnerUserId(userId);
        saved.setName("P1");
        saved.setDescription("Desc");
        saved.setDataType("TEXT");
        saved.setStatus(ProjectStatus.NEW);
        saved.setReviewersCount(2);
        saved.setRewardPoints(10);
        saved.setBillingStatus("UNPAID");
        saved.setTaskQuota(0);

        when(projectService.create(eq(userId), any(CreateProjectRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/core/projects")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("P1"))
                .andExpect(jsonPath("$.billingStatus").value("UNPAID"))
                .andExpect(jsonPath("$.taskQuota").value(0));
    }

    @Test
    void create_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(post("/core/projects")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.properties.errors.name").exists());
    }

    @Test
    void my_returns200_list() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setOwnerUserId(userId);
        p.setName("Mine");
        p.setStatus(ProjectStatus.NEW);
        p.setBillingStatus("UNPAID");
        p.setTaskQuota(0);

        when(projectService.myProjects(userId)).thenReturn(List.of(p));

        mockMvc.perform(get("/core/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$[0].name").value("Mine"));
    }

    @Test
    void get_returns200_project() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setOwnerUserId(userId);
        p.setName("P");
        p.setStatus(ProjectStatus.NEW);
        p.setBillingStatus("UNPAID");
        p.setTaskQuota(0);

        when(projectService.getOwnedOrThrow(projectId, userId)).thenReturn(p);

        mockMvc.perform(get("/core/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("P"));
    }
}
