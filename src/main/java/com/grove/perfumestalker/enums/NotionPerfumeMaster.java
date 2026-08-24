package com.grove.perfumestalker.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum NotionPerfumeMaster {

    NAME("NAME", "title") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("text", Map.of("content", String.valueOf(value)))));
        }
    },
    UID("UID", "rich_text") {
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
    NOTES("NOTES", "multi_select") {
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> formatValue(Object value) {
            List<String> notes = (List<String>) value;
            return Map.of(getPropertyType(), notes.stream()
                    .map(note -> Map.of("name", note))
                    .toList());
        }
    },
    URL("URL", "url") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), String.valueOf(value));
        }
    },
    IMAGE("IMAGE", "files") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(
                    Map.of("name", "향수 썸네일", "type", "external", "external", Map.of("url", String.valueOf(value)))
            ));
        }
    };

    private final String columnName;
    private final String propertyType;

    public abstract Map<String, Object> formatValue(Object value);
}