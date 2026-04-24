package main

import (
	"bufio"
	"context"
	"crypto/subtle"
	"encoding/csv"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Port          string
	CoreInternal  string
	InternalToken string
	DataDir       string

	// Operational limits (sane defaults)
	DefaultBatchSize int
	MaxBatchSize     int
	MaxTasksPerBulk  int
	BatchBulkSize    int

	HTTPTimeout time.Duration
	RetryCount  int
	RetryDelay  time.Duration
}

func loadConfig() Config {
	port := getenv("RUNNER_PORT", "8090")
	coreInternal := getenv("CORE_INTERNAL_URL", "http://core-service:8082")
	internalToken := getenv("INTERNAL_TOKEN", "change-me")
	dataDir := getenv("DATA_DIR", "/data")

	defaultBatchSize := getenvInt("DEFAULT_BATCH_SIZE", 100)
	maxBatchSize := getenvInt("MAX_BATCH_SIZE", 1000)
	maxTasksPerBulk := getenvInt("MAX_TASKS_PER_BULK", 2000)
	batchBulkSize := getenvInt("BATCH_BULK_SIZE", 25)

	httpTimeoutSec := getenvInt("HTTP_TIMEOUT_SECONDS", 20)
	retryCount := getenvInt("RETRY_COUNT", 3)
	retryDelayMs := getenvInt("RETRY_DELAY_MS", 450)

	return Config{
		Port:             port,
		CoreInternal:     strings.TrimRight(coreInternal, "/"),
		InternalToken:    internalToken,
		DataDir:          dataDir,
		DefaultBatchSize: defaultBatchSize,
		MaxBatchSize:     maxBatchSize,
		MaxTasksPerBulk:  maxTasksPerBulk,
		BatchBulkSize:    batchBulkSize,
		HTTPTimeout:      time.Duration(httpTimeoutSec) * time.Second,
		RetryCount:       retryCount,
		RetryDelay:       time.Duration(retryDelayMs) * time.Millisecond,
	}
}

func main() {
	cfg := loadConfig()

	if strings.TrimSpace(cfg.InternalToken) == "" {
		log.Fatal("INTERNAL_TOKEN is required")
	}

	client := &http.Client{Timeout: cfg.HTTPTimeout}
	svc := &Service{cfg: cfg, http: client}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status":"UP"}`))
	})

	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = w.Write([]byte("runner-service"))
	})

	mux.HandleFunc("/api/v1/runner/datasets/", svc.handleGenerateTasks) // we parse the rest manually

	addr := ":" + cfg.Port
	log.Printf("runner listening on %s", addr)
	if err := http.ListenAndServe(addr, withInternalToken(cfg.InternalToken, mux)); err != nil {
		log.Fatal(err)
	}
}

type Service struct {
	cfg  Config
	http *http.Client
}

type GenerateTasksRequest struct {
	DatasetId      string `json:"datasetId"`
	SourcePath     string `json:"sourcePath"`
	SourceType     string `json:"sourceType,omitempty"`
	ManifestPath   string `json:"manifestPath,omitempty"`
	BatchSize      int    `json:"batchSize"`
	ReviewersCount int    `json:"reviewersCount"`
	RewardPoints   int    `json:"rewardPoints"`
}

type coreCreateBatchesBulkRequest struct {
	Batches []coreBatchItem `json:"batches"`
}
type coreBatchItem struct {
	DatasetId  string `json:"datasetId"`
	Status     string `json:"status"`
	TotalTasks int    `json:"totalTasks"`
}
type coreCreateBatchesBulkResponse struct {
	BatchIds []string `json:"batchIds"`
}

type coreCreateTasksBulkRequest struct {
	Tasks []coreTaskItem `json:"tasks"`
}
type coreTaskItem struct {
	BatchId     string `json:"batchId"`
	PayloadJson string `json:"payloadJson"`
	Status      string `json:"status"`
}
type failedItemRequest struct {
	LineNumber int    `json:"lineNumber"`
	RawContent string `json:"rawContent"`
	ErrorMsg   string `json:"errorMsg"`
}

// HuggingFace Pre-annotation структуры
type hfRequest struct {
	Inputs string `json:"inputs"`
}

type hfLabel struct {
	Label string  `json:"label"`
	Score float64 `json:"score"`
}

