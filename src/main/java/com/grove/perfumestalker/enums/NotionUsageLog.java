package com.grove.perfumestalker.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum NotionUsageLog {

    LOG_ID("LOG_ID", "title") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("text", Map.of("content", String.valueOf(value)))));
        }
    },
    PERFUME("PERFUME", "relation") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("id", String.valueOf(value))));
        }
    },
    DATE("DATE", "date") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), Map.of("start", String.valueOf(value)));
        }
    },
    WEATHER("WEATHER", "select") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), Map.of("name", String.valueOf(value)));
        }
    },
    TEMPERATURE("TEMPERATURE", "number") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), value);
        }
    },
    HUMIDITY("HUMIDITY", "number") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), value);
        }
    },
    RATE("RATE", "number") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), value);
        }
    },
    COMMENT("COMMENT", "rich_text") {
        @Override
        public Map<String, Object> formatValue(Object value) {
            return Map.of(getPropertyType(), List.of(Map.of("text", Map.of("content", String.valueOf(value)))));
        }
    };

    private final String columnName;
    private final String propertyType;

    public abstract Map<String, Object> formatValue(Object value);
}