package com.orbit.integration.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${orbit.jira.base-url:}")
    private String baseUrl;

    @Value("${orbit.jira.email:}")
    private String email;

    @Value("${orbit.jira.api-token:}")
    private String apiToken;

    public Map<String, Object> addComment(String issueKey, String comment) {
        if (baseUrl.isBlank()) return Map.of("ok", false, "error", "jira_not_configured");
        try {
            String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/comment";
            String body = objectMapper.writeValueAsString(Map.of(
                "body", Map.of(
                    "type", "doc", "version", 1,
                    "content", new Object[]{Map.of(
                        "type", "paragraph",
                        "content", new Object[]{Map.of("type", "text", "text", comment)}
                    )}
                )
            ));
            int status = post(url, body);
            if (status == 201) {
                log.info("JiraClient: added comment to {}", issueKey);
                return Map.of("ok", true, "issueKey", issueKey);
            }
            return Map.of("ok", false, "error", "jira_api_error", "status", status);
        } catch (Exception e) {
            log.error("JiraClient.addComment failed for {}: {}", issueKey, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> transition(String issueKey, String transitionId) {
        if (baseUrl.isBlank()) return Map.of("ok", false, "error", "jira_not_configured");
        try {
            String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";
            String body = objectMapper.writeValueAsString(Map.of(
                "transition", Map.of("id", transitionId)
            ));
            int status = post(url, body);
            if (status == 204) {
                log.info("JiraClient: transitioned {} to {}", issueKey, transitionId);
                return Map.of("ok", true, "issueKey", issueKey, "transitionId", transitionId);
            }
            return Map.of("ok", false, "error", "jira_api_error", "status", status);
        } catch (Exception e) {
            log.error("JiraClient.transition failed for {}: {}", issueKey, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    private int post(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection http = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        http.setRequestMethod("POST");
        http.setRequestProperty("Authorization", "Basic " +
            Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8)));
        http.setRequestProperty("Content-Type", "application/json");
        http.setRequestProperty("Accept", "application/json");
        http.setDoOutput(true);
        http.setConnectTimeout(5000);
        http.setReadTimeout(10000);
        try (OutputStream os = http.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return http.getResponseCode();
    }
}
