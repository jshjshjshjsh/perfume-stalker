package com.grove.perfumestalker.dto;

import java.util.List;
import java.util.Map;

public record WishlistRequest(
        String name,
        String brand,
        String imageUrl,
        String url,
        String date,
        Map<String, List<String>> notes
) {}