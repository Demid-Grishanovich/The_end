package com.datacrowd.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Service
public class RunnerClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String runnerBaseUrl;
    private final String internalToken;

    public RunnerClient(
            @Value("${app.runner-base-url:http://runner:8090}") String runnerBaseUrl,
            @Value("${app.security.internal-token:super-internal-token-change-me}") String internalToken
    ) {
        this.runnerBaseUrl = runnerBaseUrl;
        this.internalToken = internalToken;
    }

    public void triggerGenerate(UUID datasetId, Map<String, Object> body) {
        String url = runnerBaseUrl + "/api/v1/runner/datasets/" + datasetId + "/generate-tasks";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, req, String.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Runner returned " + resp.getStatusCode() + ": " + resp.getBody());
        }
    }
}
