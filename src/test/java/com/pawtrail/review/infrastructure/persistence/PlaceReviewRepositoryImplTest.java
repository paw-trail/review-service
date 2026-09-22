package com.pawtrail.review.infrastructure.persistence;

import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.infrastructure.persistence.jpa.PlaceReviewJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceReviewRepositoryImplTest {

    @Mock
    private PlaceReviewJpaRepository jpaRepository;

    @InjectMocks
    private PlaceReviewRepositoryImpl repository;

    @Test
    void alwaysUsesReviewIdAsTheFinalSortCondition() {
        UUID accountId = UUID.randomUUID();
        when(jpaRepository.findByAccountIdAndDeletedAtIsNull(eq(accountId), any(Pageable.class)))
            .thenReturn(Page.empty());

        repository.findActiveByAccountId(accountId, ReviewSort.RECENT, 0, 200);
        repository.findActiveByAccountId(accountId, ReviewSort.OLDEST, 0, 200);
        repository.findActiveByAccountId(accountId, ReviewSort.RATING_DESC, 0, 200);
        repository.findActiveByAccountId(accountId, ReviewSort.RATING_ASC, 0, 200);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(jpaRepository, times(4))
            .findByAccountIdAndDeletedAtIsNull(eq(accountId), captor.capture());

        List<Sort.Direction> expectedDirections = List.of(
            Sort.Direction.DESC,
            Sort.Direction.ASC,
            Sort.Direction.DESC,
            Sort.Direction.DESC
        );
        List<Pageable> pageables = captor.getAllValues();
        for (int index = 0; index < pageables.size(); index++) {
            Pageable pageable = pageables.get(index);
            List<Sort.Order> orders = pageable.getSort().stream().toList();
            Sort.Order finalOrder = orders.getLast();

            assertEquals(200, pageable.getPageSize());
            assertEquals("id", finalOrder.getProperty());
            assertEquals(expectedDirections.get(index), finalOrder.getDirection());
        }
    }
}
