package io.casehub.persistence.hibernate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter: Map&lt;String, Object&gt; ↔ JSON TEXT column.
 * Used for CaseFile workspace items and Task context.
 *
 * <p><strong>Type limitation:</strong> Jackson deserializes JSON numbers as
 * {@code Integer}/{@code Long}/{@code Double} and complex objects as
 * {@code LinkedHashMap}. Custom POJOs stored in the workspace will NOT
 * round-trip correctly — only JSON-primitive-compatible types are supported:
 * {@code String}, {@code Number}, {@code Boolean}, {@code Map}, {@code List}.
 */
@Converter
public class ObjectMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize object map to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || "{}".equals(dbData)) return new HashMap<>();
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize JSON to object map: " + dbData, e);
        }
    }
}
