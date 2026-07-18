package com.knox.galaxy.service;

import com.knox.galaxy.dto.ClientRequest;
import com.knox.galaxy.dto.ClientResponse;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.ClientRepository;
import com.knox.galaxy.repository.KnoxPlanCatalogueRepository;
import com.knox.galaxy.repository.SetupFeeInstallmentRepository;
import com.knox.galaxy.repository.SubscriptionPeriodRepository;
import com.knox.galaxy.repository.TenantRepository;
import com.knox.galaxy.tenancy.ProvisionTenantCommand;
import com.knox.galaxy.tenancy.TenantContext;
import com.knox.galaxy.tenancy.TenantProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KNOX client manager (§16). Platform-only: every table here lives in `knox`
 * and is schema-qualified, so no tenant is ever bound.
 */
@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private static final int TRIAL_DAYS = 7;
    private static final short INSTALLMENT_COUNT = 4;
    private static final int MAX_SLUG_ATTEMPTS = 30;

    private final ClientRepository clientRepository;
    private final SetupFeeInstallmentRepository installmentRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final KnoxPlanCatalogueRepository planRepository;
    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final EmailService emailService;

    public ClientService(ClientRepository clientRepository,
                         SetupFeeInstallmentRepository installmentRepository,
                         SubscriptionPeriodRepository periodRepository,
                         KnoxPlanCatalogueRepository planRepository,
                         TenantRepository tenantRepository,
                         TenantProvisioningService tenantProvisioningService,
                         EmailService emailService) {
        this.clientRepository = clientRepository;
        this.installmentRepository = installmentRepository;
        this.periodRepository = periodRepository;
        this.planRepository = planRepository;
        this.tenantRepository = tenantRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        TenantContext.clear();
        List<Client> clients = clientRepository.findAllByOrderByIdAsc();
        if (clients.isEmpty()) {
            return List.of();
        }
        List<Long> ids = clients.stream().map(Client::getId).collect(Collectors.toList());

        // Fetched in two queries rather than per client: the dashboard lists
        // every client at once and this is the N+1 hot spot.
        Map<Long, List<SetupFeeInstallment>> installments = installmentRepository.findByClientIdIn(ids)
                .stream().collect(Collectors.groupingBy(SetupFeeInstallment::getClientId));
        Map<Long, List<SubscriptionPeriod>> periods = periodRepository.findByClientIdIn(ids)
                .stream().collect(Collectors.groupingBy(SubscriptionPeriod::getClientId));
        Map<Long, String> tenantSlugs = tenantRepository.findByClientIdIn(ids).stream()
                .filter(t -> t.getClientId() != null)
                .collect(Collectors.toMap(Tenant::getClientId, Tenant::getSlug));

        return clients.stream()
                .map(c -> toResponse(c,
                        installments.getOrDefault(c.getId(), List.of()),
                        periods.getOrDefault(c.getId(), List.of()),
                        tenantSlugs.get(c.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        TenantContext.clear();
        Client client = require(id);
        String tenantSlug = tenantRepository.findByClientId(id).map(Tenant::getSlug).orElse(null);
        return toResponse(client,
                installmentRepository.findByClientIdOrderByInstallmentNoAsc(id),
                periodRepository.findByClientIdOrderByPeriodStartAsc(id),
                tenantSlug);
    }

    /**
     * Creates the KNOX billing record, then provisions the client a real
     * Galaxy tenant and emails them the login.
     *
     * <p>Deliberately NOT {@code @Transactional}: {@link TenantProvisioningService#provision}
     * switches {@link TenantContext} across several of its own Hibernate
     * sessions on different schemas, which cannot safely nest inside a
     * connection already checked out by an outer transaction (same reason
     * {@code AuthService} avoids it). If provisioning fails, the client row
     * created a moment earlier is deleted by hand so a retry doesn't leave an
     * orphaned billing record with no working login — the same "not atomic,
     * cleaned up on failure" approach provisioning itself already uses.
     */
    public ClientResponse create(ClientRequest request) {
        TenantContext.clear();

        Client client = new Client();
        apply(client, request);
        client.setStatus(request.isOnTrial() ? KnoxStatus.trial : KnoxStatus.active);
        client.setCreatedAt(LocalDateTime.now());
        client = clientRepository.save(client);
        createSetupInstallments(client);

        ProvisionTenantCommand command = buildProvisionCommand(client);
        try {
            tenantProvisioningService.provision(command);
        } catch (RuntimeException e) {
            log.error("Tenant provisioning failed for new client {}; removing the client record", client.getId(), e);
            installmentRepository.deleteByClientId(client.getId());
            clientRepository.deleteById(client.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Could not create a Galaxy account for this client: " + e.getMessage(), e);
        }

        boolean emailSent;
        String temporaryPassword = null;
        try {
            emailService.sendTenantWelcomeEmail(client.getEmail(), client.getBusinessName(),
                    client.getEmail(), command.getOwnerPassword());
            emailSent = true;
        } catch (MailException e) {
            // The tenant already exists and is usable — losing the email is a
            // notification failure, not a provisioning one. Hand the password
            // back so staff can relay it some other way.
            log.error("Tenant provisioned for client {} but the welcome email failed to send", client.getId(), e);
            emailSent = false;
            temporaryPassword = command.getOwnerPassword();
        }

        ClientResponse response = get(client.getId());
        response.setEmailSent(emailSent);
        response.setTemporaryPassword(temporaryPassword);
        return response;
    }

    /** Builds the owner's Galaxy login from what Add Client already collects. */
    private ProvisionTenantCommand buildProvisionCommand(Client client) {
        ProvisionTenantCommand command = new ProvisionTenantCommand();
        String slug = generateUniqueSlug(client.getBusinessName());
        command.setSlug(slug);
        command.setBusinessName(client.getBusinessName());
        command.setClientId(client.getId());
        command.setOwnerEmail(client.getEmail());
        command.setOwnerPassword(PasswordGenerator.generate());
        command.setOwnerUsername(slug);

        String contact = client.getContactPerson();
        if (contact != null && !contact.isBlank()) {
            String[] parts = contact.trim().split("\\s+", 2);
            command.setOwnerFirstName(parts[0]);
            command.setOwnerLastName(parts.length > 1 ? parts[1] : "Owner");
        } else {
            command.setOwnerFirstName(client.getBusinessName());
            command.setOwnerLastName("Owner");
        }
        return command;
    }

    /**
     * Derives a schema-safe slug from the business name (lowercase letters,
     * digits, underscores; 3-31 chars; starts with a letter — mirrors
     * TenantProvisioningService's SAFE_SLUG) and appends a numeric suffix on
     * collision. "Dazz Electric" -> "dazz_electric"; a second client with the
     * same name becomes "dazz_electric_2".
     */
    private String generateUniqueSlug(String businessName) {
        String base = businessName.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "c_" + base;
        }
        if (base.length() > 27) {
            base = base.substring(0, 27);
        }
        while (base.length() < 3) {
            base = base + "_x";
        }
        String candidate = base;
        for (int attempt = 2; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            if (!tenantRepository.existsBySlug(candidate)) {
                return candidate;
            }
            candidate = base + "_" + attempt;
        }
        throw new IllegalStateException("Could not derive a unique tenant slug from " + businessName);
    }

    /**
     * Changing the plan or setup option rebuilds the installment rows, because
     * both the amount and the number of slots derive from them. Payment marks on
     * the old rows are deliberately not carried over — the amounts they referred
     * to no longer exist, and silently re-marking them would misstate revenue.
     */
    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        TenantContext.clear();
        Client client = require(id);

        boolean feeShapeChanged = client.getPlan() != request.getPlan()
                || client.getSetupOption() != request.getSetupOption();

        apply(client, request);
        // status is owned by block/unblock; only the trial flag moves here.
        if (client.getStatus() != KnoxStatus.blocked) {
            client.setStatus(request.isOnTrial() ? KnoxStatus.trial : KnoxStatus.active);
        }
        clientRepository.save(client);

        if (feeShapeChanged) {
            installmentRepository.deleteByClientId(id);
            installmentRepository.flush();
            createSetupInstallments(client);
        }
        return get(id);
    }

    @Transactional
    public ClientResponse setBlocked(Long id, boolean blocked) {
        TenantContext.clear();
        Client client = require(id);
        if (blocked) {
            client.setStatus(KnoxStatus.blocked);
            client.setOnTrial(false);
        } else {
            client.setStatus(client.isOnTrial() ? KnoxStatus.trial : KnoxStatus.active);
        }
        clientRepository.save(client);
        return get(id);
    }

    @Transactional
    public ClientResponse setSetupInstallmentPaid(Long id, short installmentNo, boolean paid) {
        TenantContext.clear();
        require(id);
        SetupFeeInstallment installment = installmentRepository
                .findByClientIdAndInstallmentNo(id, installmentNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No installment " + installmentNo + " for client " + id));
        installment.setPaid(paid);
        installment.setPaidAt(paid ? LocalDateTime.now() : null);
        installmentRepository.save(installment);
        return get(id);
    }

    /**
     * Marks a billing period paid/unpaid, creating the row on first touch.
     *
     * <p>Per-order clients have no fixed price, so a period cannot be marked
     * paid until its amount has been entered — otherwise the payment records a
     * revenue of nothing.
     */
    @Transactional
    public ClientResponse setPeriodPayment(Long id, String periodKey, Boolean paid, BigDecimal amount) {
        TenantContext.clear();
        Client client = require(id);
        LocalDate periodStart = PeriodKeys.toPeriodStart(client.getPlan(), periodKey);

        SubscriptionPeriod period = periodRepository
                .findByClientIdAndPeriodStart(id, periodStart)
                .orElseGet(() -> {
                    SubscriptionPeriod p = new SubscriptionPeriod();
                    p.setClientId(id);
                    p.setPeriodStart(periodStart);
                    return p;
                });

        if (amount != null) {
            period.setAmount(amount);
        }
        if (paid != null) {
            BigDecimal effective = period.getAmount() != null
                    ? period.getAmount()
                    : planFee(client.getPlan());
            if (paid && effective == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Enter the bill amount before marking this period paid");
            }
            period.setPaid(paid);
            period.setPaidAt(paid ? LocalDateTime.now() : null);
        }
        periodRepository.save(period);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<KnoxPlanCatalogue> plans() {
        TenantContext.clear();
        return planRepository.findAll();
    }

    // ------------------------------------------------------------------

    private Client require(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No client " + id));
    }

    private void apply(Client client, ClientRequest request) {
        client.setBusinessName(request.getBusinessName());
        client.setContactPerson(request.getContactPerson());
        client.setPhone(request.getPhone());
        client.setEmail(request.getEmail());
        client.setPlan(request.getPlan());
        client.setStartDate(request.getStartDate());
        client.setSetupOption(request.getSetupOption());
        client.setOnTrial(request.isOnTrial());
        client.setNotes(request.getNotes());
    }

    private void createSetupInstallments(Client client) {
        BigDecimal setupFee = planRepository.findById(client.getPlan())
                .map(KnoxPlanCatalogue::getSetupFee)
                .orElseThrow(() -> new IllegalStateException(
                        "knox.plans has no row for " + client.getPlan() + "; run knox_platform.sql"));

        if (client.getSetupOption() == KnoxSetupOption.full) {
            installmentRepository.save(installment(client.getId(), (short) 1, setupFee));
            return;
        }
        // Divide so the four rows sum back to setupFee exactly: the last slot
        // absorbs any rounding remainder rather than losing cents.
        BigDecimal each = setupFee.divide(BigDecimal.valueOf(INSTALLMENT_COUNT), 2, RoundingMode.DOWN);
        BigDecimal last = setupFee.subtract(each.multiply(BigDecimal.valueOf(INSTALLMENT_COUNT - 1)));
        for (short n = 1; n <= INSTALLMENT_COUNT; n++) {
            installmentRepository.save(installment(client.getId(), n, n == INSTALLMENT_COUNT ? last : each));
        }
    }

    private SetupFeeInstallment installment(Long clientId, short no, BigDecimal amount) {
        SetupFeeInstallment i = new SetupFeeInstallment();
        i.setClientId(clientId);
        i.setInstallmentNo(no);
        i.setAmount(amount);
        i.setPaid(false);
        return i;
    }

    private BigDecimal planFee(KnoxPlan plan) {
        return planRepository.findById(plan).map(KnoxPlanCatalogue::getSubscriptionFee).orElse(null);
    }

    private ClientResponse toResponse(Client c,
                                      List<SetupFeeInstallment> installments,
                                      List<SubscriptionPeriod> periods,
                                      String tenantSlug) {
        ClientResponse r = new ClientResponse();
        r.setId(c.getId());
        r.setBusinessName(c.getBusinessName());
        r.setContactPerson(c.getContactPerson());
        r.setPhone(c.getPhone());
        r.setEmail(c.getEmail());
        r.setPlan(c.getPlan());
        r.setStartDate(c.getStartDate());
        r.setSetupOption(c.getSetupOption());
        r.setOnTrial(c.isOnTrial());
        r.setNotes(c.getNotes());
        r.setBlocked(c.getStatus() == KnoxStatus.blocked);
        r.setStatus(effectiveStatus(c));
        r.setTrialDaysLeft(trialDaysLeft(c));
        r.setTenantSlug(tenantSlug);

        r.setSetupPaid(installments.stream()
                .sorted(Comparator.comparing(SetupFeeInstallment::getInstallmentNo))
                .map(SetupFeeInstallment::isPaid)
                .collect(Collectors.toList()));

        Map<String, Boolean> payments = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (SubscriptionPeriod p : periods) {
            String key = PeriodKeys.toKey(c.getPlan(), p.getPeriodStart());
            payments.put(key, p.isPaid());
            if (p.getAmount() != null) {
                amounts.put(key, p.getAmount());
            }
        }
        r.setSubPayments(payments);
        r.setSubAmounts(amounts);
        return r;
    }

    /** A lapsed trial reads as blocked without anything having to write to the row. */
    private KnoxStatus effectiveStatus(Client c) {
        if (c.getStatus() == KnoxStatus.blocked) {
            return KnoxStatus.blocked;
        }
        if (c.isOnTrial()) {
            return daysSinceStart(c) >= TRIAL_DAYS ? KnoxStatus.blocked : KnoxStatus.trial;
        }
        return KnoxStatus.active;
    }

    private int trialDaysLeft(Client c) {
        if (!c.isOnTrial()) {
            return 0;
        }
        return (int) Math.max(0, TRIAL_DAYS - daysSinceStart(c));
    }

    private long daysSinceStart(Client c) {
        return ChronoUnit.DAYS.between(c.getStartDate(), LocalDate.now());
    }
}