// callHuggingFace вызывает бесплатный Inference API HuggingFace для текстовой классификации.
// Модель: cardiffnlp/twitter-roberta-base-sentiment-latest
// Возвращает (label, confidence, error)
// Если API недоступен — возвращает ошибку, задача создаётся как обычно без AI аннотации.
func callHuggingFace(text string) (string, float64, error) {
	if strings.TrimSpace(text) == "" {
		return "", 0, fmt.Errorf("empty text")
	}

	apiURL := "https://api-inference.huggingface.co/models/cardiffnlp/twitter-roberta-base-sentiment-latest"

	bodyBytes, _ := json.Marshal(hfRequest{Inputs: text})

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Post(apiURL, "application/json", strings.NewReader(string(bodyBytes)))
	if err != nil {
		return "", 0, fmt.Errorf("huggingface request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", 0, fmt.Errorf("huggingface returned status %d", resp.StatusCode)
	}

	// HuggingFace возвращает [[{label, score}, ...]]
	var result [][]hfLabel
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", 0, fmt.Errorf("huggingface response decode error: %w", err)
	}

	if len(result) == 0 || len(result[0]) == 0 {
		return "", 0, fmt.Errorf("empty huggingface response")
	}

	// Находим метку с максимальным score
	best := result[0][0]
	for _, r := range result[0] {
		if r.Score > best.Score {
			best = r
		}
	}

	return strings.ToLower(best.Label), best.Score, nil
}

// reportFailedItem отправляет битую строку в Dead Letter Queue.
// Не останавливает обработку если DLQ недоступен.
func (s *Service) reportFailedItem(ctx context.Context, datasetID string, lineNo int, raw, errMsg string) {
	req := failedItemRequest{
		LineNumber: lineNo,
		RawContent: raw,
		ErrorMsg:   errMsg,
	}
	path := fmt.Sprintf("/internal/datasets/%s/failed-items", datasetID)
	if err := s.corePOST(ctx, path, req, nil); err != nil {
		log.Printf("[DLQ] Failed to report failed item (line %d): %v", lineNo, err)
	}
}
func (s *Service) handleGenerateTasks(w http.ResponseWriter, r *http.Request) {
	// POST /api/v1/runner/datasets/{datasetId}/generate-tasks
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]any{"error": "Method not allowed"})
		return
	}

	// path parsing
	path := strings.TrimPrefix(r.URL.Path, "/api/v1/runner/datasets/")
	parts := strings.Split(strings.Trim(path, "/"), "/")
	if len(parts) != 2 || parts[1] != "generate-tasks" {
		writeJSON(w, http.StatusNotFound, map[string]any{"error": "Not found"})
		return
	}
	datasetId := parts[0]
	if datasetId == "" {
		writeJSON(w, http.StatusBadRequest, map[string]any{"error": "datasetId is required"})
		return
	}

	var req GenerateTasksRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"error": "invalid JSON body"})
		return
	}

	// basic validation / normalization
	if req.DatasetId != "" && req.DatasetId != datasetId {
		writeJSON(w, http.StatusBadRequest, map[string]any{"error": "datasetId in body doesn't match path"})
		return
	}
	req.DatasetId = datasetId

	if strings.TrimSpace(req.SourcePath) == "" {
		writeJSON(w, http.StatusBadRequest, map[string]any{"error": "sourcePath is required"})
		return
	}

	batchSize := req.BatchSize
	if batchSize <= 0 {
		batchSize = s.cfg.DefaultBatchSize
	}
	if batchSize > s.cfg.MaxBatchSize {
		batchSize = s.cfg.MaxBatchSize
	}
	req.BatchSize = batchSize

	// Run generation asynchronously so core-service call returns fast (no long HTTP timeouts).
	go func(copyReq GenerateTasksRequest) {
		jobCtx, cancel := context.WithTimeout(context.Background(), 30*time.Minute)
		defer cancel()

		if err := s.generateTasks(jobCtx, copyReq); err != nil {
			log.Printf("generateTasks failed datasetId=%s err=%v", copyReq.DatasetId, err)
			_ = s.patchDatasetStatus(context.Background(), copyReq.DatasetId, "FAILED")
		}
	}(req)

	writeJSON(w, http.StatusAccepted, map[string]any{"status": "accepted", "datasetId": datasetId})
}

