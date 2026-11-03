package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class BankMatchingSchemaTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private KnownUpiIdDemoSeeder upiIdSeeder;

    @Autowired
    private KnownUpiIdRepository knownUpiIdRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private BankImportRepository importRepository;

    @Autowired
    private BankRowRepository rowRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        coreSeeder.seedIfEmpty();
    }

    @Test
    void theFourUpiIdsTheSampleStatementNeedsAreSeeded() {
        upiIdSeeder.seed();

        Map.of("aamir.s@okicici", "T21-1601", "kale.family@okaxis", "T21-0303",
                "dhole.home@okaxis", "T26-2007", "nene.home@okicici", "T15-1605").forEach((upiId, flatCode) ->
                assertThat(knownUpiIdRepository.findByUpiId(upiId)).get()
                        .extracting(KnownUpiId::getFlatId)
                        .isEqualTo(flatRepository.findByCode(flatCode).orElseThrow().getId()));
    }

    @Test
    void aboutAThirdOfFlatsHaveAKnownUpiIdAndNoneCollideWithTheSamplesUnknownOnes() {
        upiIdSeeder.seed();

        List<String> upiIds = knownUpiIdRepository.findAll().stream().map(KnownUpiId::getUpiId).toList();
        assertThat(upiIds).hasSizeBetween(14, 22).doesNotHaveDuplicates()
                .doesNotContain("a.wagh@okhdfcbank", "shree.ent@ybl")
                .allSatisfy(upiId -> assertThat(upiId).matches("[a-z0-9._-]+@[a-z]+"));
    }

    @Test
    void upiIdsAreStoredLowercaseOnly() {
        Long flatId = flatRepository.findByCode("T07-1203").orElseThrow().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO known_vpa (vpa, flat_id, created_at) VALUES ('Mixed@OkAxis', ?, now())", flatId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameRowHashMayAppearTwiceAndAResolutionNeedsATime() {
        Long uploader = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE phone = '+919000000101'", Long.class);
        BankImport bankImport = importRepository.saveAndFlush(new BankImport("a.csv", "a".repeat(64), uploader, NOW));
        bankImport.recordCounts(Map.of(), 0);
        String hash = "b".repeat(64);
        insertRow(bankImport.getId(), 2, hash);
        insertRow(bankImport.getId(), 3, hash);

        assertThat(rowRepository.findByImportIdOrderByRowNoAsc(bankImport.getId())).hasSize(2);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE bank_row SET resolution = 'IGNORED' WHERE import_id = ? AND row_no = 2", bankImport.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRow(Long importId, int rowNo, String hash) {
        entityManager.flush();
        jdbcTemplate.update("INSERT INTO bank_row (import_id, row_no, row_hash, decision) VALUES (?, ?, ?, 'UNMATCHED')",
                importId, rowNo, hash);
    }
}
