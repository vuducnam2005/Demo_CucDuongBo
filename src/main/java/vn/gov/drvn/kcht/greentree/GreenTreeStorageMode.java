package vn.gov.drvn.kcht.greentree;

public enum GreenTreeStorageMode {
    LEGACY,
    SHADOW,
    NEW,
    FINAL;

    public boolean readsNewTable() {
        return this == NEW || this == FINAL;
    }

    public boolean writesLegacyTable() {
        return this != FINAL;
    }

    public boolean writesNewTable() {
        return this != LEGACY;
    }
}

