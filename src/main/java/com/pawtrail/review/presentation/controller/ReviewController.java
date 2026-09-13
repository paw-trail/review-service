package com.pawtrail.review.presentation.controller;


import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.review.application.dto.output.ReviewDetailOutput;
import com.pawtrail.review.application.service.ReviewService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/places/{placeId}/reviews")
    public CommonApiResponse<PageResponse<ReviewDetailOutput>> findByPlace(
        @PathVariable UUID placeId,
        @CurrentUser CustomUserPrincipal principal,
        @RequestParam(defaultValue = "0") @PositiveOrZero int page,
        @RequestParam(defaultValue = "10") @Positive int size
    ) {
        return CommonApiResponse.success(reviewService.findByPlace(
            principal.accountId(),
            principal.role(),
            placeId,
            page,
            size
        ));
    }
}
