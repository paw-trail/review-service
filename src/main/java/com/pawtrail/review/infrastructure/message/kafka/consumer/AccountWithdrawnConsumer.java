package com.pawtrail.review.infrastructure.message.kafka.consumer;

import com.pawtrail.common.message.EventEnvelope;
import com.pawtrail.common.message.inbox.InboxProcessor;
import com.pawtrail.review.application.service.AccountWithdrawnService;
import com.pawtrail.review.infrastructure.message.kafka.consumer.dto.AccountWithdrawnMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 탈퇴한 계정의 후기를 치웁니다.
//
// 예외를 잡지 않습니다.
// 여기서 삼키면 실패한 채로 메시지가 처리됐다고 표시되어 다시 시도할 길이 없어집니다.
// 던지면 재시도가 돌고, 끝내 안 되면 DLQ 로 갑니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountWithdrawnConsumer {

    private static final String TOPIC = "account.withdrawn";

    private final InboxProcessor inboxProcessor;
    private final AccountWithdrawnService accountWithdrawnService;

    @KafkaListener(topics = TOPIC)
    public void consume(EventEnvelope<AccountWithdrawnMessage> envelope) {
        AccountWithdrawnMessage message = envelope.data();

        log.info(
            "account.withdrawn 수신: eventId={}, accountId={}",
            envelope.eventId(),
            message.accountId()
        );

        inboxProcessor.processOnce(
            envelope.eventId(),
            TOPIC,
            () -> accountWithdrawnService.withdraw(message.accountId())
        );
    }
}