func (s *Service) generateTasks(ctx context.Context, req GenerateTasksRequest) error {
	absPath, err := s.resolveSourcePath(req.SourcePath)
	if err != nil {
		return err
	}

	// mark dataset GENERATING is already done by core, but safe to keep idempotent.
	_ = s.patchDatasetStatus(ctx, req.DatasetId, "GENERATING")

	if strings.EqualFold(strings.TrimSpace(req.SourceType), "ZIP_MANIFEST") || strings.TrimSpace(req.ManifestPath) != "" {
		mp := strings.TrimSpace(req.ManifestPath)
		if mp == "" {
			return fmt.Errorf("manifestPath is required for ZIP_MANIFEST datasets")
		}
		manifestAbs, err := s.resolvePathWithinDataDir(mp)
		if err != nil {
			return err
		}
		return s.generateFromManifestJSONL(ctx, req.DatasetId, manifestAbs, req.BatchSize)
	}

	ext := strings.ToLower(filepath.Ext(absPath))
	switch ext {
	case ".csv":
		return s.generateFromCSV(ctx, req.DatasetId, absPath, req.BatchSize)
	case ".jsonl", ".ndjson":
		return s.generateFromJSONL(ctx, req.DatasetId, absPath, req.BatchSize)
	default:
		return fmt.Errorf("unsupported dataset format: %s (supported: .csv, .jsonl)", ext)
	}
}

func (s *Service) resolvePathWithinDataDir(path string) (string, error) {
	p := strings.TrimSpace(path)
	if p == "" {
		return "", fmt.Errorf("path is required")
	}
	abs, err := filepath.Abs(filepath.Clean(p))
	if err != nil {
		return "", fmt.Errorf("failed to resolve path: %w", err)
	}

	dataAbs, err := filepath.Abs(filepath.Clean(s.cfg.DataDir))
	if err != nil {
		return "", fmt.Errorf("failed to resolve DATA_DIR: %w", err)
	}

	// Security: ensure file is within DATA_DIR
	rel, err := filepath.Rel(dataAbs, abs)
	if err != nil || strings.HasPrefix(rel, "..") {
		return "", fmt.Errorf("path must be inside DATA_DIR (%s)", dataAbs)
	}

	return abs, nil
}

func (s *Service) resolveSourcePath(sourcePath string) (string, error) {
	abs, err := s.resolvePathWithinDataDir(sourcePath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve sourcePath: %w", err)
	}
	return abs, nil
}

func (s *Service) generateFromJSONL(ctx context.Context, datasetId, absPath string, batchSize int) error {
	f, err := os.Open(absPath)
	if err != nil {
		return err
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	// allow long JSON lines (up to ~4MB)
	buf := make([]byte, 0, 1024*1024)
	scanner.Buffer(buf, 4*1024*1024)

	total := 0
	group := newBatchGroup(s.cfg.BatchBulkSize, s.cfg.MaxTasksPerBulk)

	lineNo := 0
	for scanner.Scan() {
		lineNo++
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		payload := map[string]any{
			"filePath":   absPath,
			"format":     "jsonl",
			"lineNumber": lineNo,
		}
		payloadBytes, _ := json.Marshal(payload)

		group.addTask(preTask{payloadJson: string(payloadBytes), status: "NEW"})
		total++

		if group.shouldFlush(batchSize) {
			_ = group.hasAny()
			if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
				return err
			}
			group.reset()
		}
	}
	if err := scanner.Err(); err != nil {
		return fmt.Errorf("jsonl scan error: %w", err)
	}

	// flush remainder
	if group.hasAny() {
		if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
			return err
		}
	}

	// update dataset stats + READY
	_ = s.patchDatasetTotalItems(ctx, datasetId, total)
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

