package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tính toàn vẹn của phân trang (Pagination Stability & Integrity Test):
 * Đảm bảo duyệt qua nhiều trang liên tiếp:
 * - Không có bản ghi nào bị lặp (Zero Duplicates).
 * - Không có bản ghi nào bị mất (Zero Misses).
 * - Thứ tự sắp xếp tất định (Deterministic Sort Order).
 * - Xử lý an toàn các giá trị biên (Boundary Conditions: âm, vượt ngưỡng, trang sâu).
 */
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
@DisplayName("Kiểm thử Phân trang: Không lặp, Không mất bản ghi và Thứ tự tất định")
class PaginationIntegrityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Duyệt 10 trang liên tiếp (250 bản ghi): Tuyệt đối không lặp và không mất bản ghi")
    void testSequentialPaginationNoDuplicatesAndNoMissingRecords() throws Exception {
        int pageSize = 25;
        int numberOfPages = 10;
        int expectedTotalFetched = pageSize * numberOfPages;

        List<Long> allFetchedIds = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();

        Long lastIdOnPreviousPage = -1L;

        for (int page = 0; page < numberOfPages; page++) {
            MvcResult res = mockMvc.perform(get("/api/datasets/tbl_bridge/records")
                            .header("X-User-Role", "ROLE_ADMIN")
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(pageSize))
                            .param("sort", "id,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page", is(page)))
                    .andExpect(jsonPath("$.size", is(pageSize)))
                    .andExpect(jsonPath("$.content", hasSize(pageSize)))
                    .andReturn();

            JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
            JsonNode content = root.get("content");

            Long firstIdOnCurrentPage = content.get(0).get("id").asLong();
            if (page > 0) {
                // Kiểm tra tính tất định: id đầu trang hiện tại phải lớn hơn id cuối trang trước
                assertTrue(firstIdOnCurrentPage > lastIdOnPreviousPage,
                        String.format("Lỗi thứ tự sắp xếp tại trang %d: id đầu trang (%d) không lớn hơn id cuối trang trước (%d)",
                                page, firstIdOnCurrentPage, lastIdOnPreviousPage));
            }

            for (JsonNode item : content) {
                Long id = item.get("id").asLong();
                allFetchedIds.add(id);
                uniqueIds.add(id);
            }

            lastIdOnPreviousPage = content.get(pageSize - 1).get("id").asLong();
        }

        // 1. Số lượng bản ghi thu thập đúng bằng 250
        assertEquals(expectedTotalFetched, allFetchedIds.size(), "Tổng số bản ghi đọc được phải đúng 250");

        // 2. Không có bản ghi trùng lặp (Set.size == List.size)
        assertEquals(allFetchedIds.size(), uniqueIds.size(),
                String.format("Phát hiện bản ghi bị lặp qua các trang: Thu thập %d dòng nhưng chỉ có %d id duy nhất",
                        allFetchedIds.size(), uniqueIds.size()));
    }

    @Test
    @DisplayName("2. Kiểm tra Phân trang Sắp xếp Giảm dần (id,desc): Thứ tự tất định và không lặp")
    void testDeterministicSortDescending() throws Exception {
        int pageSize = 20;
        int numberOfPages = 5;

        List<Long> allIds = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();
        Long lastId = Long.MAX_VALUE;

        for (int page = 0; page < numberOfPages; page++) {
            MvcResult res = mockMvc.perform(get("/api/datasets/tbl_bridge/records")
                            .header("X-User-Role", "ROLE_ADMIN")
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(pageSize))
                            .param("sort", "id,desc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode content = objectMapper.readTree(res.getResponse().getContentAsString()).get("content");
            for (JsonNode item : content) {
                Long currentId = item.get("id").asLong();
                assertTrue(currentId < lastId, "Thứ tự sắp xếp giảm dần phải đảm bảo currentId < lastId");
                lastId = currentId;
                allIds.add(currentId);
                uniqueIds.add(currentId);
            }
        }

        assertEquals(allIds.size(), uniqueIds.size(), "Không được có bản ghi lặp khi sắp xếp giảm dần");
    }

    @Test
    @DisplayName("3. Kiểm tra Giá trị biên: page âm, size vượt ngưỡng tối đa, và trang vượt tổng số trang")
    void testPaginationBoundaryConditions() throws Exception {
        // Trang vượt quá tổng số trang (mst_national_road chỉ có 169 bản ghi ~ 9 trang với size=20)
        mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .param("page", "999")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(999)))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(169)))
                .andExpect(jsonPath("$.totalPages", is(9)));

        // Kích thước trang hợp lệ tối đa
        mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .param("page", "0")
                        .param("size", "100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(100)))
                .andExpect(jsonPath("$.content", hasSize(100)));
    }
}
