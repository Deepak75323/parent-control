package com.parentalcontrol.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the LLM log classification golden dataset structure and expected outputs.
 * Does not call a live LLM — ensures the dataset is well-formed and internally consistent
 * for use in regression / prompt-evaluation pipelines.
 */
class LogClassificationGoldenDatasetTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode root;

    @BeforeAll
    static void loadDataset() throws Exception {
        try (InputStream in = LogClassificationGoldenDatasetTest.class
                .getResourceAsStream("/llm-classification-golden-dataset.json")) {
            assertNotNull(in, "Golden dataset resource must exist at /llm-classification-golden-dataset.json");
            root = MAPPER.readTree(in);
        }
    }

    @Test
    @DisplayName("Dataset has version and at least 8 cases")
    void datasetMetadata() {
        assertEquals("1.0", root.path("version").asText());
        JsonNode cases = root.path("cases");
        assertTrue(cases.isArray());
        assertTrue(cases.size() >= 8, "Expected at least 8 golden cases, got " + cases.size());
    }

    static Stream<JsonNode> allCases() {
        List<JsonNode> list = new ArrayList<>();
        Iterator<JsonNode> it = root.path("cases").elements();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("Each case has id, log, expected_output with event_type")
    void caseStructure(JsonNode testCase) {
        assertFalse(testCase.path("id").asText().isBlank(), "case id required");
        assertTrue(testCase.has("log"), "case must have log");
        assertTrue(testCase.has("expected_output"), "case must have expected_output");

        JsonNode expected = testCase.path("expected_output");
        String eventType = expected.path("event_type").asText();
        assertFalse(eventType.isBlank(), "expected_output.event_type required");

        assertTrue(
                List.of("activity", "audit", "authentication", "generic", "security_event").contains(eventType),
                "Unknown event_type: " + eventType);

        assertTrue(expected.has("confidence"), "confidence required");
        double confidence = expected.path("confidence").asDouble();
        assertTrue(confidence >= 0.0 && confidence <= 1.0, "confidence must be 0..1");

        assertTrue(expected.has("message"), "message required");
        assertTrue(expected.has("reason"), "reason required");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("Log payloads include timestamp and message")
    void logPayloadBasics(JsonNode testCase) {
        JsonNode log = testCase.path("log");
        assertTrue(log.has("timestamp"), "log.timestamp required");
        assertTrue(log.has("message"), "log.message required");
        assertFalse(log.path("message").asText().isBlank());
    }

    @Test
    @DisplayName("All required event categories are represented")
    void eventCategoryCoverage() {
        List<String> ids = new ArrayList<>();
        root.path("cases").forEach(c -> ids.add(c.path("id").asText()));

        assertTrue(ids.stream().anyMatch(id -> id.startsWith("activity-")),
                "Missing activity case");
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("audit-")),
                "Missing audit case");
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("authentication-")),
                "Missing authentication case");
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("generic-")),
                "Missing generic case");
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("security-")),
                "Missing security_event case");
    }

    @Test
    @DisplayName("Activity user-create case matches rich metadata expectations")
    void activityUserCreateGolden() {
        JsonNode c = findCase("activity-user-create");
        JsonNode expected = c.path("expected_output");
        assertEquals("activity", expected.path("event_type").asText());
        assertEquals("user_create", expected.path("activity_type").asText());
        assertEquals("success", expected.path("status").asText());
        assertEquals("user", expected.path("target_type").asText());
        assertEquals("user_12345", expected.path("target_id").asText());
        assertTrue(c.path("log").path("activity_event").path("correlation").has("trace_id"));
    }

    @Test
    @DisplayName("Audit data-export case expects failure with compliance metadata")
    void auditDataExportGolden() {
        JsonNode c = findCase("audit-data-export");
        JsonNode expected = c.path("expected_output");
        assertEquals("audit", expected.path("event_type").asText());
        assertEquals("security_event", expected.path("audit_type").asText());
        assertEquals("failure", expected.path("status").asText());
        assertTrue(expected.path("metadata").has("compliance_standard"));
    }

    @Test
    @DisplayName("Security bruteforce case expects high confidence failure")
    void securityBruteforceGolden() {
        JsonNode c = findCase("security-bruteforce-block");
        JsonNode expected = c.path("expected_output");
        assertEquals("security_event", expected.path("event_type").asText());
        assertEquals("failure", expected.path("status").asText());
        assertTrue(expected.path("confidence").asDouble() >= 0.9);
        assertTrue(c.path("log").path("security_event").has("geo"));
    }

    private static JsonNode findCase(String id) {
        for (JsonNode c : root.path("cases")) {
            if (id.equals(c.path("id").asText())) {
                return c;
            }
        }
        throw new AssertionError("Case not found: " + id);
    }
}
