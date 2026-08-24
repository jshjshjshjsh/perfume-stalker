package com.grove.perfumestalker.enums;

public enum NotionPerfumeMaster {

    NAME("NAME", "title"),
    UID("UID", "text"),
    BRAND("BRAND", "select"),
    NOTES("NOTES", "multi_select"),
    URL("URL", "url"),
    IMAGE("IMAGE", "files");

    private final String columnName;
    private final String propertyType;

    NotionPerfumeMaster(String columnName, String propertyType) {
        this.columnName = columnName;
        this.propertyType = propertyType;
    }

    public String getColumnName() { return columnName; }
    public String getPropertyType() { return propertyType; }
}
