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

// ── Config ──────────────────────────────────────────────────────────────────

type Config struct {
	Port          string
	CoreInternal  string
	InternalToken string
	DataDir       string
	HTTPTimeout   time.Duration
	RetryCount    int
	RetryDelay    time.Duration
}

func loadConfig() Config {
	return Config{
		Port:          getenv("RUNNER_PORT", "8090"),
		CoreInternal:  strings.TrimRight(getenv("CORE_INTERNAL_URL", "http://core-service:8082"), "/"),
		InternalToken: getenv("INTERNAL_TOKEN", "change-me"),
		DataDir:       getenv("DATA_DIR", "/data"),
		HTTPTimeout:   time.Duration(getenvInt("HTTP_TIMEOUT_SECONDS", 30)) * time.Second,
		RetryCount:    getenvInt("RETRY_COUNT", 3),
		RetryDelay:    time.Duration(getenvInt("RETRY_DELAY_MS", 500)) * time.Millisecond,
	}
}

// ── Main ─────────────────────────────────────────────────────────────────────

func main() {
	cfg := loadConfig()
	if strings.TrimSpace(cfg.InternalToken) == "" {
		log.Fatal("INTERNAL_TOKEN is required")
	}

	svc := &Service{cfg: cfg, http: &http.Client{Timeout: cfg.HTTPTimeout}}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"status": "UP"})
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("runner-service"))
	})
	mux.HandleFunc("/api/v1/runner/datasets/", svc.handleGenerateTasks)

	log.Printf("runner listening on :%s", cfg.Port)
	if err := http.ListenAndServe(":"+cfg.Port, withInternalToken(cfg.InternalToken, mux)); err != nil {
		log.Fatal(err)
	}
}

// ── Service ──────────────────────────────────────────────────────────────────

type Service struct {
	cfg  Config
	http *http.Client
}

// ── DTOs ─────────────────────────────────────────────────────────────────────

type GenerateTasksRequest struct {
	DatasetId      string `json:"datasetId"`
	SourcePath     string `json:"sourcePath"`
	SourceType     string `json:"sourceType,omitempty"`
	ManifestPath   string `json:"manifestPath,omitempty"`
	ReviewersCount int    `json:"reviewersCount"`
	RewardPoints   int    `json:"rewardPoints"`
	ProjectId      string `json:"projectId"`
}

// Bulk task create request — one HTTP call for all tasks in dataset
type coreCreateTasksBulkRequest struct {
	DatasetId string         `json:"datasetId"`
	ProjectId string         `json:"projectId"`
	Tasks     []taskPayload  `json:"tasks"`
}
type taskPayload struct {
	PayloadJson string `json:"payloadJson"`
	Status      string `json:"status"`
}

type failedItemRequest struct {
	LineNumber int    `json:"lineNumber"`
	RawContent string `json:"rawContent"`
	ErrorMsg   string `json:"errorMsg"`
}

type hfRequest struct {
	Inputs string `json:"inputs"`
}
type hfLabel struct {
	Label string  `json:"label"`
	Score float64 `json:"score"`
}

// ── HTTP Handler ─────────────────────────────────────────────────────────────

func (s *Service) handleGenerateTasks(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]any{"error": "Method not allowed"})
		return
	}

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
	req.DatasetId = datasetId

	if strings.TrimSpace(req.SourcePath) == "" {
		writeJSON(w, http.StatusBadRequest, map[string]any{"error": "sourcePath is required"})
		return
	}

	// Run asynchronously — return 202 immediately
	go func(copyReq GenerateTasksRequest) {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Minute)
		defer cancel()
		if err := s.generateTasks(ctx, copyReq); err != nil {
			log.Printf("generateTasks failed datasetId=%s err=%v", copyReq.DatasetId, err)
			_ = s.patchDatasetStatus(context.Background(), copyReq.DatasetId, "FAILED")
		}
	}(req)

	writeJSON(w, http.StatusAccepted, map[string]any{"status": "accepted", "datasetId": datasetId})
}

// ── Task Generation ──────────────────────────────────────────────────────────

func (s *Service) generateTasks(ctx context.Context, req GenerateTasksRequest) error {
	absPath, err := s.resolveSourcePath(req.SourcePath)
	if err != nil {
		return err
	}

	_ = s.patchDatasetStatus(ctx, req.DatasetId, "GENERATING")

	// Manifest / ZIP path
	if strings.EqualFold(strings.TrimSpace(req.SourceType), "ZIP_MANIFEST") ||
		strings.TrimSpace(req.ManifestPath) != "" {
		mp := strings.TrimSpace(req.ManifestPath)
		if mp == "" {
			return fmt.Errorf("manifestPath is required for ZIP_MANIFEST datasets")
		}
		manifestAbs, err := s.resolvePathWithinDataDir(mp)
		if err != nil {
			return err
		}
		return s.generateFromManifestJSONL(ctx, req.DatasetId, req.ProjectId, manifestAbs)
	}

	ext := strings.ToLower(filepath.Ext(absPath))
	switch ext {
	case ".csv":
		return s.generateFromCSV(ctx, req.DatasetId, req.ProjectId, absPath)
	case ".jsonl", ".ndjson":
		return s.generateFromJSONL(ctx, req.DatasetId, req.ProjectId, absPath)
	default:
		return fmt.Errorf("unsupported format: %s (supported: .csv, .jsonl)", ext)
	}
}

