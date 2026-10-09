package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TrafficImportControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final TrafficImportController controller = new TrafficImportController(
            database, new ObjectMapper(), mock(TransactionTemplate.class));

    private final TrafficImportController.StationSpec station = new TrafficImportController.StationSpec(
            "SITE-1", "Điểm đo 1", "QL1", "kqldb_1", new BigDecimal("2.5"));
    private final OffsetDateTime instant = OffsetDateTime.parse("2026-10-07T08:00:00+07:00");

    @Test
    void rejectsMissingSurveyedLengthAndInvalidCountsBeforeDatabaseWork() {
        var snapshot = new TrafficImportController.DensityEvent("frame-1", "Bắc", instant, "Xe tải", 10);
        var noLength = new TrafficImportController.StationSpec("SITE-1", "Điểm đo 1", "QL1", "kqldb_1", null);
        var badCount = new TrafficImportController.CountEvent("event-1", "Bắc", 1, instant,
                instant.plusMinutes(15), "Xe tải", -1);
        var exampleStation = new TrafficImportController.StationSpec(
                "EXAMPLE-TRAFFIC-01", "Điểm mẫu", "Tuyến mẫu", "kqldb_1", new BigDecimal("2.5"));
        var validCount = new TrafficImportController.CountEvent("event-2", "Bắc", 1, instant,
                instant.plusMinutes(15), "Xe tải", 4);
        for (var request : List.of(
                new TrafficImportController.ImportRequest("batch-1", "UPLOADED", noLength, List.of(), List.of(snapshot)),
                new TrafficImportController.ImportRequest("batch-2", "UPLOADED", station, List.of(badCount), List.of()),
                new TrafficImportController.ImportRequest("batch-3", "UPLOADED", station, List.of(), List.of()),
                new TrafficImportController.ImportRequest("batch-5", "UPLOADED", exampleStation,
                        List.of(validCount), List.of()))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> controller.ingest(null, request)).getStatusCode().value());
        }
        verifyNoInteractions(database);
    }

    @Test
    void rejectsDuplicateKeysAcrossMeasurementTypes() {
        var count = new TrafficImportController.CountEvent("same-key", "Bắc", 1, instant,
                instant.plusMinutes(15), "Xe tải", 8);
        var snapshot = new TrafficImportController.DensityEvent("same-key", "Bắc", instant,
                "Xe tải", 8);
        var request = new TrafficImportController.ImportRequest("batch-4", "UPLOADED", station,
                List.of(count), List.of(snapshot));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.ingest(null, request)).getStatusCode().value());
        verifyNoInteractions(database);
    }
}
