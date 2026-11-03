package com.isocode.township.common;

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
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final long RANDOM_SEED = 42L;
    private static final List<String> TOWER_CODES = List.of("T03", "T07", "T12", "T15", "T21", "T26");
    private static final int EXTRA_FLAT_COUNT = 45;
    private static final int MIN_AREA_SQFT = 650;
    private static final int MAX_AREA_SQFT = 1800;
    private static final int AREA_STEP_SQFT = 5;
    private static final int TOP_FLOOR = 20;
    private static final int FLATS_PER_FLOOR = 8;
    private static final double TENANT_SHARE = 0.20;
    private static final double FAMILY_MEMBER_SHARE = 0.25;
    private static final String EXTRA_RESIDENT_PHONE_FORMAT = "+9191000%05d";

    private static final List<String> FIRST_NAMES = List.of(
            "Aarti", "Abhijit", "Aditi", "Ajay", "Amruta", "Anil", "Ashwini", "Chetan", "Deepa", "Dhanashree",
            "Ganesh", "Gauri", "Harshad", "Ketaki", "Mahesh", "Manasi", "Mangesh", "Neha", "Nikhil", "Omkar",
            "Pallavi", "Pooja", "Pranav", "Priya", "Rahul", "Rohini", "Sachin", "Sanjay", "Shreya", "Siddharth",
            "Snehal", "Sonali", "Tejas", "Tushar", "Vaishali", "Varun", "Yogesh", "Zainab", "Farhan", "Imran");

    private static final List<String> LAST_NAMES = List.of(
            "Apte", "Bapat", "Chavan", "Dhole", "Gokhale", "Joglekar", "Kale", "Karve", "Khan", "Mane",
            "Nene", "Pawar", "Phadke", "Ranade", "Salunkhe", "Sathe", "Shirke", "Thorat", "Wagh", "Sayyed");

    private final TowerRepository towerRepository;
    private final FlatRepository flatRepository;
    private final AppUserRepository userRepository;
    private final FlatMemberRepository memberRepository;
    private final Clock clock;

    public DemoDataSeeder(TowerRepository towerRepository, FlatRepository flatRepository,
                          AppUserRepository userRepository, FlatMemberRepository memberRepository, Clock clock) {
        this.towerRepository = towerRepository;
        this.flatRepository = flatRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean seedIfEmpty() {
        if (flatRepository.count() > 0) {
            return false;
        }
        Instant now = clock.instant();
        Random random = new Random(RANDOM_SEED);
        Map<String, Tower> towers = seedTowers();
        seedBriefFlats(towers, now);
        seedExtraFlats(towers, now, random);
        seedStaff(now);
        log.info("Demo data seeded: {} towers, {} flats, {} users",
                towerRepository.count(), flatRepository.count(), userRepository.count());
        return true;
    }

    private Map<String, Tower> seedTowers() {
        Map<String, Tower> towers = new LinkedHashMap<>();
        for (String code : TOWER_CODES) {
            String name = "Tower " + Integer.parseInt(code.substring(1));
            towers.put(code, towerRepository.save(new Tower(code, name)));
        }
        return towers;
    }

    private void seedBriefFlats(Map<String, Tower> towers, Instant now) {
        Flat neerajFlat = saveFlat(towers, "T07-1203", 1150);
        join(neerajFlat, AppUser.resident("+919000000001", "Neeraj Kulkarni", now), MemberRelation.OWNER);

        Flat swatiFlat = saveFlat(towers, "T12-0408", 980);
        join(swatiFlat, AppUser.resident("+919000000002", "Swati Joshi", now), MemberRelation.TENANT);
        join(swatiFlat, AppUser.residentWithoutLogin("Vivek Bhosale", now), MemberRelation.OWNER);

        Flat aamirFlat = saveFlat(towers, "T21-1601", 1640);
        join(aamirFlat, AppUser.resident("+919000000003", "Aamir Shaikh", now), MemberRelation.OWNER);
    }

    private void seedExtraFlats(Map<String, Tower> towers, Instant now, Random random) {
        Set<String> usedCodes = new HashSet<>(List.of("T07-1203", "T12-0408", "T21-1601"));
        int phoneCounter = 0;
        int seeded = 0;
        while (seeded < EXTRA_FLAT_COUNT) {
            String code = randomFlatCode(random);
            if (!usedCodes.add(code)) {
                continue;
            }
            Flat flat = saveFlat(towers, code, randomArea(random));
            String lastName = pick(LAST_NAMES, random);
            String ownerName = pick(FIRST_NAMES, random) + " " + lastName;
            if (random.nextDouble() < TENANT_SHARE) {
                join(flat, AppUser.residentWithoutLogin(ownerName, now), MemberRelation.OWNER);
                String tenantName = pick(FIRST_NAMES, random) + " " + pick(LAST_NAMES, random);
                join(flat, AppUser.resident(extraPhone(++phoneCounter), tenantName, now), MemberRelation.TENANT);
            } else {
                join(flat, AppUser.resident(extraPhone(++phoneCounter), ownerName, now), MemberRelation.OWNER);
            }
            if (random.nextDouble() < FAMILY_MEMBER_SHARE) {
                String familyName = pick(FIRST_NAMES, random) + " " + lastName;
                join(flat, AppUser.resident(extraPhone(++phoneCounter), familyName, now), MemberRelation.FAMILY);
            }
            seeded++;
        }
    }

    private void seedStaff(Instant now) {
        saveStaff("+919000000101", "Anjali Deshmukh", EnumSet.allOf(ComplaintCategory.class), false, now);
        saveStaff("+919000000102", "Ravi Patil", EnumSet.of(ComplaintCategory.PLUMBING, ComplaintCategory.CIVIL), false, now);
        saveStaff("+919000000103", "Sunil More", EnumSet.of(ComplaintCategory.ELECTRICAL), false, now);
        saveStaff("+919000000104", "Prakash Jadhav", EnumSet.of(ComplaintCategory.LIFT), false, now);
        saveStaff("+919000000105", "Meena Gaikwad",
                EnumSet.of(ComplaintCategory.HOUSEKEEPING, ComplaintCategory.PEST_CONTROL), false, now);
        saveStaff("+919000000106", "Kiran Shinde", EnumSet.of(ComplaintCategory.SECURITY), true, now);
    }

    private Flat saveFlat(Map<String, Tower> towers, String code, int areaSqft) {
        Tower tower = towers.get(code.substring(0, 3));
        return flatRepository.save(new Flat(code, tower, areaSqft));
    }

    private void join(Flat flat, AppUser user, MemberRelation relation) {
        memberRepository.save(new FlatMember(flat, userRepository.save(user), relation));
    }

    private void saveStaff(String phone, String name, Set<ComplaintCategory> skills, boolean dutyManager, Instant now) {
        userRepository.save(AppUser.staff(phone, name, skills, dutyManager, now));
    }

    private static String randomFlatCode(Random random) {
        String tower = pick(TOWER_CODES, random);
        int floor = 1 + random.nextInt(TOP_FLOOR);
        int unit = 1 + random.nextInt(FLATS_PER_FLOOR);
        return "%s-%02d%02d".formatted(tower, floor, unit);
    }

    private static int randomArea(Random random) {
        int steps = (MAX_AREA_SQFT - MIN_AREA_SQFT) / AREA_STEP_SQFT;
        return MIN_AREA_SQFT + AREA_STEP_SQFT * random.nextInt(steps + 1);
    }

    private static String extraPhone(int counter) {
        return EXTRA_RESIDENT_PHONE_FORMAT.formatted(counter);
    }

    private static <T> T pick(List<T> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }
}
