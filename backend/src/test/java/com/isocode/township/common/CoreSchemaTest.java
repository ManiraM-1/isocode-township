package com.isocode.township.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.IntegrationTest;
import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatMember;
import com.isocode.township.flat.FlatMemberRepository;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.flat.MemberRelation;
import com.isocode.township.flat.Tower;
import com.isocode.township.flat.TowerRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CoreSchemaTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-03T04:30:00Z");

    @Autowired
    private SocietySettingsRepository settingsRepository;

    @Autowired
    private TowerRepository towerRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private FlatMemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void settingsRowHoldsTheAgreedDefaults() {
        SocietySettings settings = settingsRepository.current();

        assertThat(settings.getRatePerSqft()).isEqualByComparingTo(new BigDecimal("6.50"));
        assertThat(settings.getDueDay()).isEqualTo(10);
        assertThat(settings.getLateInterestPctPerAnnum()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(settings.getAiConfidenceThreshold()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(settings.getAutoPostLimitMonths()).isEqualTo(1);
        assertThat(List.of(
                settings.getFirstResponseMinutesCritical(), settings.getResolveMinutesCritical(),
                settings.getFirstResponseMinutesHigh(), settings.getResolveMinutesHigh(),
                settings.getFirstResponseMinutesMedium(), settings.getResolveMinutesMedium(),
                settings.getFirstResponseMinutesLow(), settings.getResolveMinutesLow()))
                .containsExactly(15, 240, 120, 1440, 480, 4320, 1440, 10080);
    }

    @Test
    void settingsTableAllowsOnlyOneRow() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO society_settings (id) VALUES (2)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staffSkillsSurviveARoundTrip() {
        AppUser staff = userRepository.save(AppUser.staff("+919811111111", "Test Plumber",
                List.of(ComplaintCategory.PLUMBING, ComplaintCategory.CIVIL), false, CREATED_AT));
        entityManager.flush();
        entityManager.clear();

        AppUser reloaded = userRepository.findById(staff.getId()).orElseThrow();

        assertThat(reloaded.getSkills()).containsExactlyInAnyOrder(ComplaintCategory.PLUMBING, ComplaintCategory.CIVIL);
    }

    @Test
    void flatAreaMustBePositive() {
        Tower tower = towerRepository.save(new Tower("T98", "Tower 98"));

        assertThatThrownBy(() -> flatRepository.saveAndFlush(new Flat("T98-0101", tower, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flatCodeMustFollowTowerFlatFormat() {
        Tower tower = towerRepository.save(new Tower("T98", "Tower 98"));

        assertThatThrownBy(() -> flatRepository.saveAndFlush(new Flat("98-101", tower, 900)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aUserJoinsAFlatOnlyOnce() {
        Tower tower = towerRepository.save(new Tower("T98", "Tower 98"));
        Flat flat = flatRepository.save(new Flat("T98-0101", tower, 900));
        AppUser resident = userRepository.save(AppUser.resident("+919822222222", "Test Resident", CREATED_AT));
        memberRepository.saveAndFlush(new FlatMember(flat, resident, MemberRelation.OWNER));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(new FlatMember(flat, resident, MemberRelation.FAMILY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyStaffCanBeDutyManager() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO app_user (phone, full_name, role, is_duty_manager, created_at)
                VALUES ('+919833333333', 'Not Staff', 'RESIDENT', TRUE, now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void residentsCannotHaveSkills() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO app_user (phone, full_name, role, skills, created_at)
                VALUES ('+919844444444', 'Not Staff', 'RESIDENT', '{PLUMBING}', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
