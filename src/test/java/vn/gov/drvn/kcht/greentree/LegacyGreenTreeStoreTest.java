package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.gov.drvn.kcht.dto.AssetCreateUpdateDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.repository.AssetGeometryRepository;
import vn.gov.drvn.kcht.repository.AssetRecordRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyGreenTreeStoreTest {

    @Test
    void updateWithoutCoordinateFieldsPreservesExistingGeometry() {
        Fixture fixture = fixture(Map.of("species_name", "Sao den"));

        fixture.store.update("1", fixture.dto, admin());

        verify(fixture.geometryRepository, never()).deleteByAssetId(any());
        verify(fixture.geometryRepository, never()).upsertPointGeometry(any(), any(), any());
    }

    @Test
    void explicitNullCoordinateClearsExistingGeometry() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("longitude", null);
        Fixture fixture = fixture(patch);

        fixture.store.update("1", fixture.dto, admin());

        verify(fixture.geometryRepository).deleteByAssetId(1L);
    }

    private Fixture fixture(Map<String, Object> attributes) {
        AssetRecordRepository assetRepository = mock(AssetRecordRepository.class);
        AssetGeometryRepository geometryRepository = mock(AssetGeometryRepository.class);
        AssetRecordEntity entity = new AssetRecordEntity();
        entity.setId(1L);
        entity.setRecordId("TREE-1");
        entity.setDatasetCode("green_trees");
        entity.setAssetType("TREE");
        entity.setName("Cay sao den");
        entity.setAttributes("{}");
        entity.setIsDeleted(false);
        entity.setVersion(3);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(assetRepository.saveAndFlush(any(AssetRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AssetCreateUpdateDto dto = new AssetCreateUpdateDto(
                "Cay sao den", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, attributes, 3
        );
        LegacyGreenTreeStore store = new LegacyGreenTreeStore(
                assetRepository, geometryRepository, mock(JdbcTemplate.class), new ObjectMapper());
        return new Fixture(store, geometryRepository, dto);
    }

    private GreenTreeAccessService.AccessScope admin() {
        return new GreenTreeAccessService.AccessScope("ROLE_ADMIN", 1L, null, "admin");
    }

    private record Fixture(LegacyGreenTreeStore store,
                           AssetGeometryRepository geometryRepository,
                           AssetCreateUpdateDto dto) {
    }
}
