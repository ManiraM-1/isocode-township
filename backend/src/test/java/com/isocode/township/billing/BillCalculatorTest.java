package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class BillCalculatorTest {

    private static final BigDecimal RATE = Money.of("6.50");

    @ParameterizedTest(name = "{0} sq ft × ₹6.50 = ₹{1}")
    @CsvSource({
            "1150, 7475.00",
            "980, 6370.00",
            "1640, 10660.00"
    })
    void briefFlatsBillExactlyAsInTheSampleData(int areaSqft, String expected) {
        BigDecimal amount = BillCalculator.amount(areaSqft, RATE);

        assertThat(amount).isEqualTo(new BigDecimal(expected));
        assertThat(amount.scale()).isEqualTo(2);
    }

    @Test
    void fractionalRatesRoundHalfUpToPaise() {
        assertThat(BillCalculator.amount(1001, new BigDecimal("6.555"))).isEqualTo(new BigDecimal("6561.56"));
        assertThat(BillCalculator.amount(3, new BigDecimal("0.005"))).isEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void zeroOrNegativeInputsAreRejected() {
        assertThatThrownBy(() -> BillCalculator.amount(0, RATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillCalculator.amount(980, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
