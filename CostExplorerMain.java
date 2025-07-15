// Directory: com.company.costexplorer
// ├── models
// │   ├── PlanType.java
// │   ├── SubscriptionEvent.java
// │   ├── SubscriptionEventType.java
// │   ├── MonthlyBill.java
// │   └── ErrorCode.java
// ├── service
// │   └── CostExplorerService.java
// ├── store
// │   └── SubscriptionStore.java
// ├── exceptions
// │   └── ServiceException.java
// ├── Main.java

// models/PlanType.java
package models;

public enum PlanType {
    BASIC(9.99), STANDARD(49.99), PREMIUM(249.99);

    private final double monthlyCost;

    PlanType(double monthlyCost) {
        this.monthlyCost = monthlyCost;
    }

    public double getMonthlyCost() {
        return monthlyCost;
    }
}

// models/SubscriptionEventType.java
package models;

public enum SubscriptionEventType {
    SUBSCRIBE, UPGRADE, CANCEL
}

// models/SubscriptionEvent.java
package models;

import java.time.LocalDate;

public class SubscriptionEvent {
    private final SubscriptionEventType type;
    private final LocalDate effectiveDate;
    private final PlanType planType;
    private final int trialDays;

    public SubscriptionEvent(SubscriptionEventType type, LocalDate effectiveDate, PlanType planType, int trialDays) {
        this.type = type;
        this.effectiveDate = effectiveDate;
        this.planType = planType;
        this.trialDays = trialDays;
    }

    public SubscriptionEventType getType() {
        return type;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public int getTrialDays() {
        return trialDays;
    }
}

// models/MonthlyBill.java
package models;

import java.time.Month;

public class MonthlyBill {
    private final Month month;
    private final double amount;

    public MonthlyBill(Month month, double amount) {
        this.month = month;
        this.amount = amount;
    }

    public Month getMonth() {
        return month;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return month + ": $" + amount;
    }
}

// models/ErrorCode.java
package models;

public enum ErrorCode {
    INVALID_CUSTOMER_ID,
    INVALID_PLAN,
    INVALID_DATE
}

// exceptions/ServiceException.java
package exceptions;

import models.ErrorCode;

public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

// store/SubscriptionStore.java
package store;

import models.SubscriptionEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionStore {
    private final Map<String, List<SubscriptionEvent>> customerEvents = new ConcurrentHashMap<>();

    public synchronized void addEvent(String customerId, SubscriptionEvent event) {
        customerEvents.computeIfAbsent(customerId, k -> new ArrayList<>()).add(event);
        customerEvents.get(customerId).sort(Comparator.comparing(SubscriptionEvent::getEffectiveDate));
    }

    public List<SubscriptionEvent> getEvents(String customerId) {
        return customerEvents.getOrDefault(customerId, Collections.emptyList());
    }
}

// service/CostExplorerService.java
package service;

import exceptions.ServiceException;
import models.*;
import store.SubscriptionStore;

import java.time.*;
import java.util.*;

public class CostExplorerService {
    private final SubscriptionStore store = new SubscriptionStore();

    public void subscribe(String customerId, PlanType plan, LocalDate startDate, int trialDays) {
        validateInputs(customerId, plan, startDate);
        store.addEvent(customerId, new SubscriptionEvent(SubscriptionEventType.SUBSCRIBE, startDate, plan, trialDays));
    }

    public void upgrade(String customerId, PlanType newPlan, LocalDate effectiveDate) {
        validateInputs(customerId, newPlan, effectiveDate);
        store.addEvent(customerId, new SubscriptionEvent(SubscriptionEventType.UPGRADE, effectiveDate, newPlan, 0));
    }

    public void cancel(String customerId, LocalDate effectiveDate) {
        if (customerId == null || customerId.isEmpty()) throw new ServiceException(ErrorCode.INVALID_CUSTOMER_ID, "Customer ID is required");
        store.addEvent(customerId, new SubscriptionEvent(SubscriptionEventType.CANCEL, effectiveDate, null, 0));
    }

    public List<MonthlyBill> getMonthlyBill(String customerId, int year) {
        List<MonthlyBill> bills = new ArrayList<>();
        List<SubscriptionEvent> events = store.getEvents(customerId);
        if (events.isEmpty()) return bills;

        PlanType currentPlan = null;
        LocalDate activeFrom = null;
        LocalDate cancelDate = null;
        int trialEndOffset = 0;

        for (Month month : Month.values()) {
            LocalDate firstOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());

            for (SubscriptionEvent event : events) {
                if (event.getEffectiveDate().isAfter(endOfMonth)) break;
                if (!event.getEffectiveDate().isAfter(firstOfMonth)) {
                    if (event.getType() == SubscriptionEventType.SUBSCRIBE || event.getType() == SubscriptionEventType.UPGRADE) {
                        currentPlan = event.getPlanType();
                        activeFrom = event.getEffectiveDate();
                        trialEndOffset = event.getTrialDays();
                    } else if (event.getType() == SubscriptionEventType.CANCEL) {
                        cancelDate = event.getEffectiveDate();
                    }
                }
            }

            if (currentPlan == null || firstOfMonth.isBefore(activeFrom)) {
                bills.add(new MonthlyBill(month, 0.0));
                continue;
            }

            if (cancelDate != null && cancelDate.isBefore(firstOfMonth)) {
                bills.add(new MonthlyBill(month, 0.0));
                continue;
            }

            LocalDate trialEnd = activeFrom.plusDays(trialEndOffset);
            if (!firstOfMonth.isBefore(trialEnd)) {
                bills.add(new MonthlyBill(month, currentPlan.getMonthlyCost()));
            } else {
                bills.add(new MonthlyBill(month, 0.0));
            }
        }

        return bills;
    }

    public double getYearlyEstimate(String customerId, int year) {
        return getMonthlyBill(customerId, year).stream().mapToDouble(MonthlyBill::getAmount).sum();
    }

    private void validateInputs(String customerId, PlanType plan, LocalDate date) {
        if (customerId == null || customerId.isEmpty())
            throw new ServiceException(ErrorCode.INVALID_CUSTOMER_ID, "Customer ID cannot be null or empty");
        if (plan == null)
            throw new ServiceException(ErrorCode.INVALID_PLAN, "PlanType cannot be null");
        if (date == null || date.isAfter(LocalDate.now().plusYears(1)))
            throw new ServiceException(ErrorCode.INVALID_DATE, "Invalid effective date");
    }
}

// Main.java
import service.CostExplorerService;
import models.PlanType;
import models.MonthlyBill;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        CostExplorerService explorer = new CostExplorerService();
        explorer.subscribe("cust1", PlanType.BASIC, LocalDate.of(2024, 5, 15), 10);
        explorer.upgrade("cust1", PlanType.STANDARD, LocalDate.of(2024, 9, 1));

        List<MonthlyBill> bills = explorer.getMonthlyBill("cust1", 2024);
        bills.forEach(System.out::println);

        double yearly = explorer.getYearlyEstimate("cust1", 2024);
        System.out.println("Yearly Estimate: $" + yearly);
    }
}
