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
    TOP_NOTES("TOP_NOTES", "multi_select") {
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> formatValue(Object value) {
            List<String> notes = parseNotesList(value);
            return formatMultiSelect(notes);
        }
    },
    MIDDLE_NOTES("MIDDLE_NOTES", "multi_select") {
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> formatValue(Object value) {
            List<String> notes = parseNotesList(value);
            return formatMultiSelect(notes);
        }
    },
    BASE_NOTES("BASE_NOTES", "multi_select") {
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> formatValue(Object value) {
            List<String> notes = parseNotesList(value);
            return formatMultiSelect(notes);
        }
    },
    NOTES("NOTES", "multi_select") { // General 노트용
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> formatValue(Object value) {
            List<String> notes = parseNotesList(value);
            return formatMultiSelect(notes);
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

    protected static List<String> parseNotesList(Object value) {
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of(); // 타입이 안 맞거나 비어있으면 안전하게 빈 리스트 반환
    }

    protected static Map<String, Object> formatMultiSelect(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return Map.of("multi_select", List.of());
        }
        return Map.of("multi_select", notes.stream()
                .map(note -> Map.of("name", note))
                .toList());
    }
}