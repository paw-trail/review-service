package com.pawtrail.review.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.review")
public record ReviewProperties(
    @NotEmpty List<@NotBlank String> tags
) {
    public ReviewProperties {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
