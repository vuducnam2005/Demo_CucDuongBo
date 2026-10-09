package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VroadSurveyControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final VroadSurveyController controller = new VroadSurveyController(database);

    @Test
    void defectDeepLinkRejectsInvalidIdentifiersBeforeAnyQuery() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.defectPoint(principal(1L, "ROLE_ADMIN", null), 0)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.defectPoint(principal(2L, "ROLE_MANAGER", null), 1)).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void defectDeepLinkNeverFallsBackToAnUnrelatedPoint() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.defectPoint(principal(2L, "ROLE_MANAGER", "kqldb_2"), 999999)).getStatusCode().value());
    }

    @Test
    void viewerAndUnscopedManagerCannotReadSurveyData() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.near(
                principal(1L, "ROLE_VIEWER", null), 109.2, 13.4, 200, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.points(
                principal(2L, "ROLE_MANAGER", null), 109, 13, 110, 14, 100)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.mapAssets(
                principal(1L, "ROLE_VIEWER", null), 109, 13, 110, 14, 100)).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void managerCannotGuessOutsideBranchDefect() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(0L);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.evidence(
                principal(2L, "ROLE_MANAGER", "kqldb_2"), 42L)).getStatusCode().value());
    }

    @Test
    void assetPointRejectsOutOfScopeOrInvalidIdentifiers() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.assetPoint(principal(1L, "ROLE_VIEWER", null), 42L, true)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.assetPoint(principal(1L, "ROLE_ADMIN", null), 0L, true)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.assetPoint(principal(2L, "ROLE_MANAGER", "kqldb_2"), 42L, true))
                .getStatusCode().value());
    }

    @Test
    void assetPointUsesBranchScopeAndReturnsLinkedPhoto() {
        VroadSurveyController.AssetPoint asset = new VroadSurveyController.AssetPoint(17L, "MAST-17",
                109.2, 13.4, "QL1", "Km 10+000", "Chiều phải", "Mặt đường", "Đường nhựa",
                "Đang sử dụng", "Phải", "2026-09-01", "https://platform.vroad.vn/photo/17", null);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetPoint>>any(),
                eq(17L), eq("ROLE_MANAGER"), eq(2L))).thenReturn(List.of(asset));

        VroadSurveyController.AssetPoint selected = controller.assetPoint(
                principal(2L, "ROLE_MANAGER", "kqldb_1"), 17L, false);

        assertEquals("MAST-17", selected.recordKey());
        assertEquals("QL1", selected.routeName());
        assertEquals("Chiều phải", selected.routeSide());
        assertEquals("https://platform.vroad.vn/photo/17", selected.sourceImageUrl());
        assertEquals(null, selected.roadCatalog());
        assertTrue(selected.relatedDefects().isEmpty());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetPoint>>any(),
                eq(17L), eq("ROLE_MANAGER"), eq(2L));
        assertTrue(sql.getValue().contains("vroad_record_visible(r.id, ?)"));
        assertTrue(sql.getValue().contains("vroad_image_reference"));
        verifyNoMoreInteractions(database);
    }

    @Test
    void assetPointIncludesOnlyScopedDefectsAtSameChainageAndSide() {
        VroadSurveyController.AssetPoint asset = new VroadSurveyController.AssetPoint(17L, "MAST-17",
                109.2, 13.4, "DRVN-1 QL1 Km 10-11", "Km 10+000", "RHS", "Biển báo",
                "Biển báo giao thông", "Đang sử dụng", "LHS", "2026-09-01", null, null);
        VroadSurveyController.DefectPoint defect = new VroadSurveyController.DefectPoint(88L, "DEF-88",
                109.2, 13.4, null, null, null, null, false, "DRVN-1 QL1 Km 10-11", "RHS",
                "Km 10+000", "LHS", "Ổ gà", "1.5", "2026-10-01");
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetPoint>>any(),
                eq(17L), eq("ROLE_MANAGER"), eq(2L))).thenReturn(List.of(asset));
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(asset.routeName()), eq(asset.chainage()),
                eq(asset.routeSide()), eq(asset.assetSide()), eq(asset.longitude()), eq(asset.latitude()),
                eq(20), eq(asset.longitude()), eq(asset.latitude()), eq(31)))
                .thenReturn(java.util.Collections.nCopies(31, defect));

        VroadSurveyController.AssetPoint selected = controller.assetPoint(
                principal(2L, "ROLE_MANAGER", "kqldb_1"), 17L, true);

        assertEquals(30, selected.relatedDefects().size());
        assertEquals(defect, selected.relatedDefects().get(0));
        assertTrue(selected.relatedDefectsTruncated());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(asset.routeName()), eq(asset.chainage()),
                eq(asset.routeSide()), eq(asset.assetSide()), eq(asset.longitude()), eq(asset.latitude()),
                eq(20), eq(asset.longitude()), eq(asset.latitude()), eq(31));
        assertTrue(sql.getValue().contains("vroad_record_visible(r.id, ?)"));
        assertTrue(sql.getValue().contains("p.raw_payload->>'chainage' = ?"));
        assertTrue(sql.getValue().contains("p.raw_payload->>'defect_side' = ?"));
        assertTrue(sql.getValue().contains("ST_DistanceSphere"));
    }

    @Test
    void administratorSeesUnambiguousRoadCatalogButNotUnmatchedSegments() {
        VroadSurveyController.AssetPoint asset = new VroadSurveyController.AssetPoint(17L, "MAST-17",
                109.2, 13.4, "DRVN-1 QL1 Km 10-11", "Km 10+000", "Chiều trái", "Mặt đường", "Đường nhựa",
                "Đang sử dụng", "Phải", "2026-09-01", null, null);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetPoint>>any(),
                eq(17L), eq("ROLE_ADMIN"), eq(1L))).thenReturn(List.of(asset));
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.RoadCatalog>>any(),
                eq("QL.1"))).thenReturn(List.of(new VroadSurveyController.RoadCatalog(
                "national_id_49", "QL.1", "3878.941", null, null)));

        VroadSurveyController.AssetPoint selected = controller.assetPoint(
                principal(1L, "ROLE_ADMIN", null), 17L, false);

        assertEquals("national_id_49", selected.roadCatalog().recordKey());
        assertEquals("3878.941", selected.roadCatalog().lengthKm());
        assertEquals("Chiều trái", selected.routeSide());
    }

    @Test
    void editorCannotResolveAndInvalidCoordinatesAreRejected() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.resolve(
                principal(1L, "ROLE_EDITOR", "kqldb_1"), 42L, "Đã sửa mặt đường", null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.near(
                principal(2L, "ROLE_ADMIN", null), Double.NaN, 13, 200, null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.points(
                principal(2L, "ROLE_ADMIN", null), 100, 13, 120, 14, 501)).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void oversizedEvidenceIsRejectedBeforeReadingBytes() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq("ROLE_ADMIN"), eq(2L)))
                .thenReturn(1L);
        MockMultipartFile oversized = new MockMultipartFile("evidence", new byte[5 * 1024 * 1024 + 1]);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.resolve(
                principal(2L, "ROLE_ADMIN", null), 42L, "Đã hoàn thành sửa chữa", oversized)).getStatusCode().value());
        verify(database, never()).update(anyString());
    }

    @Test
    void duplicateResolutionIsRejectedWithoutAudit() throws Exception {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq(42L), eq("Đã sửa"), eq(2L))).thenReturn(List.of());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> controller.resolve(
                principal(2L, "ROLE_MANAGER", "kqldb_1"), 42L, "Đã sửa", null)).getStatusCode().value());
        verify(database, never()).update(anyString());
    }

    @Test
    void scopedManagerCanCreateCaseAndAuditWithoutChangingRawRecord() throws Exception {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq(42L), eq("Đã sửa đường"), eq(2L))).thenReturn(List.of(42L));
        VroadSurveyController.CaseItem saved = new VroadSurveyController.CaseItem(42L, "DEF-DEMO", "Tuyến thử",
                "Km1", "Nứt mặt đường", "Đã sửa đường", "Quản lý demo", java.time.OffsetDateTime.now(), false);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.CaseItem>>any(),
                eq(42L))).thenReturn(List.of(saved));

        assertEquals(saved, controller.resolve(principal(2L, "ROLE_MANAGER", "kqldb_1"),
                42L, "Đã sửa đường", null));
        org.mockito.ArgumentCaptor<String> update = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).update(update.capture(), eq(2L), eq("demo"), eq("42"), eq(false));
        assertTrue(update.getValue().contains("audit_log"));
    }

    @Test
    void mapQueryUsesRoleAndBranchBeforeReturningCoordinates() {
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(108.0), eq(110.0), eq(13.0), eq(15.0), eq(101)))
                .thenReturn(List.of());
        assertTrue(controller.points(principal(2L, "ROLE_MANAGER", "kqldb_1"),
                108, 13, 110, 15, 100).content().isEmpty());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(108.0), eq(110.0), eq(13.0), eq(15.0), eq(101));
        assertTrue(sql.getValue().contains("vroad_record_visible(r.id, ?)"));
    }

    @Test
    void mapAssetsReturnsOnlyScopedMarkersWithinBounds() {
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetMarker>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(108.0), eq(110.0), eq(13.0), eq(15.0), eq(101)))
                .thenReturn(List.of());
        assertTrue(controller.mapAssets(principal(2L, "ROLE_MANAGER", "kqldb_1"),
                108, 13, 110, 15, 100).content().isEmpty());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.AssetMarker>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(108.0), eq(110.0), eq(13.0), eq(15.0), eq(101));
        assertTrue(sql.getValue().contains("vroad_record_visible(r.id, ?)"));
        assertTrue(sql.getValue().contains("r.dataset_key = 'vroad_assets'"));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.mapAssets(
                principal(2L, "ROLE_ADMIN", null), 100, 13, 140, 15, 500)).getStatusCode().value());
    }

    @Test
    void selectedMarkerIsScopedAndReturnsOnlyThatRecord() {
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(42L), eq(42L), eq(109.2), eq(13.4),
                eq(200), eq(109.2), eq(13.4))).thenReturn(List.of());
        assertTrue(controller.near(principal(2L, "ROLE_MANAGER", "kqldb_1"),
                109.2, 13.4, 200, 42L).defects().isEmpty());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_MANAGER"), eq(2L), eq(42L), eq(42L), eq(109.2), eq(13.4),
                eq(200), eq(109.2), eq(13.4));
        assertTrue(sql.getValue().contains("p.id = ?"));
        assertTrue(sql.getValue().contains("LIMIT 1"));
    }

    @Test
    void selectedDefectIncludesOnlyMatchingNationalRoadSummary() {
        VroadSurveyController.DefectPoint defect = new VroadSurveyController.DefectPoint(42L, "DEF-42",
                109.2, 13.4, null, null, null, null, false,
                "DRVN-1 QL1 Km10-Km11", "INC", "Km10+000", "Phải", "Nứt", null, null);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.DefectPoint>>any(),
                eq("ROLE_ADMIN"), eq(1L), eq(42L), eq(42L), eq(109.2), eq(13.4),
                eq(200), eq(109.2), eq(13.4))).thenReturn(List.of(defect));
        when(database.queryForObject(anyString(), eq(Long.class), eq(defect.routeName()),
                eq("ROLE_ADMIN"), eq(1L), eq(109.2), eq(13.4), eq(200))).thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.RoadCatalog>>any(),
                eq("QL.1"))).thenReturn(List.of(new VroadSurveyController.RoadCatalog(
                "national_id_49", "QL.1", "3878.941", null, null)));

        VroadSurveyController.RoadContext context = controller.near(
                principal(1L, "ROLE_ADMIN", null), 109.2, 13.4, 200, 42L);

        assertEquals(1, context.defects().size());
        assertEquals(42L, context.defects().get(0).recordId());
        assertEquals("national_id_49", context.roadCatalog().recordKey());
    }

    @Test
    void overviewReturnsOnlyScopedCountsAndBreakdowns() {
        when(database.queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<long[]>>any(),
                eq("ROLE_MANAGER"), eq(2L))).thenReturn(new long[]{2, 3, 1});
        when(database.queryForObject(anyString(), eq(Long.class), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.CategoryCount>>any(),
                eq("defect_type"), eq("vroad_defects"), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(List.of(new VroadSurveyController.CategoryCount("Nứt vỡ", 3)));
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<VroadSurveyController.CategoryCount>>any(),
                eq("category"), eq("vroad_assets"), eq("ROLE_MANAGER"), eq(2L)))
                .thenReturn(List.of(new VroadSurveyController.CategoryCount("Mặt đường", 2)));
        VroadSurveyController.SurveyOverview overview = controller.overview(
                principal(2L, "ROLE_MANAGER", "kqldb_1"));
        assertEquals(2, overview.assets());
        assertEquals(3, overview.defects());
        assertEquals(1, overview.resolvedCases());
        assertEquals("Nứt vỡ", overview.defectTypes().get(0).label());
    }

    private UserPrincipal principal(long id, String role, String branchId) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
        return new UserPrincipal(id, "demo", null, null, "Demo", role, role,
                "demo", branchId, true, List.of(), List.of(authority));
    }
}
