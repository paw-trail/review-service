package com.pawtrail.review.domain.provider;

import com.pawtrail.review.domain.provider.dto.UserSummary;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserProvider {

    Map<UUID, UserSummary> getUsers(Collection<UUID> accountIds);
}
