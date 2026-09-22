package com.pawtrail.review.domain.provider;

import com.pawtrail.review.domain.provider.dto.PlaceSummary;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PlaceProvider {

    Map<UUID, PlaceSummary> getPlaces(Collection<UUID> placeIds);
}
