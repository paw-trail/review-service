package com.pawtrail.review.infrastructure.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMaximumS3PresignedUrlLifetime() {
        StorageProperties properties = new StorageProperties(
            "review-images",
            "ap-northeast-2",
            604_800
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsPresignedUrlLifetimeOverSevenDays() {
        StorageProperties properties = new StorageProperties(
            "review-images",
            "ap-northeast-2",
            604_801
        );

        assertThat(validator.validate(properties))
            .anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("uploadExpiresSeconds"));
    }
}
