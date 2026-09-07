package com.grove.perfumestalker.enums;

public enum SensoryClimate {
    COLD("15도 미만 (쌀쌀하고 향이 덜 퍼지는 날씨)"),
    WARM_BREEZY("15~24도, 습도 60% 미만 (쾌적하고 발향이 좋은 날씨)"),
    WARM_HUMID("15~24도, 습도 60% 이상 (비 오거나 습한 날씨)"),
    HOT("25도 이상 (덥고 무거운 향이 답답한 날씨)");

    private final String description;
    SensoryClimate(String description) { this.description = description; }

    public static SensoryClimate from(double temperature, double humidity) {
        if (temperature < 15.0) return COLD;
        if (temperature >= 25.0) return HOT;
        if (humidity < 60.0) return WARM_BREEZY;
        return WARM_HUMID;
    }
}