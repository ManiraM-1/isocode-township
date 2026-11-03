package com.isocode.township.common;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxMessageRepository repository;
    private final Clock clock;

    public OutboxService(OutboxMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void send(Long toUserId, MessageChannel channel, String body) {
        repository.save(new OutboxMessage(toUserId, channel, body, clock.instant()));
    }
}
