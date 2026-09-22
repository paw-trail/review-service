package com.pawtrail.review.domain.provider;

import com.pawtrail.review.domain.provider.dto.UploadTarget;

import java.util.UUID;

public interface StorageProvider {
    UploadTarget createReviewUpload(UUID accountId, String fileName, String contentType);

}
