package vn.gov.drvn.kcht.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controller cung cấp endpoint kiểm tra trạng thái hoạt động công khai (Root & Health probe),
 * phục vụ cho các dịch vụ giám sát tự động (UptimeRobot, Render Health Check, Load Balancer).
 */
@RestController
public class RootHealthController {

    @GetMapping({"/", "/health"})
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "KCHT Road Infrastructure Management Backend",
                "timestamp", Instant.now().toString()
        ));
    }
}
