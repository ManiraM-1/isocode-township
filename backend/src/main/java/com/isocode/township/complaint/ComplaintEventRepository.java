package com.isocode.township.complaint;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface ComplaintEventRepository extends Repository<ComplaintEvent, Long> {

    <S extends ComplaintEvent> S save(S event);

    List<ComplaintEvent> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);
}
