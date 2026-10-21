package com.sonetopia.model;

public record AudioMetadata(
    String title,
    String author,
    String album,
    Short year,
    Integer duration
) {}
