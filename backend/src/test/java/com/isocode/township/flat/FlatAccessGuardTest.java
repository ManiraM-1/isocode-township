package com.isocode.township.flat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.IntegrationTest;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.DemoDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class FlatAccessGuardTest {

    @Autowired
    private FlatAccessGuard guard;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SessionUser swati;
    private SessionUser anjali;
    private Long swatiFlatId;
    private Long neerajFlatId;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swati = session("+919000000002", Role.RESIDENT);
        anjali = session("+919000000101", Role.HELPDESK);
        swatiFlatId = flatRepository.findByCode("T12-0408").orElseThrow().getId();
        neerajFlatId = flatRepository.findByCode("T07-1203").orElseThrow().getId();
    }

    @Test
    void residentPassesForTheirOwnFlat() {
        assertThatCode(() -> guard.requireMember(swati, swatiFlatId)).doesNotThrowAnyException();
    }

    @Test
    void residentIsForbiddenOnAnotherFlat() {
        assertThatThrownBy(() -> guard.requireMember(swati, neerajFlatId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.requireMemberOrStaff(swati, neerajFlatId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownFlatIsForbiddenNotNotFound() {
        assertThatThrownBy(() -> guard.requireMember(swati, -1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removedMemberLosesAccess() {
        jdbcTemplate.update("UPDATE flat_member SET active = FALSE WHERE flat_id = ? AND user_id = ?",
                swatiFlatId, swati.userId());

        assertThatThrownBy(() -> guard.requireMember(swati, swatiFlatId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivatedUserLosesAccess() {
        jdbcTemplate.update("UPDATE app_user SET active = FALSE WHERE id = ?", swati.userId());

        assertThatThrownBy(() -> guard.requireMember(swati, swatiFlatId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staffAreNotMembersButPassTheStaffVariant() {
        assertThatThrownBy(() -> guard.requireMember(anjali, swatiFlatId)).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> guard.requireMemberOrStaff(anjali, swatiFlatId)).doesNotThrowAnyException();
    }

    private SessionUser session(String phone, Role role) {
        return new SessionUser(userRepository.findByPhone(phone).orElseThrow().getId(), role);
    }
}
