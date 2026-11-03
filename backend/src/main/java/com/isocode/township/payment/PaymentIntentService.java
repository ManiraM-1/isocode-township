package com.isocode.township.payment;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.billing.LedgerEntryRepository;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.ConflictException;
import com.isocode.township.common.Money;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.flat.FlatAccessGuard;
import com.isocode.township.payment.dto.PaymentIntentResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final PaymentPostingService postingService;
    private final GatewayReferences gatewayReferences;
    private final FlatAccessGuard accessGuard;
    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentIntentService(PaymentIntentRepository intentRepository, PaymentRepository paymentRepository,
                                LedgerEntryRepository ledgerRepository, PaymentPostingService postingService,
                                GatewayReferences gatewayReferences, FlatAccessGuard accessGuard,
                                PaymentProperties properties, Clock clock) {
        this.intentRepository = intentRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerRepository = ledgerRepository;
        this.postingService = postingService;
        this.gatewayReferences = gatewayReferences;
        this.accessGuard = accessGuard;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PaymentIntentResponse create(SessionUser user, Long flatId, BigDecimal amount, String idempotencyKey) {
        accessGuard.requireMember(user, flatId);
        Optional<PaymentIntent> existing = intentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return sameAttemptOrConflict(existing.get(), flatId, amount);
        }
        BigDecimal outstanding = Money.rounded(ledgerRepository.balanceOf(flatId)).max(Money.ZERO);
        if (amount.compareTo(outstanding.add(properties.maxAmountAboveOutstanding())) > 0) {
            throw new BadRequestException("That is more than ₹1,00,000 above what is due. Check the amount.");
        }
        PaymentIntent intent = intentRepository.save(new PaymentIntent(flatId, Money.rounded(amount), idempotencyKey,
                gatewayReferences.next(), user.userId(), clock.instant()));
        return toResponse(intent);
    }

    @Transactional
    public PaymentIntentResponse simulate(SessionUser user, Long intentId, SimulatedOutcome outcome) {
        PaymentIntent intent = intentRepository.findLockedById(intentId)
                .orElseThrow(() -> new NotFoundException("Payment attempt not found"));
        accessGuard.requireMember(user, intent.getFlatId());

        if (outcome == SimulatedOutcome.SUCCESS) {
            succeed(user, intent);
        } else {
            fail(intent);
        }
        return toResponse(intent);
    }

    private void succeed(SessionUser user, PaymentIntent intent) {
        switch (intent.getStatus()) {
            case SUCCEEDED -> {
            }
            case FAILED -> throw new ConflictException("This payment attempt already failed. Start a new payment.");
            case CREATED -> {
                postingService.post(new PostPaymentCommand(intent.getFlatId(), intent.getAmount(),
                        PaymentMethod.ONLINE_TEST, PaymentSource.GATEWAY, intent.getGatewayRef(), null,
                        LocalDate.now(clock), user.userId()));
                intent.markSucceeded(clock.instant());
            }
        }
    }

    private void fail(PaymentIntent intent) {
        switch (intent.getStatus()) {
            case FAILED -> {
            }
            case SUCCEEDED -> throw new ConflictException("This payment already succeeded and cannot be failed.");
            case CREATED -> intent.markFailed(clock.instant());
        }
    }

    private PaymentIntentResponse sameAttemptOrConflict(PaymentIntent intent, Long flatId, BigDecimal amount) {
        if (!intent.getFlatId().equals(flatId) || intent.getAmount().compareTo(amount) != 0) {
            throw new ConflictException("This payment attempt key was already used for a different payment.");
        }
        return toResponse(intent);
    }

    private PaymentIntentResponse toResponse(PaymentIntent intent) {
        String receiptNo = paymentRepository.findByGatewayRef(intent.getGatewayRef())
                .map(Payment::getReceiptNo)
                .orElse(null);
        return new PaymentIntentResponse(intent.getId(), intent.getFlatId(), intent.getAmount(), intent.getStatus(),
                intent.getGatewayRef(), receiptNo);
    }
}
