package vn.gov.drvn.kcht.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {
    @Test
    void documentsIndependentDemoAndBearerAuthentication() {
        OpenAPI api = new OpenApiConfig().customOpenAPI();

        assertTrue(api.getInfo().getTitle().contains("Demo"));
        assertTrue(api.getInfo().getDescription().contains("chưa kết nối"));
        assertNull(api.getInfo().getContact());
        SecurityScheme authentication = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertEquals(SecurityScheme.Type.HTTP, authentication.getType());
        assertEquals("bearer", authentication.getScheme());
        assertEquals("JWT", authentication.getBearerFormat());
    }

    @Test
    void marksProtectedApiOperationsButNotPublicAuthentication() {
        OpenAPI api = new OpenApiConfig().customOpenAPI().paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(new Operation()))
                .addPathItem("/api/auth/refresh", new PathItem().post(new Operation()))
                .addPathItem("/api/vroad/inbound/batches", new PathItem().post(new Operation()))
                .addPathItem("/api/admin/operations/summary", new PathItem().get(new Operation())));

        OpenApiConfig config = new OpenApiConfig();
        config.documentProtectedEndpoints().customise(api);
        config.documentProtectedEndpoints().customise(api);

        assertNull(api.getPaths().get("/api/auth/login").getPost().getSecurity());
        assertNull(api.getPaths().get("/api/auth/refresh").getPost().getSecurity());
        assertEquals(1, api.getPaths().get("/api/vroad/inbound/batches").getPost().getSecurity().size());
        assertTrue(api.getPaths().get("/api/admin/operations/summary").getGet()
                .getSecurity().get(0).containsKey("bearerAuth"));
    }
}
