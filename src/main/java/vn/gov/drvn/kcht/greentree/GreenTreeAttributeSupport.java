package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.gov.drvn.kcht.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

final class GreenTreeAttributeSupport {

    private GreenTreeAttributeSupport() {
    }

    static Map<String, Object> parseObject(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception exception) {
            throw new BadRequestException("attributes của cây xanh không phải JSON object hợp lệ");
        }
    }

    static String writeObject(ObjectMapper objectMapper, Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (Exception exception) {
            throw new BadRequestException("Không thể serialize attributes cây xanh");
        }
    }

    static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        if (patch == null) return merged;
        boolean clearCoordinates = patch.containsKey("longitude") && patch.get("longitude") == null
                || patch.containsKey("latitude") && patch.get("latitude") == null;
        patch.forEach((key, value) -> {
            if (clearCoordinates && ("longitude".equals(key) || "latitude".equals(key))) return;
            if (value == null) merged.remove(key);
            else merged.put(key, value);
        });
        if (clearCoordinates) {
            // Keep a JSON null tombstone so legacy reads do not fall back to immutable raw coordinates.
            merged.put("longitude", null);
            merged.put("latitude", null);
        }
        return merged;
    }

    static Coordinates coordinates(Map<String, Object> attributes) {
        Object longitudeValue = attributes.get("longitude");
        Object latitudeValue = attributes.get("latitude");
        if (longitudeValue == null && latitudeValue == null) return new Coordinates(null, null);
        if (longitudeValue == null || latitudeValue == null) {
            throw new BadRequestException("Kinh độ và vĩ độ phải được cung cấp cùng nhau");
        }
        BigDecimal longitude = decimal(longitudeValue);
        BigDecimal latitude = decimal(latitudeValue);
        if (longitude == null || latitude == null
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BadRequestException("Tọa độ cây xanh không hợp lệ");
        }
        return new Coordinates(longitude, latitude);
    }

    static boolean hasCoordinateIntent(Map<String, Object> patch) {
        return patch != null && (patch.containsKey("longitude") || patch.containsKey("latitude"));
    }

    static String text(Map<String, Object> primary, Map<String, Object> fallback, String key) {
        Object value = primary == null ? null : primary.get(key);
        if (value == null && fallback != null) value = fallback.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static BigDecimal nonNegativeDecimal(Map<String, Object> primary, Map<String, Object> fallback, String key) {
        Object value = primary == null ? null : primary.get(key);
        if (value == null && fallback != null) value = fallback.get(key);
        BigDecimal decimal = decimal(value);
        return decimal != null && decimal.signum() >= 0 ? decimal : null;
    }

    static Integer integer(Map<String, Object> primary, Map<String, Object> fallback, String key) {
        Object value = primary == null ? null : primary.get(key);
        if (value == null && fallback != null) value = fallback.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValueExact();
        } catch (Exception ignored) {
            return null;
        }
    }

    static BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    record Coordinates(BigDecimal longitude, BigDecimal latitude) {
        boolean present() {
            return longitude != null && latitude != null;
        }
    }
}

