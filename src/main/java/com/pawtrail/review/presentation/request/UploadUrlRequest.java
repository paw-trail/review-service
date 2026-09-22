package com.pawtrail.review.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UploadUrlRequest(
    @NotBlank
    @Size(max = 255)
    String fileName,
    @NotBlank
    @Pattern(regexp = "image/(jpeg|png)")
    String contentType
) {
}
