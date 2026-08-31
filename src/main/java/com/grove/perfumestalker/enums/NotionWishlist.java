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
    URL("URL", "url") { // 형님 요청대로 FRAGRANTICA_URL에서 URL로 팩폭 수정 완료
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
    };

    private final String columnName;
    private final String propertyType;

    public abstract Map<String, Object> formatValue(Object value);
}