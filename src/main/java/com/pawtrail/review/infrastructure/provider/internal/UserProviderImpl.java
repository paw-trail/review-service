package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.infrastructure.provider.internal.dto.InternalApiResponse;
import com.pawtrail.review.infrastructure.provider.internal.dto.UserInternalResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class UserProviderImpl implements UserProvider {

    private final RestClient internalRestClient;

    public UserProviderImpl(
        @Qualifier("internalRestClientBuilder") RestClient.Builder builder
    ) {
        this.internalRestClient = builder.baseUrl("lb://user-service").build();
    }

    @Override
    public Map<UUID, UserSummary> getUsers(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> distinctAccountIds = accountIds.stream()
            .distinct()
            .sorted()
            .toList();

        String ids = distinctAccountIds.stream()
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
                throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
            }

            return response.data().stream()
                .filter(user -> user.accountId() != null)
                .map(user -> new UserSummary(
                    user.accountId(),
                    user.nickname(),
                    user.profileImageUrl()
                ))
                .collect(Collectors.toUnmodifiableMap(
                    UserSummary::accountId,
                    Function.identity(),
                    (first, ignored) -> first
                ));
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }
    }
}
