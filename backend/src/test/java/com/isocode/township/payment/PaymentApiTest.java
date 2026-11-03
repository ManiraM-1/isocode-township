package com.isocode.township.payment;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.billing.BillingRunService;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.servlet.http.Cookie;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class PaymentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private BillingRunService billingRunService;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestSessions sessions;

    private Long swatiFlat;
    private Cookie swati;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        billingRunService.run(YearMonth.of(2026, 11), null);
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
        swati = sessions.sessionFor(TestSessions.SWATI);
    }

    @Test
    void residentStartsATestPaymentWithAGatewayReference() throws Exception {
        createIntent(swati, swatiFlat, "6370.00", "attempt-0001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("6370.00"))
                .andExpect(jsonPath("$.gatewayRef").value(matchesPattern("PGTEST-[A-Z0-9]{12}")))
                .andExpect(jsonPath("$.receiptNo").isEmpty());
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameIntent() throws Exception {
        long first = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));

        long second = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001").andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameKeyForADifferentAmountIsAConflict() throws Exception {
        createIntent(swati, swatiFlat, "6370.00", "attempt-0001");

        createIntent(swati, swatiFlat, "100.00", "attempt-0001").andExpect(status().isConflict());
    }

    @Test
    void successPostsThePaymentExactlyOnceEvenIfCalledTwice() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));
        long paymentsBefore = paymentRepository.count();

        String receipt = textAt(simulate(swati, intentId, "SUCCESS").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED")), "receiptNo");
        String receiptAgain = textAt(simulate(swati, intentId, "SUCCESS").andExpect(status().isOk()), "receiptNo");

        assertThat(receipt).matches("RCPT-202611-\\d{5}").isEqualTo(receiptAgain);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore + 1);
        mockMvc.perform(get("/api/flats/{id}/summary", swatiFlat).cookie(swati))
                .andExpect(jsonPath("$.outstanding").value("0.00"));
    }

    @Test
    void failurePostsNothingAndTheAttemptCannotSucceedLater() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));
        long paymentsBefore = paymentRepository.count();

        simulate(swati, intentId, "FAILURE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        simulate(swati, intentId, "SUCCESS").andExpect(status().isConflict());

        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }

    @Test
    void amountsAreValidatedAndCappedAboveWhatIsDue() throws Exception {
        createIntent(swati, swatiFlat, "0.00", "attempt-0001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        createIntent(swati, swatiFlat, "106370.01", "attempt-0002")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That is more than ₹1,00,000 above what is due. Check the amount."));
        createIntent(swati, swatiFlat, "106370.00", "attempt-0003").andExpect(status().isOk());
        createIntent(swati, swatiFlat, "10.00", "bad key!").andExpect(status().isBadRequest());
    }

    @Test
    void residentCannotPayForAnotherFlat() throws Exception {
        createIntent(sessions.sessionFor(TestSessions.NEERAJ), swatiFlat, "6370.00", "attempt-0001")
                .andExpect(status().isForbidden());
    }

    @Test
    void residentCannotCompleteAnotherFlatsPayment() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));

        simulate(sessions.sessionFor(TestSessions.NEERAJ), intentId, "SUCCESS").andExpect(status().isForbidden());
    }

    @Test
    void receiptShowsWhatThePaymentWasAppliedTo() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6500.00", "attempt-0001"));
        String receiptNo = textAt(simulate(swati, intentId, "SUCCESS"), "receiptNo");

        mockMvc.perform(get("/api/receipts/{no}", receiptNo).cookie(swati))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatCode").value("T12-0408"))
                .andExpect(jsonPath("$.amount").value("6500.00"))
                .andExpect(jsonPath("$.method").value("ONLINE_TEST"))
                .andExpect(jsonPath("$.reference").value(startsWith("PGTEST-")))
                .andExpect(jsonPath("$.appliedTo[0].description").value("Maintenance for November 2026"))
                .andExpect(jsonPath("$.appliedTo[0].amount").value("6370.00"))
                .andExpect(jsonPath("$.heldAsAdvance").value("130.00"));
    }

    @Test
    void receiptsAreOnlyForTheFlatOrTheHelpDesk() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));
        String receiptNo = textAt(simulate(swati, intentId, "SUCCESS"), "receiptNo");

        mockMvc.perform(get("/api/receipts/{no}", receiptNo).cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/receipts/{no}", receiptNo).cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/receipts/{no}", "RCPT-000000-00000").cookie(swati))
                .andExpect(status().isNotFound());
    }

    @Test
    void ledgerShowsTheReceiptNumberOnThePaymentLine() throws Exception {
        long intentId = idOf(createIntent(swati, swatiFlat, "6370.00", "attempt-0001"));
        String receiptNo = textAt(simulate(swati, intentId, "SUCCESS"), "receiptNo");

        mockMvc.perform(get("/api/flats/{id}/ledger", swatiFlat).cookie(swati))
                .andExpect(jsonPath("$[0].receiptNo").isEmpty())
                .andExpect(jsonPath("$[1].type").value("PAYMENT"))
                .andExpect(jsonPath("$[1].credit").value("6370.00"))
                .andExpect(jsonPath("$[1].receiptNo").value(receiptNo))
                .andExpect(jsonPath("$[1].runningBalance").value("0.00"));
    }

    private ResultActions createIntent(Cookie session, Long flatId, String amount, String key) throws Exception {
        return mockMvc.perform(post("/api/flats/{id}/payment-intents", flatId).with(sameOriginCsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\",\"idempotencyKey\":\"" + key + "\"}"));
    }

    private ResultActions simulate(Cookie session, long intentId, String outcome) throws Exception {
        return mockMvc.perform(post("/api/payment-intents/{id}/simulate", intentId).with(sameOriginCsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + outcome + "\"}"));
    }

    private long idOf(ResultActions result) throws Exception {
        return json(result).get("id").asLong();
    }

    private String textAt(ResultActions result, String field) throws Exception {
        return json(result).get(field).asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
