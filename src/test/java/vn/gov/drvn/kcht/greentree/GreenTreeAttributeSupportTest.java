package vn.gov.drvn.kcht.greentree;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenTreeAttributeSupportTest {

    @Test
    void explicitNullCoordinateClearsTheWholePairWithoutDroppingOtherAttributes() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("longitude", 108.25);
        existing.put("latitude", 13.75);
        existing.put("species_name", "Sao đen");

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("longitude", null);
        Map<String, Object> merged = GreenTreeAttributeSupport.merge(existing, patch);

        assertEquals(null, merged.get("longitude"));
        assertEquals(null, merged.get("latitude"));
        assertEquals("Sao đen", merged.get("species_name"));
    }

    @Test
    void coordinatesMustBeCompleteAndWithinWgs84Bounds() {
        assertThrows(RuntimeException.class,
                () -> GreenTreeAttributeSupport.coordinates(Map.of("longitude", 108.25)));
        assertThrows(RuntimeException.class,
                () -> GreenTreeAttributeSupport.coordinates(Map.of("longitude", 181, "latitude", 13)));

        GreenTreeAttributeSupport.Coordinates value = GreenTreeAttributeSupport.coordinates(
                Map.of("longitude", "108.25", "latitude", "13.75"));
        assertEquals(new BigDecimal("108.25"), value.longitude());
        assertEquals(new BigDecimal("13.75"), value.latitude());
    }

    @Test
    void onlyAnExplicitCoordinatePatchMayChangeExistingGeometry() {
        assertFalse(GreenTreeAttributeSupport.hasCoordinateIntent(Map.of("species_name", "Sao den")));

        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("longitude", null);
        assertTrue(GreenTreeAttributeSupport.hasCoordinateIntent(clear));
    }
}
