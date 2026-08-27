package com.grove.perfumestalker.dto;

public record UserAccountCommand(
        String userId,
        String rawPassword,
        String name,
        String defaultLocation,
        Boolean notiEnabled
) {}