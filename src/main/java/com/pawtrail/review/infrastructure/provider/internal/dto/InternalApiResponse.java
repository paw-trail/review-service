package com.pawtrail.review.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalApiResponse<T>(
    String code,
    String message,
    T data,
    String traceId
) {
}
