package com.grove.perfumestalker.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum NotionWishlist {

    NAME("NAME", "title") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("text", Map.of("content", String.valueOf(value)))));
        }
    },
    BRAND("BRAND", "select") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), Map.of("name", String.valueOf(value)));
        }
    },
    IMAGE_URL("IMAGE_URL", "url") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), String.valueOf(value));
        }
    },
    URL("URL", "url") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), String.valueOf(value));
        }
    },
    USER("USER", "relation") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("id", String.valueOf(value))));
        }
    },
    ORDER_INDEX("ORDER_INDEX", "number") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), Double.parseDouble(String.valueOf(value)));
        }
    },
    DATE("DATE", "date") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of("date", Map.of("start", String.valueOf(value)));
        }
    },
    TOP_NOTES("TOP_NOTES", "multi_select") {
        @Override
        public Map<String, Object> formatValue(Object value) { return formatMultiSelect(parseNotesList(value)); }
    },
    MIDDLE_NOTES("MIDDLE_NOTES", "multi_select") {
        @Override
        public Map<String, Object> formatValue(Object value) { return formatMultiSelect(parseNotesList(value)); }
    },
    BASE_NOTES("BASE_NOTES", "multi_select") {
        @Override
        public Map<String, Object> formatValue(Object value) { return formatMultiSelect(parseNotesList(value)); }
    },
    NOTES("NOTES", "multi_select") {
        @Override
        public Map<String, Object> formatValue(Object value) { return formatMultiSelect(parseNotesList(value)); }
    };

    private final String columnName;
    private final String propertyType;

    public abstract Map<String, Object> formatValue(Object value);

    @SuppressWarnings("unchecked")
    private static List<String> parseNotesList(Object value) {
        if (value instanceof List) return (List<String>) value;
        return List.of();
    }

    private static Map<String, Object> formatMultiSelect(List<String> notes) {
        if (notes == null || notes.isEmpty()) return Map.of("multi_select", List.of());
        return Map.of("multi_select", notes.stream().map(note -> Map.of("name", note)).toList());
    }
}