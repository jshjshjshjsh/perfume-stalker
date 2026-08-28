package com.grove.perfumestalker.dto;

public record UsageLogResponse(
        String pageId,
        String perfumeName,
        String date,
        String weather,
        Double temp,
        Double humidity,
        String imageUrl
) {}