package com.isocode.township.common;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByToUserIdOrderByIdDesc(Long toUserId);
}
