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

func (s *Service) resolveSourcePath(sourcePath string) (string, error) {
	p := strings.TrimSpace(sourcePath)
	if p == "" {
		return "", errors.New("sourcePath is empty")
	}

	// If core provided an absolute path (/data/...), keep it.
	if !filepath.IsAbs(p) {
		p = filepath.Join(s.cfg.DataDir, p)
	}

	abs, err := filepath.Abs(filepath.Clean(p))
	if err != nil {
		return "", fmt.Errorf("failed to resolve sourcePath: %w", err)
	}

	dataAbs, err := filepath.Abs(filepath.Clean(s.cfg.DataDir))
	if err != nil {
		return "", fmt.Errorf("failed to resolve DATA_DIR: %w", err)
	}

	// Security: ensure file is within DATA_DIR
	rel, err := filepath.Rel(dataAbs, abs)
	if err != nil || strings.HasPrefix(rel, "..") {
		return "", fmt.Errorf("sourcePath must be inside DATA_DIR (%s)", dataAbs)
	}

	st, err := os.Stat(abs)
	if err != nil {
		return "", fmt.Errorf("source file not found: %s", abs)
	}
	if st.IsDir() {
		return "", fmt.Errorf("sourcePath points to a directory: %s", abs)
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
		_, err := r.Read()
		if err != nil {
			if errors.Is(err, io.EOF) {
				break
			}
			return fmt.Errorf("csv read error (row %d): %w", rowNo, err)
		}

		payload := map[string]any{
			"filePath":  absPath,
			"format":    "csv",
			"rowNumber": rowNo,
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
