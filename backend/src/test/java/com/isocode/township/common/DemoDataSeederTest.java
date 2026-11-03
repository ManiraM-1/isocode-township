package com.isocode.township.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.IntegrationTest;
import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatMember;
import com.isocode.township.flat.FlatMemberRepository;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.flat.MemberRelation;
import com.isocode.township.flat.TowerRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DemoDataSeederTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-03T04:30:00Z");

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

    @Autowired
    private ApplicationContext applicationContext;

    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Kolkata"));
        seeder = new DemoDataSeeder(towerRepository, flatRepository, userRepository, memberRepository, fixedClock);
    }

    @Test
    void nothingIsSeededAtStartupWhenDemoModeIsOff() {
        assertThat(applicationContext.getBeansOfType(DemoDataSeedRunner.class)).isEmpty();
        assertThat(flatRepository.count()).isZero();
    }

    @Test
    void briefFlatsMatchTheSampleDataExactly() {
        seeder.seedIfEmpty();

        assertThat(flatRepository.findByCode("T07-1203")).get().extracting(Flat::getAreaSqft).isEqualTo(1150);
        assertThat(flatRepository.findByCode("T12-0408")).get().extracting(Flat::getAreaSqft).isEqualTo(980);
        assertThat(flatRepository.findByCode("T21-1601")).get().extracting(Flat::getAreaSqft).isEqualTo(1640);

        assertThat(membersOf("T07-1203"))
                .containsExactly(tuple("Neeraj Kulkarni", MemberRelation.OWNER, Optional.of("+919000000001")));
        assertThat(membersOf("T12-0408")).containsExactlyInAnyOrder(
                tuple("Swati Joshi", MemberRelation.TENANT, Optional.of("+919000000002")),
                tuple("Vivek Bhosale", MemberRelation.OWNER, Optional.empty()));
        assertThat(membersOf("T21-1601"))
                .containsExactly(tuple("Aamir Shaikh", MemberRelation.OWNER, Optional.of("+919000000003")));
    }

    @Test
    void seedsSixTowersAndAboutFortyEightFlatsWithinTheAreaRange() {
        seeder.seedIfEmpty();

        assertThat(towerRepository.findAll()).extracting(tower -> tower.getCode())
                .containsExactlyInAnyOrder("T03", "T07", "T12", "T15", "T21", "T26");
        List<Flat> flats = flatRepository.findAll();
        assertThat(flats).hasSize(48);
        assertThat(flats).allSatisfy(flat -> {
            assertThat(flat.getAreaSqft()).isBetween(650, 1800);
            assertThat(flat.getCode()).startsWith(flat.getTower().getCode() + "-");
            assertThat(memberRepository.findByFlatIdAndActiveTrue(flat.getId()))
                    .extracting(FlatMember::getRelation).containsOnlyOnce(MemberRelation.OWNER);
        });
    }

    @Test
    void seedsTheHelpDeskTeamWithSkillsAndOneDutyManager() {
        seeder.seedIfEmpty();

        List<AppUser> staff = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.HELPDESK)
                .sorted(Comparator.comparing(user -> user.getPhone().orElseThrow()))
                .toList();

        assertThat(staff).extracting(AppUser::getFullName).containsExactly(
                "Anjali Deshmukh", "Ravi Patil", "Sunil More", "Prakash Jadhav", "Meena Gaikwad", "Kiran Shinde");
        assertThat(staff).extracting(user -> user.getPhone().orElseThrow()).containsExactly(
                "+919000000101", "+919000000102", "+919000000103",
                "+919000000104", "+919000000105", "+919000000106");
        assertThat(staff.get(0).getSkills()).containsExactlyInAnyOrder(ComplaintCategory.values());
        assertThat(staff.get(1).getSkills()).containsExactlyInAnyOrder(ComplaintCategory.PLUMBING, ComplaintCategory.CIVIL);
        assertThat(staff.get(2).getSkills()).containsExactly(ComplaintCategory.ELECTRICAL);
        assertThat(staff.get(3).getSkills()).containsExactly(ComplaintCategory.LIFT);
        assertThat(staff.get(4).getSkills())
                .containsExactlyInAnyOrder(ComplaintCategory.HOUSEKEEPING, ComplaintCategory.PEST_CONTROL);
        assertThat(staff.get(5).getSkills()).containsExactly(ComplaintCategory.SECURITY);
        assertThat(staff).filteredOn(AppUser::isDutyManager).extracting(AppUser::getFullName)
                .containsExactly("Kiran Shinde");
    }

    @Test
    void timestampsComeFromTheInjectedClock() {
        seeder.seedIfEmpty();

        assertThat(userRepository.findAll()).extracting(AppUser::getCreatedAt).containsOnly(FIXED_NOW);
    }

    @Test
    void secondRunChangesNothing() {
        assertThat(seeder.seedIfEmpty()).isTrue();
        long users = userRepository.count();

        assertThat(seeder.seedIfEmpty()).isFalse();

        assertThat(flatRepository.count()).isEqualTo(48);
        assertThat(userRepository.count()).isEqualTo(users);
    }

    @Test
    void sameSeedProducesTheSameFlats() {
        seeder.seedIfEmpty();
        List<String> firstRun = flatSnapshot();
        deleteSeededData();

        seeder.seedIfEmpty();

        assertThat(flatSnapshot()).isEqualTo(firstRun);
    }

    private List<Tuple> membersOf(String flatCode) {
        Flat flat = flatRepository.findByCode(flatCode).orElseThrow();
        return memberRepository.findByFlatIdAndActiveTrue(flat.getId()).stream()
                .map(member -> tuple(member.getUser().getFullName(), member.getRelation(), member.getUser().getPhone()))
                .toList();
    }

    private List<String> flatSnapshot() {
        return jdbcTemplate.queryForList("""
                SELECT f.code || ':' || f.area_sqft || ':' || string_agg(m.relation || '=' || u.full_name, ',' ORDER BY m.relation, u.full_name)
                FROM flat f JOIN flat_member m ON m.flat_id = f.id JOIN app_user u ON u.id = m.user_id
                GROUP BY f.code, f.area_sqft ORDER BY f.code
                """, String.class);
    }

    private void deleteSeededData() {
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("DELETE FROM flat_member");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("DELETE FROM flat");
        jdbcTemplate.update("DELETE FROM tower");
    }
}
