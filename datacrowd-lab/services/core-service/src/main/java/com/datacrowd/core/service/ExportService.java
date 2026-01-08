package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ExportRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ExportService {

    private final ExportRepository exportRepository;
    private final ProjectRepository projectRepository;
    private final DatasetRepository datasetRepository;
    private final AnswerRepository answerRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExportService(
            ExportRepository exportRepository,
            ProjectRepository projectRepository,
            DatasetRepository datasetRepository,
            AnswerRepository answerRepository,
            StorageService storageService
    ) {
        this.exportRepository = exportRepository;
        this.projectRepository = projectRepository;
        this.datasetRepository = datasetRepository;
        this.answerRepository = answerRepository;
        this.storageService = storageService;
    }

    @Transactional
    public ExportEntity buildExport(UUID ownerUserId, UUID projectId, UUID datasetId, String format) {
        // 1) проверяем, что проект принадлежит пользователю
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiNotFoundException("Project not found"));
        if (!project.getOwnerUserId().equals(ownerUserId)) {
            // у тебя есть ApiForbiddenException, но чтобы не тащить — можно так:
            throw new com.datacrowd.core.api.ApiForbiddenException("Not your project");
        }

        // 2) проверяем, что dataset существует и принадлежит проекту
        DatasetEntity ds = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ApiNotFoundException("Dataset not found"));
        if (!ds.getProjectId().equals(projectId)) {
            throw new com.datacrowd.core.api.ApiConflictException("Dataset doesn't belong to project");
        }

        String fmt = (format == null || format.isBlank()) ? "jsonl" : format.trim().toLowerCase();
        if (!fmt.equals("jsonl")) {
            // для диплома достаточно jsonl, чтобы не раздувать
            throw new com.datacrowd.core.api.ApiConflictException("Only format=jsonl supported");
        }

        // 3) собираем approved answers
        List<AnswerEntity> answers = answerRepository.findByProjectDatasetAndStatus(projectId, datasetId, AnswerStatus.APPROVED);

        // 4) создаём файл
        Path file = storageService.ensureExportFile(projectId, datasetId, "result.jsonl");

        try {
            StringBuilder sb = new StringBuilder();
            for (AnswerEntity a : answers) {
                ObjectNode line = objectMapper.createObjectNode();
                line.put("answerId", a.getId().toString());
                line.put("taskId", a.getTaskId().toString());
                line.put("userId", a.getUserId().toString());

                // content может быть JSON-строкой — пробуем распарсить красиво
                JsonNode contentNode;
                try {
                    contentNode = objectMapper.readTree(a.getContent());
                } catch (Exception ex) {
                    contentNode = objectMapper.getNodeFactory().textNode(a.getContent());
                }
                line.set("content", contentNode);

                sb.append(objectMapper.writeValueAsString(line)).append("\n");
            }

            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build export: " + e.getMessage(), e);
        }

        // 5) пишем запись в БД
        ExportEntity ex = new ExportEntity();
        ex.setId(UUID.randomUUID());
        ex.setProjectId(projectId);
        ex.setDatasetId(datasetId);
        ex.setFormat(fmt);
        ex.setStatus(ExportStatus.READY);
        ex.setFilePath(file.toString());

        return exportRepository.save(ex);
    }

    @Transactional(readOnly = true)
    public ExportEntity getOwnedOrThrow(UUID ownerUserId, UUID exportId) {
        ExportEntity ex = exportRepository.findById(exportId)
                .orElseThrow(() -> new ApiNotFoundException("Export not found"));

        ProjectEntity project = projectRepository.findById(ex.getProjectId())
                .orElseThrow(() -> new ApiNotFoundException("Project not found"));

        if (!project.getOwnerUserId().equals(ownerUserId)) {
            throw new com.datacrowd.core.api.ApiForbiddenException("Not your export");
        }
        return ex;
    }
}
