package com.pawtrail.review.infrastructure.message.kafka.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// account.withdrawn 의 payload 입니다.
//
// 쓰는 칸은 accountId 뿐입니다.
// 저쪽이 칸을 늘려도 이 서비스가 깨지지 않도록 모르는 칸은 흘려보냅니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountWithdrawnMessage(UUID accountId) {
}
