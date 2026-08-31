package com.grove.perfumestalker.dto;

public record WishlistRequest(
        String name,
        String brand,
        String imageUrl,
        String url
) {}