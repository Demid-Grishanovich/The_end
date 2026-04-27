package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiConflictException;
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
    private final ObjectMapper objectMapper;

    public ExportService(ExportRepository exportRepository,
                         ProjectRepository projectRepository,
                         DatasetRepository datasetRepository,
                         AnswerRepository answerRepository,
                         StorageService storageService,
                         ObjectMapper objectMapper) {
        this.exportRepository   = exportRepository;
        this.projectRepository  = projectRepository;
        this.datasetRepository  = datasetRepository;
        this.answerRepository   = answerRepository;
        this.storageService     = storageService;
        this.objectMapper       = objectMapper;
    }

    @Transactional
    public ExportEntity buildExport(UUID ownerUserId,
                                    UUID projectId,
                                    UUID datasetId,
                                    String format) {

        // 1. Проверяем владельца проекта
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiNotFoundException("Project not found"));
        if (!project.getOwnerUserId().equals(ownerUserId)) {
            throw new ApiForbiddenException("Not your project");
        }

        // 2. Проверяем датасет
        DatasetEntity ds = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ApiNotFoundException("Dataset not found"));
        if (!ds.getProjectId().equals(projectId)) {
            throw new ApiConflictException("Dataset doesn't belong to project");
        }

        // 3. Нормализуем формат
        String fmt = (format == null || format.isBlank()) ? "jsonl" : format.trim().toLowerCase();
        if (!fmt.equals("jsonl") && !fmt.equals("csv")) {
            throw new ApiConflictException("Only format=jsonl and format=csv are supported");
        }

        // 4. Получаем тип задач проекта
        DataType dataType = project.getDataType() != null ? project.getDataType() : DataType.TEXT;

        // 5. Собираем одобренные ответы
        List<AnswerEntity> answers = answerRepository
                .findByProjectDatasetAndStatus(projectId, datasetId, AnswerStatus.APPROVED);

        // 6. Строим содержимое файла
        String fileContent;
        String fileName;

        if ("csv".equals(fmt)) {
            fileContent = buildCsv(answers, dataType);
            fileName    = "result.csv";
        } else {
            fileContent = buildJsonl(answers, dataType);
            fileName    = "result.jsonl";
        }

        // 7. Сохраняем файл на диск
        Path file = storageService.ensureExportFile(projectId, datasetId, fileName);
        try {
            Files.writeString(file, fileContent, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write export file: " + e.getMessage(), e);
        }

        // 8. Сохраняем запись в БД
        ExportEntity ex = new ExportEntity();
        ex.setId(UUID.randomUUID());
        ex.setProjectId(projectId);
        ex.setDatasetId(datasetId);
        ex.setFormat(fmt);
        ex.setStatus(ExportStatus.READY);
        ex.setFilePath(file.toString());

        return exportRepository.save(ex);
    }

    // -----------------------------------------------------------------------
    // JSONL: одна строка = один ответ, типизированный по dataType
    // -----------------------------------------------------------------------
    private String buildJsonl(List<AnswerEntity> answers, DataType dataType) {
        StringBuilder sb = new StringBuilder();
        for (AnswerEntity a : answers) {
            try {
                ObjectNode line = objectMapper.createObjectNode();
                line.put("taskId",   a.getTaskId().toString());
                line.put("workerId", a.getUserId().toString());
                line.put("dataType", dataType.name());

                // Исходный payload задачи
                if (a.getTask() != null && a.getTask().getPayloadJson() != null) {
                    try {
                        JsonNode payload = objectMapper.readTree(a.getTask().getPayloadJson());
                        line.set("input", payload);
                    } catch (Exception ignored) {
                        line.put("input", a.getTask().getPayloadJson());
                    }
                }

                // Ответ воркера
                try {
                    JsonNode answerNode = objectMapper.readTree(a.getContent());
                    line.set("answer", answerNode);
                } catch (Exception e) {
                    line.put("answer", a.getContent());
                }

                sb.append(objectMapper.writeValueAsString(line)).append("\n");
            } catch (Exception e) {
                // пропускаем битую запись, не ломаем весь экспорт
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // CSV: заголовки и колонки зависят от dataType
    // -----------------------------------------------------------------------
    private String buildCsv(List<AnswerEntity> answers, DataType dataType) {
        StringBuilder sb = new StringBuilder();

        // Заголовок зависит от типа задач
        String header = switch (dataType) {
            case TEXT  -> "taskId,workerId,text,label,confidence\n";
            case IMAGE -> "taskId,workerId,file,label,bbox\n";
            case AUDIO -> "taskId,workerId,file,transcript,language\n";
            case CODE  -> "taskId,workerId,language,hasBug,severity\n";
            case MATH  -> "taskId,workerId,problem,solution,isCorrect\n";
        };
        sb.append(header);

        for (AnswerEntity a : answers) {
            try {
                JsonNode payload = null;
                if (a.getTask() != null && a.getTask().getPayloadJson() != null) {
                    try { payload = objectMapper.readTree(a.getTask().getPayloadJson()); }
                    catch (Exception ignored) {}
                }

                JsonNode answer = null;
                try { answer = objectMapper.readTree(a.getContent()); }
                catch (Exception ignored) {}

                String row = switch (dataType) {
                    case TEXT -> buildCsvRow(
                            a.getTaskId().toString(),
                            a.getUserId().toString(),
                            getField(payload, "text"),
                            getField(answer,  "label"),
                            getField(answer,  "confidence")
                    );
                    case IMAGE -> buildCsvRow(
                            a.getTaskId().toString(),
                            a.getUserId().toString(),
                            getField(payload, "file", "assetRelPath"),
                            getField(answer,  "label"),
                            getField(answer,  "bbox")
                    );
                    case AUDIO -> buildCsvRow(
                            a.getTaskId().toString(),
                            a.getUserId().toString(),
                            getField(payload, "file", "assetRelPath"),
                            getField(answer,  "transcript"),
                            getField(answer,  "language")
                    );
                    case CODE -> buildCsvRow(
                            a.getTaskId().toString(),
                            a.getUserId().toString(),
                            getField(payload, "language"),
                            getField(answer,  "hasBug"),
                            getField(answer,  "severity")
                    );
                    case MATH -> buildCsvRow(
                            a.getTaskId().toString(),
                            a.getUserId().toString(),
                            getField(payload, "problem"),
                            getField(payload, "solution"),
                            getField(answer,  "isCorrect")
                    );
                };
                sb.append(row).append("\n");
            } catch (Exception e) {
                // пропускаем битую запись
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы
    // -----------------------------------------------------------------------

    /** Пробует несколько ключей, возвращает первый найденный */
    private String getField(JsonNode node, String... keys) {
        if (node == null) return "";
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull()) {
                return escapeCsv(v.asText());
            }
        }
        return "";
    }

    /** Оборачивает значение в кавычки если содержит запятую или кавычки */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Собирает одну CSV строку из колонок */
    private String buildCsvRow(String... columns) {
        return String.join(",", columns);
    }

    // -----------------------------------------------------------------------
    // Получить существующий экспорт (для скачивания)
    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ExportEntity getOwnedOrThrow(UUID ownerUserId, UUID exportId) {
        ExportEntity ex = exportRepository.findById(exportId)
                .orElseThrow(() -> new ApiNotFoundException("Export not found"));

        ProjectEntity project = projectRepository.findById(ex.getProjectId())
                .orElseThrow(() -> new ApiNotFoundException("Project not found"));

        if (!project.getOwnerUserId().equals(ownerUserId)) {
            throw new ApiForbiddenException("Not your export");
        }
        return ex;
    }
}