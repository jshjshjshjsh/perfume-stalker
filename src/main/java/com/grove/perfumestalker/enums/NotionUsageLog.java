package com.grove.perfumestalker.enums;

public enum NotionUsageLog {

    LOG_ID("NAME", "title"),
    PERFUME("PERFUME", "relation"),
    DATE("DATE", "date"),
    TEMPERATURE("TEMPERATURE", "number"),
    HUMIDITY("HUMIDITY", "number"),
    RATE("RATE", "number"),
    COMMENT("COMMENT", "text");

    private final String columnName;
    private final String propertyType;

    NotionUsageLog(String columnName, String propertyType) {
        this.columnName = columnName;
        this.propertyType = propertyType;
    }

    public String getColumnName() { return columnName; }
    public String getPropertyType() { return propertyType; }
}
