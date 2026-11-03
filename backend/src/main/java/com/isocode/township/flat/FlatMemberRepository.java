package com.isocode.township.flat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlatMemberRepository extends JpaRepository<FlatMember, Long> {

    List<FlatMember> findByFlatIdAndActiveTrue(Long flatId);

    List<FlatMember> findByUserIdAndActiveTrue(Long userId);
}
