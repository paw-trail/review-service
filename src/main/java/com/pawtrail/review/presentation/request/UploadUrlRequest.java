package com.pawtrail.review.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UploadUrlRequest(
    @NotBlank String fileName,
    @NotBlank
    @Pattern(regexp = "image/(jpeg|png)")
    String contentType
) {
}
