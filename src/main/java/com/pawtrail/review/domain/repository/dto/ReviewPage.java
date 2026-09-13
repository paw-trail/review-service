package com.pawtrail.review.domain.repository.dto;

import java.util.List;

public record ReviewPage<T>(
    List<T> content,
    int number,
    int size,
    long totalElements,
    int totalPages
) {
    public ReviewPage {
        content = List.copyOf(content);
    }
}
