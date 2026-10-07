package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://localhost:5436/kcht_db",
        "spring.datasource.username=kcht_user",
        "spring.datasource.password=${POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Kiểm thử tích hợp hợp đồng API Giai đoạn 4 - API contract, catalogs, documents, deterministic pagination")
public class DatasetApiContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Catalog alias: GET /api/catalogs/{catalog} hoạt động tương đương /api/reference-catalogs/{catalog}")
    void testCatalogAliasRoute() throws Exception {
        mockMvc.perform(get("/api/catalogs/c_tinhthanhpho")
                        .param("page", "0")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].catalogCode").value("c_tinhthanhpho"));
    }

    @Test
    @DisplayName("2. Catalog item lookup: GET /api/reference-catalogs/{catalog}/{itemCode} và GET /api/catalogs/{catalog}/{itemCode}")
    void testCatalogItemLookup() throws Exception {
        // Tra cứu qua đường dẫn chuẩn
        mockMvc.perform(get("/api/reference-catalogs/c_tinhthanhpho/01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCode").value("01"))
                .andExpect(jsonPath("$.itemName", containsString("Hà Nội")));

        // Tra cứu qua alias ngắn /api/catalogs
        mockMvc.perform(get("/api/catalogs/c_tinhthanhpho/01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCode").value("01"))
                .andExpect(jsonPath("$.itemName", containsString("Hà Nội")));

        // Tra cứu mục không tồn tại trả về 404
        mockMvc.perform(get("/api/catalogs/c_tinhthanhpho/NON_EXISTENT_99999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("3. Document detail lookup: GET /api/documents/{id} và GET /api/documents/{id}/metadata")
    void testDocumentDetailLookup() throws Exception {
        String docKey = "17894819108555896";
        String fileEntryId = "9a85e1f6-b071-4b8a-8be5-426c990b14bb";

        // Tra cứu theo record_key
        mockMvc.perform(get("/api/documents/" + docKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docKey))
                .andExpect(jsonPath("$.fileEntryId").value(fileEntryId))
                .andExpect(jsonPath("$.fileName", notNullValue()));

        // Tra cứu qua đường dẫn /{id}/metadata
        mockMvc.perform(get("/api/documents/" + docKey + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docKey))
                .andExpect(jsonPath("$.fileEntryId").value(fileEntryId));

        // Tra cứu theo file_entry_id UUID
        mockMvc.perform(get("/api/documents/" + fileEntryId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileEntryId").value(fileEntryId));

        // Tài liệu không tồn tại -> 404
        mockMvc.perform(get("/api/documents/non_existent_doc_id_9999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("4. Document binary download: GET /api/documents/{id}/file kèm Content-Disposition")
    void testDocumentFileDownload() throws Exception {
        String docKey = "17894819108555896";

        mockMvc.perform(get("/api/documents/" + docKey + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("5. Deterministic Pagination: Phân trang ổn định, không trùng lặp và không nhảy cóc giữa các trang khi sort")
    void testDeterministicPagination() throws Exception {
        // Lấy trang 0 (5 bản ghi, sort theo name asc)
        MvcResult resPage0 = mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "name,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Lấy trang 1 (5 bản ghi tiếp theo, cùng sort)
        MvcResult resPage1 = mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonPage0 = objectMapper.readTree(resPage0.getResponse().getContentAsString());
        JsonNode jsonPage1 = objectMapper.readTree(resPage1.getResponse().getContentAsString());

        JsonNode content0 = jsonPage0.get("content");
        JsonNode content1 = jsonPage1.get("content");

        assertEquals(5, content0.size());
        assertEquals(5, content1.size());

        Set<Long> idsPage0 = new HashSet<>();
        for (JsonNode item : content0) {
            idsPage0.add(item.get("id").asLong());
        }

        Set<Long> idsPage1 = new HashSet<>();
        for (JsonNode item : content1) {
            idsPage1.add(item.get("id").asLong());
        }

        // Đảm bảo không có ID nào trùng lặp giữa 2 trang (deterministic pagination invariance)
        Set<Long> intersection = new HashSet<>(idsPage0);
        intersection.retainAll(idsPage1);
        assertTrue(intersection.isEmpty(), "Phát hiện ID bị trùng lặp giữa trang 0 và trang 1: " + intersection);
    }
}
