package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.infrastructure.provider.internal.dto.InternalApiResponse;
import com.pawtrail.review.infrastructure.provider.internal.dto.UserInternalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserProviderImpl implements UserProvider {

    // 한 번에 물어보는 개수입니다.
    //
    // user-service 의 조회 API 가 한 번에 100개까지만 받습니다.
    // 장소 하나에 후기 100개가 달려도 작성자가 전부 다르면 100명을 물어야 하므로
    // 쪽 크기 상한과 같은 값으로 둡니다.
    private static final int BATCH_SIZE = 100;

    private final RestClient internalRestClient;

    public UserProviderImpl(
        @Qualifier("internalRestClientBuilder") RestClient.Builder builder
    ) {
        this.internalRestClient = builder.baseUrl("lb://user-service").build();
    }

    @Override
    public Map<UUID, UserSummary> getUsers(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> targets = accountIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

        if (targets.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserSummary> result = new LinkedHashMap<>();

        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, targets.size());
            Map<UUID, UserSummary> chunk = requestChunk(targets.subList(from, to));

            // 한 묶음이라도 실패하면 작성자가 반쯤 채워진 목록이 나가므로 전부 비웁니다.
            if (chunk == null) {
                return Map.of();
            }

            result.putAll(chunk);
        }

        return Collections.unmodifiableMap(result);
    }

    // 실패를 예외로 올리지 않고 null 로 돌려주는 자리입니다.
    //
    // 작성자 이름이 없다고 후기를 못 읽을 이유는 없습니다.
    // 빠진 자리는 부르는 쪽이 "알 수 없음" 으로 채우고 목록은 그대로 나갑니다.
    private Map<UUID, UserSummary> requestChunk(List<UUID> accountIds) {
        String ids = accountIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining(","));

        try {
            InternalApiResponse<List<UserInternalResponse>> response = internalRestClient.get()
                .uri("/internal/users?ids={ids}", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

            if (response == null
                || !"SUCCESS".equals(response.code())
                || response.data() == null) {
                log.warn("사용자 응답이 비어 있습니다: 요청 {}건", accountIds.size());
                return null;
            }

            Map<UUID, UserSummary> chunk = new LinkedHashMap<>();
            for (UserInternalResponse user : response.data()) {
                if (user == null || user.accountId() == null) {
                    continue;
                }
                chunk.putIfAbsent(user.accountId(), new UserSummary(
                    user.accountId(),
                    user.nickname(),
                    user.profileImageUrl()
                ));
            }

            return chunk;
        } catch (Exception exception) {
            log.warn(
                "사용자를 받아오지 못했습니다: 요청 {}건, reason={}",
                accountIds.size(),
                exception.getMessage()
            );
            return null;
        }
    }
}
