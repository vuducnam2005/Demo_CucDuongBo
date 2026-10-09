package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class VroadInboundControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final VroadInboundController controller = new VroadInboundController(
            database, mapper, mock(TransactionTemplate.class));

    @Test
    void rejectsUnversionedOrOversizedBatchesWithoutWriting() {
        JsonNode sample = mapper.createObjectNode().put("sourceId", "DEF-001").put("kind", "DEFECT");
        for (VroadInboundController.IngestRequest request : List.of(
                new VroadInboundController.IngestRequest("batch-demo-1", "unknown", List.of(sample)),
                new VroadInboundController.IngestRequest("batch-demo-1", "demo-proposal-v1", List.of()),
                new VroadInboundController.IngestRequest("x", "demo-proposal-v1", List.of(sample)))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> controller.stage(null, request)).getStatusCode().value());
        }
        verifyNoInteractions(database);
    }

    @Test
    void marksDuplicateAndInvalidCoordinatesAndConfidencePerRecord() {
        Set<String> seen = new HashSet<>();
        JsonNode sample = mapper.createObjectNode().put("sourceId", "00123").put("kind", "DEFECT")
                .put("latitude", 21).put("longitude", 106).put("confidence", 0.8);
        assertTrue(controller.validate(sample, seen).isEmpty());
        assertTrue(controller.validate(sample, seen).stream().anyMatch(error -> error.contains("Trùng mã")));
        JsonNode invalid = mapper.createObjectNode().put("sourceId", "00124").put("kind", "DEFECT")
                .put("latitude", 21).put("confidence", 2.0);
        assertEquals(2, controller.validate(invalid, seen).size());
    }
}
