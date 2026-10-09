package vn.gov.drvn.kcht.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demo Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - REST API")
                        .version("1.0.0")
                        .description("API của bản demo độc lập, không phải cổng chính thức. Lô VroadAI chỉ lưu ở vùng chờ; chưa kết nối hệ thống nguồn."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .servers(List.of(
                        new Server().url("/").description("Máy chủ mặc định")
                ));
    }

    @Bean
    public OpenApiCustomizer documentProtectedEndpoints() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/") || PUBLIC_AUTH_PATHS.contains(path)) return;
                item.readOperations().forEach(operation -> {
                    if (operation.getSecurity() == null || operation.getSecurity().stream()
                            .noneMatch(requirement -> requirement.containsKey("bearerAuth"))) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    }
                });
            });
        };
    }
}
