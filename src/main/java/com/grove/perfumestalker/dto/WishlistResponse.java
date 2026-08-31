package com.grove.perfumestalker.dto;

public record WishlistResponse(
        String id,
        String name,
        String brand,
        String imageUrl,
        String url
) {}