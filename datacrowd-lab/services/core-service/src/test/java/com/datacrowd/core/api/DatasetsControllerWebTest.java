package com.datacrowd.core.api;

import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.security.JwtPrincipal;
import com.datacrowd.core.service.DatasetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DatasetsControllerWebTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    DatasetService datasetService;

    UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        datasetService = mock(DatasetService.class);

        DatasetsController controller = new DatasetsController(datasetService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                // важно: сначала String-конвертер, потом Jackson
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
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
    void upload_returns200_datasetResponse() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "a,b,c\n1,2,3\n".getBytes()
        );

        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);
        d.setStatus(DatasetStatus.UPLOADED);
        d.setSourcePath("s3://bucket/data.csv");
        d.setTotalItems(0);

        when(datasetService.upload(eq(projectId), eq(userId), any())).thenReturn(d);

        mockMvc.perform(multipart("/core/projects/{projectId}/datasets", projectId)
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(datasetId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.sourcePath").value("s3://bucket/data.csv"));
    }

    @Test
    void generate_returns200_ok() throws Exception {
        UUID datasetId = UUID.randomUUID();
        GenerateTasksRequest req = new GenerateTasksRequest();
        req.batchSize = 10;
        req.reviewersCount = 2;
        req.rewardPoints = 5;

        doNothing().when(datasetService).generateTasks(eq(datasetId), eq(userId), any(GenerateTasksRequest.class));

        mockMvc.perform(post("/core/datasets/{datasetId}/generate-tasks", datasetId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void generate_returns400_whenBodyMissing() throws Exception {
        UUID datasetId = UUID.randomUUID();

        mockMvc.perform(post("/core/datasets/{datasetId}/generate-tasks", datasetId)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed JSON"));
    }
}
