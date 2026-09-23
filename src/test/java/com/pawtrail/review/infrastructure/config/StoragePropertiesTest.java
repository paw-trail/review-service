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
            604_800,
            604_800,
            20_971_520
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsPresignedUrlLifetimeOverSevenDays() {
        StorageProperties properties = new StorageProperties(
            "review-images",
            "ap-northeast-2",
            604_801,
            604_801,
            20_971_520
        );

        assertThat(validator.validate(properties))
            .anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("uploadExpiresSeconds"))
            .anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("downloadExpiresSeconds"));
    }

    @Test
    void rejectsNonPositiveMaxImageBytes() {
        StorageProperties properties = new StorageProperties(
            "review-images",
            "ap-northeast-2",
            600,
            3_600,
            0
        );

        assertThat(validator.validate(properties))
            .anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("maxImageBytes"));
    }
}
