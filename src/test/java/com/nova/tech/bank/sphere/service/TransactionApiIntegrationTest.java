package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.*;
import com.nova.tech.bank.sphere.repository.AccountRepository;
import com.nova.tech.bank.sphere.repository.BankTransactionRepository;
import com.nova.tech.bank.sphere.repository.StepUpTokenRepository;
import com.nova.tech.bank.sphere.repository.UserRepository;
import com.nova.tech.bank.sphere.security.JwtService;
import com.nova.tech.bank.sphere.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired BankTransactionRepository transactionRepository;
    @Autowired StepUpTokenRepository stepUpTokenRepository;
    @Autowired JwtService jwtService;
    @MockitoBean MailService mailService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /* ----------------------------------------------------------- helpers */

    private record Customer(User user, String token, Account account) {}

    private static String randomPhone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    private Customer customer(String balance) {
        User user = userRepository.save(User.builder()
                .name("Test Customer")
                .email("tx-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .phone(randomPhone())
                .role(Role.CUSTOMER).status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL)
                .failedAttempts(0).emailVerified(true).build());
        Account account = accountRepository.save(Account.builder()
                .accountNumber("7" + String.format("%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L)))
                .userId(user.getId()).type(AccountType.SAVINGS).status(AccountStatus.ACTIVE)
                .balance(new BigDecimal(balance)).build());
        return new Customer(user, jwtService.generateAccessToken(user), account);
    }

    private ResultActions deposit(Customer c, String number, String key, String amount) throws Exception {
        return mvc.perform(post("/api/accounts/" + number + "/deposit")
                .header("Authorization", "Bearer " + c.token())
                .header("Idempotency-Key", key)
                .contentType(JSON).content("{\"amount\": " + amount + ", \"description\": \"test\"}"));
    }

    private ResultActions withdraw(Customer c, String key, String amount, String stepUpToken) throws Exception {
        var request = post("/api/accounts/" + c.account().getAccountNumber() + "/withdraw")
                .header("Authorization", "Bearer " + c.token())
                .header("Idempotency-Key", key)
                .contentType(JSON).content("{\"amount\": " + amount + "}");
        if (stepUpToken != null) request = request.header("X-Step-Up-Token", stepUpToken);
        return mvc.perform(request);
    }

    private BigDecimal balanceOf(Customer c) {
        return accountRepository.findById(c.account().getId()).orElseThrow().getBalance();
    }

    private long ledgerRowsFor(Customer c) {
        return transactionRepository.findAll().stream()
                .filter(t -> c.user().getId().equals(t.getUserId())).count();
    }

    private void approve(Customer c, String rawToken, String context) {
        stepUpTokenRepository.save(StepUpToken.builder()
                .userId(c.user().getId()).action(StepUpAction.HIGH_VALUE_WITHDRAWAL).context(context)
                .tokenHash(TokenUtil.sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(2)).used(false).createdAt(LocalDateTime.now())
                .build());
    }

    /* -------------------------------------------------------------- tests */

    @Test
    void depositThenWithdraw_updatesTheBalance_andTheLedger() throws Exception {
        Customer c = customer("1000.00");
        String number = c.account().getAccountNumber();

        deposit(c, number, "dep-key-0001", "250.50")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(250.5))
                .andExpect(jsonPath("$.balanceAfter").value(1250.5))
                .andExpect(jsonPath("$.referenceId").exists());

        withdraw(c, "wd-key-00001", "100.00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(1150.5));

        assertEquals("1150.50", balanceOf(c).toPlainString());
        assertEquals(2, ledgerRowsFor(c));

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value(number))
                .andExpect(jsonPath("$[0].balance").value(1150.5));
    }

    @Test
    void theSameRequestSentTwice_movesMoneyOnce() throws Exception {
        Customer c = customer("0.00");
        String number = c.account().getAccountNumber();

        deposit(c, number, "same-key-0001", "500.00").andExpect(status().isCreated());
        deposit(c, number, "same-key-0001", "500.00")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertEquals("500.00", balanceOf(c).toPlainString());
        assertEquals(1, ledgerRowsFor(c));

        deposit(c, number, "same-key-0001", "600.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals("500.00", balanceOf(c).toPlainString());
    }

    @Test
    void refusedRequests_changeNothing() throws Exception {
        Customer c = customer("100.00");
        String number = c.account().getAccountNumber();

        withdraw(c, "wd-key-00002", "100.01", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        deposit(c, number, "bad-amount-01", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        deposit(c, number, "bad-amount-02", "10.123").andExpect(status().isBadRequest());

        mvc.perform(post("/api/accounts/" + number + "/deposit")
                        .header("Authorization", "Bearer " + c.token())
                        .contentType(JSON).content("{\"amount\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertEquals("100.00", balanceOf(c).toPlainString());
        assertEquals(0, ledgerRowsFor(c));
    }

    @Test
    void frozenAccount_cannotTransact() throws Exception {
        Customer c = customer("100.00");
        Account frozen = accountRepository.findById(c.account().getId()).orElseThrow();
        frozen.setStatus(AccountStatus.FROZEN);
        accountRepository.save(frozen);

        deposit(c, frozen.getAccountNumber(), "frozen-key-01", "10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        assertEquals("100.00", balanceOf(c).toPlainString());
    }

    @Test
    void anotherCustomersAccount_isInvisible() throws Exception {
        Customer owner = customer("100.00");
        Customer attacker = customer("0.00");

        deposit(attacker, owner.account().getAccountNumber(), "idor-key-0001", "50")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        deposit(attacker, "799999999999", "idor-key-0002", "50").andExpect(status().isNotFound());

        assertEquals("100.00", balanceOf(owner).toPlainString());
    }

    @Test
    void onlyCustomersMayUseTheseEndpoints() throws Exception {
        Customer c = customer("100.00");
        User admin = userRepository.save(User.builder().name("Admin User")
                .email("adm-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .phone(randomPhone())
                .role(Role.ADMIN).status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL)
                .failedAttempts(0).emailVerified(true).build());

        mvc.perform(post("/api/accounts/" + c.account().getAccountNumber() + "/deposit")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin))
                        .header("Idempotency-Key", "role-key-0001")
                        .contentType(JSON).content("{\"amount\": 10}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/accounts/" + c.account().getAccountNumber() + "/deposit")
                        .contentType(JSON).content("{\"amount\": 10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void largeWithdrawal_needsStepUp_whichWorksOnce() throws Exception {
        Customer c = customer("50000.00");
        String number = c.account().getAccountNumber();
        String context = "acct=" + number + ";amount=15000.00";

        withdraw(c, "big-key-00001", "15000", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"))
                .andExpect(jsonPath("$.details.action").value("HIGH_VALUE_WITHDRAWAL"))
                .andExpect(jsonPath("$.details.context").value(context));
        assertEquals("50000.00", balanceOf(c).toPlainString());

        // Stands in for POST /api/auth/step-up, which needs a real authenticator code.
        approve(c, "approval-token-1", context);

        // The first attempt did not burn the key, so the client may reuse it.
        withdraw(c, "big-key-00001", "15000", "approval-token-1").andExpect(status().isCreated());
        assertEquals("35000.00", balanceOf(c).toPlainString());

        // The approval is single use.
        withdraw(c, "big-key-00002", "15000", "approval-token-1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        // An approval for another amount does not fit.
        approve(c, "approval-token-2", context);
        withdraw(c, "big-key-00003", "16000", "approval-token-2").andExpect(status().isForbidden());
        assertEquals("35000.00", balanceOf(c).toPlainString());
    }
}