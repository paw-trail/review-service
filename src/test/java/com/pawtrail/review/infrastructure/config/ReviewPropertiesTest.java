package com.pawtrail.review.infrastructure.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEmptyTags() {
        ReviewProperties properties = new ReviewProperties(List.of());

        assertThat(validator.validate(properties))
            .anyMatch(violation -> violation.getPropertyPath().toString().equals("tags"));
    }

    @Test
    void rejectsBlankTag() {
        ReviewProperties properties = new ReviewProperties(List.of("주차 편함", " "));

        assertThat(validator.validate(properties))
            .anyMatch(violation -> violation.getPropertyPath().toString().contains("tags[1]"));
    }

    @Test
    void keepsTagsAsImmutableSnapshot() {
        List<String> source = new ArrayList<>(List.of("주차 편함"));

        ReviewProperties properties = new ReviewProperties(source);
        source.add("야외석 넓음");

        assertThat(properties.tags()).containsExactly("주차 편함");
        assertThatThrownBy(() -> properties.tags().add("음수대 제공완료"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
