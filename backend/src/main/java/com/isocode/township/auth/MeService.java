package com.isocode.township.auth;

import com.isocode.township.auth.dto.MeResponse;
import com.isocode.township.common.UnauthenticatedException;
import com.isocode.township.flat.FlatMemberRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

    private final AppUserRepository userRepository;
    private final FlatMemberRepository memberRepository;

    public MeService(AppUserRepository userRepository, FlatMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse describe(Long userId) {
        AppUser user = userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthenticatedException("Please sign in again."));
        List<MeResponse.FlatMembership> flats = memberRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(member -> new MeResponse.FlatMembership(
                        member.getFlat().getId(), member.getFlat().getCode(), member.getRelation()))
                .sorted(Comparator.comparing(MeResponse.FlatMembership::code))
                .toList();
        return new MeResponse(user.getId(), user.getFullName(), user.getRole(), user.isDutyManager(), flats);
    }
}