// ── CSV ──────────────────────────────────────────────────────────────────────

func (s *Service) generateFromCSV(ctx context.Context, datasetId, projectId, absPath string) error {
	f, err := os.Open(absPath)
	if err != nil { return err }
	defer f.Close()
	r := csv.NewReader(bufio.NewReader(f))
	r.FieldsPerRecord = -1
	if _, err = r.Read(); err != nil {
		if errors.Is(err, io.EOF) {
			_ = s.patchDatasetTotalItems(ctx, datasetId, 0)
			return s.patchDatasetStatus(ctx, datasetId, "READY")
		}
		return fmt.Errorf("csv header read error: %w", err)
	}
	var tasks []taskPayload
	rowNo := 1
	for {
		record, err := r.Read()
		if err != nil {
			if errors.Is(err, io.EOF) { break }
			s.reportFailedItem(ctx, datasetId, rowNo, "", err.Error())
			rowNo++; continue
		}
		isHoneypot := false; expectedAnswer := ""; taskText := ""
		if len(record) >= 2 { taskText = strings.TrimSpace(record[1]) }
		if len(record) >= 3 { isHoneypot = strings.EqualFold(strings.TrimSpace(record[2]), "true") }
		if len(record) >= 4 { expectedAnswer = strings.TrimSpace(record[3]) }
		payload := map[string]any{"format": "csv", "rowNumber": rowNo, "text": taskText, "isHoneypot": isHoneypot, "expectedAnswer": expectedAnswer}
		if taskText != "" && !isHoneypot {
			if label, score, hfErr := callHuggingFace(taskText); hfErr == nil && score > 0.5 {
				payload["aiSuggestedLabel"] = label; payload["aiConfidence"] = score
			} else if hfErr != nil {
				log.Printf("[PreAnnotation] HuggingFace unavailable for row %d: %v", rowNo, hfErr)
			}
		}
		payloadBytes, _ := json.Marshal(payload)
		tasks = append(tasks, taskPayload{PayloadJson: string(payloadBytes), Status: "NEW"})
		rowNo++
	}
	if len(tasks) > 0 {
		if err := s.createTasksBulk(ctx, datasetId, projectId, tasks); err != nil {
			return fmt.Errorf("bulk create tasks failed: %w", err)
		}
	}
	_ = s.patchDatasetTotalItems(ctx, datasetId, len(tasks))
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

// ── JSONL ────────────────────────────────────────────────────────────────────

func (s *Service) generateFromJSONL(ctx context.Context, datasetId, projectId, absPath string) error {
	f, err := os.Open(absPath)
	if err != nil { return err }
	defer f.Close()
	scanner := bufio.NewScanner(f)
	buf := make([]byte, 0, 1024*1024)
	scanner.Buffer(buf, 4*1024*1024)
	var tasks []taskPayload
	lineNo := 0
	for scanner.Scan() {
		lineNo++
		line := strings.TrimSpace(scanner.Text())
		if line == "" { continue }
		var obj map[string]any
		if err := json.Unmarshal([]byte(line), &obj); err != nil {
			s.reportFailedItem(ctx, datasetId, lineNo, line, err.Error())
			continue
		}
		payload := map[string]any{"format": "jsonl", "lineNumber": lineNo, "data": obj}
		if text, ok := obj["text"].(string); ok { payload["text"] = text }
		payloadBytes, _ := json.Marshal(payload)
		tasks = append(tasks, taskPayload{PayloadJson: string(payloadBytes), Status: "NEW"})
	}
	if err := scanner.Err(); err != nil { return fmt.Errorf("jsonl scan error: %w", err) }
	if len(tasks) > 0 {
		if err := s.createTasksBulk(ctx, datasetId, projectId, tasks); err != nil {
			return fmt.Errorf("bulk create tasks failed: %w", err)
		}
	}
	_ = s.patchDatasetTotalItems(ctx, datasetId, len(tasks))
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

// ── Manifest JSONL ───────────────────────────────────────────────────────────

func (s *Service) generateFromManifestJSONL(ctx context.Context, datasetId, projectId, manifestAbsPath string) error {
	f, err := os.Open(manifestAbsPath)
	if err != nil { return fmt.Errorf("failed to open manifest: %w", err) }
	defer f.Close()
	scanner := bufio.NewScanner(f)
	buf := make([]byte, 0, 1024*1024)
	scanner.Buffer(buf, 8*1024*1024)
	var tasks []taskPayload
	lineNo := 0
	for scanner.Scan() {
		lineNo++
		line := strings.TrimSpace(scanner.Text())
		if line == "" { continue }
		var obj map[string]any
		if err := json.Unmarshal([]byte(line), &obj); err != nil {
			s.reportFailedItem(ctx, datasetId, lineNo, line, err.Error())
			continue
		}
		isHoneypot := false
		if v, ok := obj["isHoneypot"].(bool); ok { isHoneypot = v }
		expectedAnswer := ""
		if v, ok := obj["expectedAnswer"].(string); ok { expectedAnswer = strings.TrimSpace(v) }
		payload := map[string]any{"format": "manifest-jsonl", "lineNumber": lineNo, "isHoneypot": isHoneypot, "expectedAnswer": expectedAnswer, "data": obj}
		if text, ok := obj["text"].(string); ok { payload["text"] = text }
		payloadBytes, _ := json.Marshal(payload)
		tasks = append(tasks, taskPayload{PayloadJson: string(payloadBytes), Status: "NEW"})
	}
	if err := scanner.Err(); err != nil { return fmt.Errorf("manifest scan error: %w", err) }
	if len(tasks) > 0 {
		if err := s.createTasksBulk(ctx, datasetId, projectId, tasks); err != nil {
			return fmt.Errorf("bulk create tasks failed: %w", err)
		}
	}
	_ = s.patchDatasetTotalItems(ctx, datasetId, len(tasks))
	return s.patchDatasetStatus(ctx, datasetId, "READY")
}

// ── Core API calls ───────────────────────────────────────────────────────────

// createTasksBulk sends all tasks in one HTTP call to core-service
func (s *Service) createTasksBulk(ctx context.Context, datasetId, projectId string, tasks []taskPayload) error {
	body := coreCreateTasksBulkRequest{
		DatasetId: datasetId,
		ProjectId: projectId,
		Tasks:     tasks,
	}
	return s.corePOST(ctx, "/internal/tasks/bulk", body, nil)
}

func (s *Service) patchDatasetStatus(ctx context.Context, datasetId, status string) error {
	path := fmt.Sprintf("/internal/datasets/%s/status?status=%s", datasetId, status)
	return s.corePATCH(ctx, path, nil)
}

func (s *Service) patchDatasetTotalItems(ctx context.Context, datasetId string, total int) error {
	path := fmt.Sprintf("/internal/datasets/%s/total-items?value=%d", datasetId, total)
	return s.corePATCH(ctx, path, nil)
}

func (s *Service) reportFailedItem(ctx context.Context, datasetID string, lineNo int, raw, errMsg string) {
	req := failedItemRequest{LineNumber: lineNo, RawContent: raw, ErrorMsg: errMsg}
	path := fmt.Sprintf("/internal/datasets/%s/failed-items", datasetID)
	if err := s.corePOST(ctx, path, req, nil); err != nil {
		log.Printf("[DLQ] Failed to report failed item (line %d): %v", lineNo, err)
	}
}

// ── HuggingFace ──────────────────────────────────────────────────────────────

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
	var result [][]hfLabel
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", 0, fmt.Errorf("huggingface decode error: %w", err)
	}
	if len(result) == 0 || len(result[0]) == 0 {
		return "", 0, fmt.Errorf("empty huggingface response")
	}
	best := result[0][0]
	for _, r := range result[0] {
		if r.Score > best.Score {
			best = r
		}
	}
	return strings.ToLower(best.Label), best.Score, nil
}

