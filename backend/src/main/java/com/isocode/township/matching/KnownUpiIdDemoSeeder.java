package com.isocode.township.matching;

import com.isocode.township.auth.AppUser;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatMember;
import com.isocode.township.flat.FlatMemberRepository;
import com.isocode.township.flat.FlatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnownUpiIdDemoSeeder {

    static final Map<String, String> SAMPLE_STATEMENT_UPI_IDS = new LinkedHashMap<>();

    static {
        SAMPLE_STATEMENT_UPI_IDS.put("T21-1601", "aamir.s@okicici");
        SAMPLE_STATEMENT_UPI_IDS.put("T21-0303", "kale.family@okaxis");
        SAMPLE_STATEMENT_UPI_IDS.put("T26-2007", "dhole.home@okaxis");
        SAMPLE_STATEMENT_UPI_IDS.put("T15-1605", "nene.home@okicici");
    }

    private static final int EVERY_NTH_FLAT = 3;
    private static final List<String> BANK_HANDLES = List.of("okicici", "oksbi", "okaxis", "okhdfcbank");

    private final FlatRepository flatRepository;
    private final FlatMemberRepository memberRepository;
    private final KnownUpiIdRepository knownUpiIdRepository;
    private final Clock clock;

    public KnownUpiIdDemoSeeder(FlatRepository flatRepository, FlatMemberRepository memberRepository,
                                KnownUpiIdRepository knownUpiIdRepository, Clock clock) {
        this.flatRepository = flatRepository;
        this.memberRepository = memberRepository;
        this.knownUpiIdRepository = knownUpiIdRepository;
        this.clock = clock;
    }

    @Transactional
    public void seed() {
        Instant now = clock.instant();
        Set<String> used = new HashSet<>();
        List<Flat> flats = flatRepository.findAll(Sort.by("id"));
        for (Flat flat : flats) {
            String sampleUpiId = SAMPLE_STATEMENT_UPI_IDS.get(flat.getCode());
            if (sampleUpiId != null) {
                save(sampleUpiId, flat, now, used);
            }
        }
        for (int index = 0; index < flats.size(); index += EVERY_NTH_FLAT) {
            Flat flat = flats.get(index);
            if (SAMPLE_STATEMENT_UPI_IDS.containsKey(flat.getCode())) {
                continue;
            }
            String bankHandle = BANK_HANDLES.get(index % BANK_HANDLES.size());
            residentName(flat).map(name -> upiIdFor(name, bankHandle)).ifPresent(upiId -> save(upiId, flat, now, used));
        }
    }

    private void save(String upiId, Flat flat, Instant now, Set<String> used) {
        if (used.add(upiId)) {
            knownUpiIdRepository.save(new KnownUpiId(upiId, flat.getId(), null, now));
        }
    }

    private Optional<String> residentName(Flat flat) {
        return memberRepository.findByFlatIdAndActiveTrue(flat.getId()).stream()
                .map(FlatMember::getUser)
                .filter(user -> user.getPhone().isPresent())
                .map(AppUser::getFullName)
                .findFirst();
    }

    private static String upiIdFor(String fullName, String bankHandle) {
        String name = fullName.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "").trim().replaceAll("\\s+", ".");
        return name + "@" + bankHandle;
    }
}
