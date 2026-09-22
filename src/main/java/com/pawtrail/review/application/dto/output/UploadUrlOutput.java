package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.provider.dto.UploadTarget;

public record UploadUrlOutput(
    String uploadUrl,
    String fileUrl,
    long expiresIn
) {
    public static UploadUrlOutput from(UploadTarget target) {
        return new UploadUrlOutput(
            target.uploadUrl(),
            target.fileUrl(),
            target.expiresIn()
        );
    }
}
