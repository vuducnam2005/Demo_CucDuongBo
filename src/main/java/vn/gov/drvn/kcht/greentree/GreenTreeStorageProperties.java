package vn.gov.drvn.kcht.greentree;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class GreenTreeStorageProperties {

    private final GreenTreeStorageMode mode;

    public GreenTreeStorageProperties(
            @Value("${kcht.green-tree.storage-mode:${GREEN_TREE_STORAGE_MODE:LEGACY}}") String configuredMode) {
        try {
            this.mode = GreenTreeStorageMode.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "GREEN_TREE_STORAGE_MODE must be one of LEGACY, SHADOW, NEW, FINAL; received: "
                            + configuredMode,
                    exception
            );
        }
    }

    public GreenTreeStorageMode getMode() {
        return mode;
    }
}