func (s *Service) generateFromManifestJSONL(ctx context.Context, datasetId, manifestAbsPath string, batchSize int) error {
	f, err := os.Open(manifestAbsPath)
	if err != nil {
		return fmt.Errorf("failed to open manifest: %w", err)
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	// allow long JSON lines (up to ~8MB) because meta may contain text, etc.
	buf := make([]byte, 0, 1024*1024)
	scanner.Buffer(buf, 8*1024*1024)

	total := 0
	group := newBatchGroup(s.cfg.BatchBulkSize, s.cfg.MaxTasksPerBulk)

	lineNo := 0
	for scanner.Scan() {
		lineNo++
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		// Parse arbitrary JSON object from manifest line.
		var obj map[string]any
		if err := json.Unmarshal([]byte(line), &obj); err != nil {
			// НОВОЕ: пишем в DLQ вместо остановки обработки
			s.reportFailedItem(ctx, datasetId, lineNo, line, err.Error())
			continue
		}

		itemId, _ := obj["id"].(string)
		if itemId == "" {
			// fallback to stable synthetic id
			itemId = fmt.Sprintf("line-%d", lineNo)
		}

		typ, _ := obj["type"].(string)
		if typ == "" {
			typ = "asset"
		}

		// "file" (preferred) or "assetRelPath"
		assetRelPath := ""
		if v, ok := obj["file"].(string); ok && strings.TrimSpace(v) != "" {
			assetRelPath = strings.TrimSpace(v)
		} else if v, ok := obj["assetRelPath"].(string); ok && strings.TrimSpace(v) != "" {
			assetRelPath = strings.TrimSpace(v)
		}

		// "text" optional
		textVal := ""
		if v, ok := obj["text"].(string); ok {
			textVal = v
		}

		// "meta" optional
		metaVal, _ := obj["meta"]

		isHoneypot := false
		if v, ok := obj["isHoneypot"].(bool); ok {
			isHoneypot = v
		}
		expectedAnswer := ""
		if v, ok := obj["expectedAnswer"].(string); ok {
			expectedAnswer = strings.TrimSpace(v)
		}

		payload := map[string]any{
			"format":     "manifest-jsonl",
			"manifest":   manifestAbsPath,
			"lineNumber": lineNo,
			"itemId":     itemId,
			"type":       typ,
			// Honeypot поля — скрыты от воркера, проверяются при submit
			"isHoneypot":     isHoneypot,
			"expectedAnswer": expectedAnswer,
		}
		if assetRelPath != "" {
			// Keep RELATIVE path from zip root; core-service will resolve inside dataset folder.
			payload["assetRelPath"] = assetRelPath
		}
		if strings.TrimSpace(textVal) != "" {
			payload["text"] = textVal
		}
		if metaVal != nil {
			payload["meta"] = metaVal
		}

		payloadBytes, _ := json.Marshal(payload)
		group.addTask(preTask{payloadJson: string(payloadBytes), status: "NEW"})
		total++

		if group.shouldFlush(batchSize) {
			if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
				return err
			}
			group.reset()
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("manifest scan error: %w", err)
	}

	if group.hasAny() {
		if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
			return err
		}
	}

	_ = s.patchDatasetTotalItems(ctx, datasetId, total)
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

func (s *Service) generateFromCSV(ctx context.Context, datasetId, absPath string, batchSize int) error {
	f, err := os.Open(absPath)
	if err != nil {
		return err
	}
	defer f.Close()

	r := csv.NewReader(bufio.NewReader(f))
	r.FieldsPerRecord = -1

	// read header (optional)
	_, err = r.Read()
	if err != nil {
		if errors.Is(err, io.EOF) {
			_ = s.patchDatasetTotalItems(ctx, datasetId, 0)
			return s.patchDatasetStatus(ctx, datasetId, "READY")
		}
		return fmt.Errorf("csv header read error: %w", err)
	}

	total := 0
	group := newBatchGroup(s.cfg.BatchBulkSize, s.cfg.MaxTasksPerBulk)

	rowNo := 1 // first row after header is 1

	for {
		record, err := r.Read()
		if err != nil {
			if errors.Is(err, io.EOF) {
				break
			}
			// НОВОЕ: пишем битую строку в DLQ вместо остановки обработки
			s.reportFailedItem(ctx, datasetId, rowNo, "", err.Error())
			rowNo++
			continue
		}
		_ = record
		isHoneypot := false
		expectedAnswer := ""
		if len(record) >= 3 {
			isHoneypot = strings.EqualFold(strings.TrimSpace(record[2]), "true")
		}
		if len(record) >= 4 {
			expectedAnswer = strings.TrimSpace(record[3])
		}

		// Текст задачи — обычно вторая колонка CSV (id, text, ...)
		taskText := ""
		if len(record) >= 2 {
			taskText = strings.TrimSpace(record[1])
		}

		payload := map[string]any{
			"filePath":       absPath,
			"format":         "csv",
			"rowNumber":      rowNo,
			"text":           strings.Join(record, ","),
			"isHoneypot":     isHoneypot,
			"expectedAnswer": expectedAnswer,
		}

		// Pre-annotation: вызываем HuggingFace для текстовых задач
		// AI работает как "тихий судья" — воркер не видит предсказание
		// При submit Java сравнит ответ воркера с aiSuggestedLabel
		if taskText != "" && !isHoneypot {
			if label, score, err := callHuggingFace(taskText); err == nil && score > 0.5 {
				payload["aiSuggestedLabel"] = label
				payload["aiConfidence"] = score
				payload["aiModel"] = "cardiffnlp/twitter-roberta-base-sentiment-latest"
			} else if err != nil {
				// API недоступен — не блокируем создание задачи
				log.Printf("[PreAnnotation] HuggingFace unavailable for row %d: %v", rowNo, err)
			}
		}
		payloadBytes, _ := json.Marshal(payload)

		group.addTask(preTask{payloadJson: string(payloadBytes), status: "NEW"})
		total++
		rowNo++

		if group.shouldFlush(batchSize) {
			_ = group.hasAny()
			if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
				return err
			}
			group.reset()
		}
	}

	if group.hasAny() {
		if err := s.flushGroup(ctx, datasetId, group, batchSize); err != nil {
			return err
		}
	}

	_ = s.patchDatasetTotalItems(ctx, datasetId, total)
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

type preTask struct {
	payloadJson string
	status      string
}

type batchGroup struct {
	batchBulkSize   int
	maxTasksPerBulk int

	// tasks for current batch-in-progress
	curBatchTasks []preTask

	// finalized batches (each is slice of tasks)
	batches [][]preTask
}

func newBatchGroup(batchBulkSize, maxTasksPerBulk int) *batchGroup {
	return &batchGroup{
		batchBulkSize:   batchBulkSize,
		maxTasksPerBulk: maxTasksPerBulk,
		curBatchTasks:   make([]preTask, 0, 128),
		batches:         make([][]preTask, 0, batchBulkSize),
	}
}

func (g *batchGroup) addTask(t preTask) {
	g.curBatchTasks = append(g.curBatchTasks, t)
}

func (g *batchGroup) shouldFlush(batchSize int) bool {
	// finalize a batch if reached batchSize
	if len(g.curBatchTasks) >= batchSize {
		g.batches = append(g.batches, g.curBatchTasks)
		g.curBatchTasks = make([]preTask, 0, batchSize)
	}

	// flush if we accumulated enough batches OR too many tasks in memory
	if len(g.batches) >= g.batchBulkSize {
		return true
	}

	// rough memory guard: tasks per bulk
	cnt := 0
	for _, b := range g.batches {
		cnt += len(b)
	}
	if cnt >= g.maxTasksPerBulk {
		return true
	}

	return false
}

func (g *batchGroup) hasAny() bool {
	if len(g.curBatchTasks) > 0 {
		g.batches = append(g.batches, g.curBatchTasks)
		g.curBatchTasks = nil
	}
	return len(g.batches) > 0
}

func (g *batchGroup) reset() {
	g.curBatchTasks = make([]preTask, 0, 128)
	g.batches = make([][]preTask, 0, g.batchBulkSize)
}

func (s *Service) flushGroup(ctx context.Context, datasetId string, g *batchGroup, batchSize int) error {
	// 1) Create batches in core
	createReq := coreCreateBatchesBulkRequest{Batches: make([]coreBatchItem, 0, len(g.batches))}
	for _, batchTasks := range g.batches {
		createReq.Batches = append(createReq.Batches, coreBatchItem{
			DatasetId:  datasetId,
			Status:     "READY",
			TotalTasks: len(batchTasks),
		})
	}

	var createResp coreCreateBatchesBulkResponse
	if err := s.corePOST(ctx, "/internal/task-batches/bulk", createReq, &createResp); err != nil {
		return fmt.Errorf("create task-batches failed: %w", err)
	}
	if len(createResp.BatchIds) != len(g.batches) {
		return fmt.Errorf("core returned %d batchIds, expected %d", len(createResp.BatchIds), len(g.batches))
	}

	// 2) Build one big tasks bulk request
	tasksReq := coreCreateTasksBulkRequest{Tasks: make([]coreTaskItem, 0)}
	for i, batchTasks := range g.batches {
		batchId := createResp.BatchIds[i]
		for _, t := range batchTasks {
			tasksReq.Tasks = append(tasksReq.Tasks, coreTaskItem{
				BatchId:     batchId,
				PayloadJson: t.payloadJson,
				Status:      t.status,
			})
		}
	}

	// 3) Create tasks
	if err := s.corePOST(ctx, "/internal/tasks/bulk", tasksReq, nil); err != nil {
		return fmt.Errorf("create tasks bulk failed: %w", err)
	}

	return nil
}

func (s *Service) patchDatasetStatus(ctx context.Context, datasetId, status string) error {
	path := fmt.Sprintf("/internal/datasets/%s/status?status=%s", datasetId, urlQueryEscape(status))
	return s.corePATCH(ctx, path, nil)
}

func (s *Service) patchDatasetTotalItems(ctx context.Context, datasetId string, total int) error {
	path := fmt.Sprintf("/internal/datasets/%s/total-items?value=%d", datasetId, total)
	return s.corePATCH(ctx, path, nil)
}

func (s *Service) corePOST(ctx context.Context, path string, body any, out any) error {
	url := s.cfg.CoreInternal + path

	payload, err := json.Marshal(body)
	if err != nil {
		return err
	}

	return doWithRetry(ctx, s.cfg.RetryCount, s.cfg.RetryDelay, func() error {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, strings.NewReader(string(payload)))
		if err != nil {
			return err
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Internal-Token", s.cfg.InternalToken)

		resp, err := s.http.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			b, _ := io.ReadAll(io.LimitReader(resp.Body, 8192))
			return fmt.Errorf("core POST %s returned %d: %s", path, resp.StatusCode, string(b))
		}

		if out != nil {
			return json.NewDecoder(resp.Body).Decode(out)
		}

		return nil
	})
}