// ── HTTP helpers ─────────────────────────────────────────────────────────────

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
			if err == nil {
				req.Header.Set("Content-Type", "application/json")
			}
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
			b, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
			return fmt.Errorf("core PATCH %s returned %d: %s", path, resp.StatusCode, string(b))
		}
		return nil
	})
}

func doWithRetry(ctx context.Context, n int, delay time.Duration, fn func() error) error {
	var last error
	for i := 0; i < n; i++ {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if last = fn(); last == nil {
			return nil
		}
		if i < n-1 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(delay):
			}
		}
	}
	return last
}

func withInternalToken(token string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t := r.Header.Get("X-Internal-Token")
		if subtle.ConstantTimeCompare([]byte(t), []byte(token)) != 1 {
			writeJSON(w, http.StatusUnauthorized, map[string]any{"error": "unauthorized"})
			return
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getenvInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return def
}

func urlQueryEscape(s string) string {
	return strings.NewReplacer(" ", "%20", "&", "%26", "=", "%3D").Replace(s)
}

// resolveSourcePath resolves and validates the source path is within DATA_DIR
func (s *Service) resolveSourcePath(sourcePath string) (string, error) {
	return s.resolvePathWithinDataDir(sourcePath)
}

// resolvePathWithinDataDir resolves path and ensures it stays within DATA_DIR (security check)
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
	rel, err := filepath.Rel(dataAbs, abs)
	if err != nil || strings.HasPrefix(rel, "..") {
		return "", fmt.Errorf("path must be inside DATA_DIR (%s)", dataAbs)
	}
	return abs, nil
}
