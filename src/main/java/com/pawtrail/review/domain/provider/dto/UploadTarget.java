package com.pawtrail.review.domain.provider.dto;

public record UploadTarget(String uploadUrl, String fileUrl, long expiresIn) {
}