func (s *Service) corePATCH(ctx context.Context, path string, body any) error {
	url := s.cfg.CoreInternal + path

	return doWithRetry(ctx, s.cfg.RetryCount, s.cfg.RetryDelay, func() error {
		var req *http.Request
		var err error

		if body != nil {
			payload, e := json.Marshal(body)
			if e != nil {
				return e
			}
			req, err = http.NewRequestWithContext(ctx, http.MethodPatch, url, strings.NewReader(string(payload)))
			req.Header.Set("Content-Type", "application/json")
		} else {
			req, err = http.NewRequestWithContext(ctx, http.MethodPatch, url, nil)
		}
		if err != nil {
			return err
		}

		req.Header.Set("X-Internal-Token", s.cfg.InternalToken)

		resp, err := s.http.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			b, _ := io.ReadAll(io.LimitReader(resp.Body, 8192))
			return fmt.Errorf("core PATCH %s returned %d: %s", path, resp.StatusCode, string(b))
		}
		return nil
	})
}

// ===== middleware / helpers =====

func withInternalToken(expected string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Only protect API endpoints; keep /healthz open.
		if strings.HasPrefix(r.URL.Path, "/api/") || strings.HasPrefix(r.URL.Path, "/internal/") {
			provided := r.Header.Get("X-Internal-Token")
			if !secureEqual(provided, expected) {
				writeJSON(w, http.StatusUnauthorized, map[string]any{"error": "Unauthorized: missing or invalid X-Internal-Token"})
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func secureEqual(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func doWithRetry(ctx context.Context, retries int, delay time.Duration, fn func() error) error {
	var last error
	for i := 0; i <= retries; i++ {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		err := fn()
		if err == nil {
			return nil
		}
		last = err
		if i == retries {
			break
		}
		select {
		case <-time.After(delay):
		case <-ctx.Done():
			return ctx.Err()
		}
	}
	return last
}

func getenv(key, def string) string {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return def
	}
	return v
}

func getenvInt(key string, def int) int {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return def
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return def
	}
	return n
}

func urlQueryEscape(s string) string {
	// minimal escape for statuses without importing net/url
	s = strings.ReplaceAll(s, " ", "%20")
	return s
}
