package com.isocode.township.flat;

import com.isocode.township.auth.SessionUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FlatAccessGuard {

    private final FlatMemberRepository memberRepository;

    public FlatAccessGuard(FlatMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public void requireMember(SessionUser user, Long flatId) {
        if (!memberRepository.existsByFlatIdAndUserIdAndActiveTrueAndUserActiveTrue(flatId, user.userId())) {
            throw new AccessDeniedException("Not a member of this flat");
        }
    }

    @Transactional(readOnly = true)
    public void requireMemberOrStaff(SessionUser user, Long flatId) {
        if (!user.isStaff()) {
            requireMember(user, flatId);
        }
    }
}
