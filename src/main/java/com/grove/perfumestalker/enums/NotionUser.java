package com.grove.perfumestalker.enums;

public enum NotionUser {

    USER_ID("USER_ID", "title"),
    NAME("NAME", "text"),
    DEFAULT_LOCATION("DEFAULT_LOCATION", "text"),
    NOTI_ENABLED("NOTI_ENABLED", "checkbox");

    private final String columnName;
    private final String propertyType;

    NotionUser(String columnName, String propertyType) {
        this.columnName = columnName;
        this.propertyType = propertyType;
    }

    public String getColumnName() { return columnName; }
    public String getPropertyType() { return propertyType; }

}
