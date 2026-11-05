package com.isocode.township.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.isocode.township.SameOriginCsrf.mismatchedCsrf;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.OutboxMessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class AuthFlowTest {

    private static final String NEERAJ = "+919000000001";
    private static final String ANJALI = "+919000000101";
    private static final String UNKNOWN = "+919999999999";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seed() {
        seeder.seedIfEmpty();
    }

    @Test
    void correctCodeSignsInWithASecureHttpOnlyCookie() throws Exception {
        requestOtp(NEERAJ).andExpect(status().isOk());

        ResultActions verified = verify(NEERAJ, smsCodeFor(NEERAJ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neeraj Kulkarni"))
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.isDutyManager").value(false))
                .andExpect(jsonPath("$.flats[0].code").value("T07-1203"))
                .andExpect(jsonPath("$.flats[0].relation").value("OWNER"));

        String setCookie = verified.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("session=").contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=43200");

        Cookie session = verified.andReturn().getResponse().getCookie(SessionCookies.NAME);
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neeraj Kulkarni"));
    }

    @Test
    void wrongCodeIsRejectedAndCountsAnAttempt() throws Exception {
        requestOtp(NEERAJ);

        verify(NEERAJ, wrongCodeFor(NEERAJ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_CODE"))
                .andExpect(jsonPath("$.attemptsRemaining").value(4));

        entityManager.flush();
        assertThat(latestChallengeAttempts(NEERAJ)).isEqualTo(1);
    }

    @Test
    void sixthAttemptIsRejectedEvenWithTheCorrectCode() throws Exception {
        requestOtp(NEERAJ);
        String wrong = wrongCodeFor(NEERAJ);
        for (int attempt = 1; attempt <= 4; attempt++) {
            verify(NEERAJ, wrong).andExpect(jsonPath("$.reason").value("INVALID_CODE"));
        }
        verify(NEERAJ, wrong).andExpect(jsonPath("$.reason").value("LOCKED"));

        verify(NEERAJ, smsCodeFor(NEERAJ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("LOCKED"));
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        requestOtp(NEERAJ);
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("UPDATE otp_challenge SET expires_at = created_at WHERE phone = ?", NEERAJ);

        verify(NEERAJ, smsCodeFor(NEERAJ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
    }

    @Test
    void usedCodeCannotBeReused() throws Exception {
        requestOtp(NEERAJ);
        String code = smsCodeFor(NEERAJ);
        verify(NEERAJ, code).andExpect(status().isOk());

        verify(NEERAJ, code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("EXPIRED"));
    }

    @Test
    void fourthRequestWithinTenMinutesIsRateLimited() throws Exception {
        for (int request = 1; request <= 3; request++) {
            requestOtp(NEERAJ).andExpect(status().isOk());
        }

        requestOtp(NEERAJ)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    @Test
    void unknownPhoneGetsExactlyTheSameResponseAndNoSms() throws Exception {
        long messagesBefore = outboxRepository.count();

        String unknown = requestOtp(UNKNOWN).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(outboxRepository.count()).isEqualTo(messagesBefore);

        String known = requestOtp(NEERAJ).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(unknown).isEqualTo(known).isEqualTo("{\"expiresInSeconds\":300}");
    }

    @Test
    void invalidPhoneIsRejectedWithAFieldError() throws Exception {
        requestOtp("9000000001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message").value("Enter a 10-digit Indian mobile number"));
    }

    @Test
    void meWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sign-in required"));
    }

    @Test
    void forgedCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me").cookie(new Cookie(SessionCookies.NAME, "eyJhbGciOiJIUzI1NiJ9.e30.forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheSessionCookie() throws Exception {
        Cookie session = signIn(NEERAJ);

        Cookie cleared = mockMvc.perform(post("/api/auth/logout").with(sameOriginCsrf()).cookie(session))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(SessionCookies.NAME);

        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
    }

    @Test
    void aSignedInRequestDoesNotClearTheCsrfCookie() throws Exception {
        Cookie session = signIn(NEERAJ);

        Cookie csrf = mockMvc.perform(get("/api/me").with(sameOriginCsrf()).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrf).isNull();
    }

    @Test
    void afterLogoutTheClearedCookieIsNotSignedInAndMatchesTheLoginCookie() throws Exception {
        Cookie session = signIn(NEERAJ);

        var response = mockMvc.perform(post("/api/auth/logout").with(sameOriginCsrf()).cookie(session))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        Cookie cleared = response.getCookie(SessionCookies.NAME);
        assertThat(cleared.getPath()).isEqualTo(session.getPath());
        assertThat(cleared.getSecure()).isEqualTo(session.getSecure()).isTrue();
        assertThat(cleared.isHttpOnly()).isEqualTo(session.isHttpOnly()).isTrue();
        assertThat(cleared.getAttribute("SameSite")).isEqualTo(session.getAttribute("SameSite"));
        mockMvc.perform(get("/api/me").cookie(cleared)).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + NEERAJ + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void postWithMismatchedCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request").with(mismatchedCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + NEERAJ + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfTokenCookieIsReadableBySameOriginScript() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        assertThat(xsrf.getSecure()).isTrue();
    }

    @Test
    void residentIsForbiddenOnDeskAndCommitteeApis() throws Exception {
        Cookie resident = signIn(NEERAJ);

        mockMvc.perform(get("/api/desk/complaints").cookie(resident)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/committee/overview").cookie(resident)).andExpect(status().isForbidden());
    }

    @Test
    void helpDeskPassesTheDeskRoleCheck() throws Exception {
        Cookie staff = signIn(ANJALI);

        mockMvc.perform(get("/api/me").cookie(staff))
                .andExpect(jsonPath("$.role").value("HELPDESK"))
                .andExpect(jsonPath("$.flats").isEmpty());
        mockMvc.perform(get("/api/desk/not-built-yet").cookie(staff)).andExpect(status().isNotFound());
    }

    private Cookie signIn(String phone) throws Exception {
        requestOtp(phone).andExpect(status().isOk());
        return verify(phone, smsCodeFor(phone)).andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookies.NAME);
    }

    private ResultActions requestOtp(String phone) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/request").with(sameOriginCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}"));
    }

    private ResultActions verify(String phone, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/verify").with(sameOriginCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"));
    }

    private String smsCodeFor(String phone) {
        Long userId = userRepository.findByPhone(phone).orElseThrow().getId();
        String body = outboxRepository.findByToUserIdOrderByIdDesc(userId).getFirst().getBody();
        Matcher matcher = SIX_DIGITS.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String wrongCodeFor(String phone) {
        return smsCodeFor(phone).equals("000000") ? "111111" : "000000";
    }

    private Integer latestChallengeAttempts(String phone) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM otp_challenge WHERE phone = ? ORDER BY id DESC LIMIT 1", Integer.class, phone);
    }
}
