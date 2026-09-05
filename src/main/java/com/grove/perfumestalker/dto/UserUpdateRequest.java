package com.grove.perfumestalker.dto;

public record UserUpdateRequest(String name, String defaultLocation, Boolean notiEnabled, String password) {

}