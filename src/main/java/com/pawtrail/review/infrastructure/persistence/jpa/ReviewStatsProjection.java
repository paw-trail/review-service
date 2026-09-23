package com.pawtrail.review.infrastructure.persistence.jpa;

import java.util.UUID;

public interface ReviewStatsProjection {

    UUID getPlaceId();

    Double getRatingAverage();

    long getReviewCount();
}
