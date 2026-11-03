package com.isocode.township.matching;

import com.isocode.township.billing.BillCalculator;
import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchContextFactory {

    private final JdbcTemplate jdbc;
    private final SocietySettingsRepository settingsRepository;

    public MatchContextFactory(JdbcTemplate jdbc, SocietySettingsRepository settingsRepository) {
        this.jdbc = jdbc;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public MatchContext build() {
        SocietySettings settings = settingsRepository.current();
        MatchContext.Builder builder = MatchContext.builder().autoPostLimitMonths(settings.getAutoPostLimitMonths());

        each("SELECT id, code, area_sqft FROM flat", resultSet -> {
            long flatId = resultSet.getLong("id");
            builder.flat(flatId, resultSet.getString("code"));
            builder.monthlyBill(flatId, BillCalculator.amount(resultSet.getInt("area_sqft"), settings.getRatePerSqft()));
        });
        each("SELECT vpa, flat_id FROM known_vpa",
                resultSet -> builder.knownUpiId(resultSet.getString("vpa"), resultSet.getLong("flat_id")));
        each("SELECT flat_id, period, amount - amount_paid AS remaining FROM bill WHERE amount_paid < amount",
                resultSet -> builder.billRemaining(resultSet.getLong("flat_id"),
                        YearMonth.from(resultSet.getDate("period").toLocalDate()),
                        resultSet.getBigDecimal("remaining")));
        each("SELECT flat_id, sum(debit - credit) AS balance FROM ledger_entry GROUP BY flat_id",
                resultSet -> builder.totalOutstanding(resultSet.getLong("flat_id"),
                        resultSet.getBigDecimal("balance").max(BigDecimal.ZERO)));
        each("SELECT gateway_ref, id, flat_id, amount, receipt_no FROM payment WHERE gateway_ref IS NOT NULL",
                resultSet -> builder.gatewayPayment(resultSet.getString("gateway_ref"), resultSet.getLong("id"),
                        resultSet.getLong("flat_id"), resultSet.getBigDecimal("amount"), resultSet.getString("receipt_no")));
        each("SELECT utr, receipt_no FROM payment WHERE utr IS NOT NULL",
                resultSet -> builder.postedUtr(resultSet.getString("utr"), resultSet.getString("receipt_no")));
        each("SELECT id, row_hash, utr, gateway_ref FROM bank_row WHERE decision <> 'PARSE_ERROR' ORDER BY id",
                resultSet -> builder.earlierBankRow(resultSet.getLong("id"), resultSet.getString("row_hash"),
                        resultSet.getString("utr"), resultSet.getString("gateway_ref")));
        return builder.build();
    }

    private void each(String sql, RowCallbackHandler handler) {
        jdbc.query(sql, handler);
    }
}
