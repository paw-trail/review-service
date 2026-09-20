package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.review.domain.provider.ReviewTagProvider;
import com.pawtrail.review.infrastructure.config.ReviewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConfiguredReviewTagProvider implements ReviewTagProvider {

    private final ReviewProperties properties;

    @Override
    public List<String> findAll() {
        return properties.tags();
    }
}
