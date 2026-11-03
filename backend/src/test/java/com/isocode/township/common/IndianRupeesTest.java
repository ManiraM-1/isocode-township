package com.isocode.township.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndianRupeesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = ';', value = {
            "0; ₹0.00",
            "59; ₹59.00",
            "999.5; ₹999.50",
            "4070; ₹4,070.00",
            "123456; ₹1,23,456.00",
            "1234567.89; ₹12,34,567.89",
            "123456789; ₹12,34,56,789.00",
            "-5660; -₹5,660.00"
    })
    void formatsWithIndianDigitGrouping(String amount, String expected) {
        assertThat(IndianRupees.format(new BigDecimal(amount))).isEqualTo(expected);
    }
}
