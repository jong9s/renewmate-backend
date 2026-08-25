package com.renewmate.subscription.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.renewmate.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "subscription_id")
	private Long subscriptionId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "service_name", nullable = false, length = 100)
	private String serviceName;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Currency currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_cycle", nullable = false, length = 30)
	private BillingCycle billingCycle;

	@Column(name = "billing_interval", nullable = false)
	private Integer billingInterval;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "next_billing_date", nullable = false)
	private LocalDate nextBillingDate;

	@Column(name = "auto_renew", nullable = false)
	private Boolean autoRenew;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(name = "reminder_days")
	private Integer reminderDays;

	@Column(name = "payment_method", length = 100)
	private String paymentMethod;

	@Column(name = "service_url", length = 500)
	private String serviceUrl;

	@Column(length = 1000)
	private String memo;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	public static Subscription create(
	        User user,
	        String serviceName,
	        BigDecimal amount,
	        Currency currency,
	        BillingCycle billingCycle,
	        Integer billingInterval,
	        LocalDate startDate,
	        LocalDate nextBillingDate,
	        Boolean autoRenew,
	        Integer reminderDays,
	        String paymentMethod,
	        String serviceUrl,
	        String memo
	) {
	    Subscription subscription = new Subscription();

	    subscription.user = user;
	    subscription.serviceName = serviceName;
	    subscription.amount = amount;
	    subscription.currency = currency;
	    subscription.billingCycle = billingCycle;
	    subscription.billingInterval = billingInterval;
	    subscription.startDate = startDate;
	    subscription.nextBillingDate = nextBillingDate;
	    subscription.autoRenew = autoRenew;
	    subscription.status = SubscriptionStatus.ACTIVE;
	    subscription.reminderDays = reminderDays;
	    subscription.paymentMethod = paymentMethod;
	    subscription.serviceUrl = serviceUrl;
	    subscription.memo = memo;
	    subscription.createdAt = LocalDateTime.now();
	    subscription.updatedAt = LocalDateTime.now();

	    return subscription;
	}
	
	public void update(
	        String serviceName,
	        BigDecimal amount,
	        Currency currency,
	        BillingCycle billingCycle,
	        Integer billingInterval,
	        LocalDate startDate,
	        LocalDate nextBillingDate,
	        Boolean autoRenew,
	        Integer reminderDays,
	        String paymentMethod,
	        String serviceUrl,
	        String memo
	) {
	    this.serviceName = serviceName;
	    this.amount = amount;
	    this.currency = currency;
	    this.billingCycle = billingCycle;
	    this.billingInterval = billingInterval;
	    this.startDate = startDate;
	    this.nextBillingDate = nextBillingDate;
	    this.autoRenew = autoRenew;
	    this.reminderDays = reminderDays;
	    this.paymentMethod = paymentMethod;
	    this.serviceUrl = serviceUrl;
	    this.memo = memo;
	    this.updatedAt = LocalDateTime.now();
	}

}


