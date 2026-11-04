package com.isocode.township.committee;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.IntegrationTest;
import com.isocode.township.committee.dto.CommitteeOverview;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.complaint.ComplaintDemoSeeder;
import com.isocode.township.matching.KnownUpiIdDemoSeeder;
import com.isocode.township.payment.DemoHistorySeeder;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CommitteeDemoDataTest {

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private DemoHistorySeeder historySeeder;

    @Autowired
    private KnownUpiIdDemoSeeder upiIdSeeder;

    @Autowired
    private ComplaintDemoSeeder complaintSeeder;

    @Autowired
    private CommitteeService committeeService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
        upiIdSeeder.seed();
        complaintSeeder.seed();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-08", "2026-09", "2026-10", "2026-11"})
    void everySeededMonthBalancesAndItsAgeingAddsUpToClosingOutstanding(String month) {
        CommitteeOverview overview = committeeService.overview(month);

        assertThat(overview.reconciliation().balanced()).isTrue();
        assertThat(overview.ageing().matchesClosingOutstanding()).isTrue();
        assertThat(overview.ageing().buckets().stream().map(CommitteeOverview.AgeingLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(overview.reconciliation().closingOutstanding());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09", "2026-10", "2026-11"})
    void eachMonthOpensWhereThePreviousOneClosed(String month) {
        CommitteeOverview current = committeeService.overview(month);
        CommitteeOverview previous = committeeService.overview(current.month().minusMonths(1).toString());

        assertThat(current.reconciliation().openingOutstanding())
                .isEqualTo(previous.reconciliation().closingOutstanding());
    }

    @Test
    void closingOutstandingForTheCurrentMonthEqualsTheWholeLedger() {
        BigDecimal ledger = jdbc.queryForObject("SELECT sum(debit - credit) FROM ledger_entry", BigDecimal.class);

        assertThat(committeeService.overview(null).reconciliation().closingOutstanding()).isEqualByComparingTo(ledger);
    }
}
