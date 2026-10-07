package vn.gov.drvn.kcht.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - REST API")
                        .version("1.0.0")
                        .description("Tập hợp các API chuẩn RESTful đọc và khai thác dữ liệu KCHT phục vụ ứng dụng WebGIS, Dashboard, Tra cứu và Hồ sơ tài liệu.")
                        .contact(new Contact()
                                .name("Cục Đường bộ Việt Nam (DRVN)")
                                .url("https://drvn.gov.vn"))
                        .license(new License()
                                .name("DRVN Internal License")))
                .servers(List.of(
                        new Server().url("/").description("Máy chủ mặc định")
                ));
    }
}
