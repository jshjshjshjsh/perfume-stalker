package com.grove.perfumestalker.dto;

import com.grove.perfumestalker.weather.WeatherService;

public record UsageLogCreateCommand(
        String masterPageId,
        WeatherService.WeatherData weather,
        String customDate
) {}