package vn.gov.drvn.kcht.greentree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GreenTreeStoragePropertiesTest {

    @Test
    void parsesEverySupportedModeCaseInsensitively() {
        assertEquals(GreenTreeStorageMode.LEGACY, new GreenTreeStorageProperties("legacy").getMode());
        assertEquals(GreenTreeStorageMode.SHADOW, new GreenTreeStorageProperties(" SHADOW ").getMode());
        assertEquals(GreenTreeStorageMode.NEW, new GreenTreeStorageProperties("new").getMode());
        assertEquals(GreenTreeStorageMode.FINAL, new GreenTreeStorageProperties("FINAL").getMode());
    }

    @Test
    void invalidModeFailsFastWithActionableMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new GreenTreeStorageProperties("dual"));
        assertTrue(error.getMessage().contains("LEGACY, SHADOW, NEW, FINAL"));
        assertTrue(error.getMessage().contains("dual"));
    }

    private void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
