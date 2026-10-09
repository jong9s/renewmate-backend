package com.renewmate.statistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.renewmate.dashboard.service.DashboardService;
import com.renewmate.subscription.dto.SubscriptionCreateRequest;
import com.renewmate.subscription.dto.SubscriptionStatusUpdateRequest;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.SubscriptionStatus;
import com.renewmate.subscription.service.SubscriptionService;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;

/**
 * 캐시 무효화는 트랜잭션 커밋 후에 일어나므로 테스트 트랜잭션(롤백) 없이 실제로 커밋하고 직접 정리한다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class SpendingCacheIntegrationTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long userId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create(
                "Spending Cache Tester",
                "spending-cache-test@example.com",
                "encoded-password"
        ));
        userId = user.getUserId();

        jdbcTemplate.update("""
                INSERT INTO categories
                    (name, display_order, active, created_at, updated_at)
                VALUES
                    (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Spending Cache", 1, true);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM categories WHERE name = ?",
                Long.class,
                "Spending Cache"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM subscriptions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM categories WHERE category_id = ?", categoryId);
    }

    @Test
    @DisplayName("같은 사용자의 통계를 다시 조회하면 SQL 없이 캐시에서 반환한다")
    void shouldServeRepeatedStatisticsFromCache() {
        createSubscription("Cached Service", "10000");
        statisticsService.getSummary(userId);
        dashboardService.getSummary(userId);

        Statistics statistics = statistics();
        statisticsService.getSummary(userId);
        dashboardService.getSummary(userId);

        assertEquals(0L, statistics.getPrepareStatementCount());
    }

    @Test
    @DisplayName("구독을 추가·상태 변경·삭제하면 해당 사용자의 통계 캐시를 비운다")
    void shouldEvictStatisticsWhenSubscriptionsChange() {
        createSubscription("First Service", "10000");
        assertEquals(1, statisticsService.getSummary(userId).activeSubscriptionCount());
        assertEquals(1, dashboardService.getSummary(userId).activeSubscriptionCount());

        createSubscription("Second Service", "20000");
        assertEquals(2, statisticsService.getSummary(userId).activeSubscriptionCount());
        assertEquals(0, new BigDecimal("30000.00").compareTo(statisticsService.getSummary(userId).totals().get(0).monthlyAmount()));
        assertEquals(2, dashboardService.getSummary(userId).activeSubscriptionCount());

        Long firstId = subscriptionId("First Service");
        subscriptionService.changeStatus(userId, firstId, new SubscriptionStatusUpdateRequest(SubscriptionStatus.INACTIVE));
        assertEquals(1, statisticsService.getSummary(userId).activeSubscriptionCount());

        subscriptionService.deleteSubscription(userId, subscriptionId("Second Service"));
        assertEquals(0, statisticsService.getSummary(userId).activeSubscriptionCount());
        assertEquals(0, statisticsService.getServiceStatistics(userId).size());
        assertEquals(0, statisticsService.getCategoryStatistics(userId).size());
        assertEquals(0, dashboardService.getSummary(userId).activeSubscriptionCount());
    }

    private void createSubscription(String serviceName, String amount) {
        subscriptionService.createSubscription(userId, new SubscriptionCreateRequest(
                serviceName,
                new BigDecimal(amount),
                Currency.KRW,
                BillingCycle.MONTHLY,
                1,
                LocalDate.now(),
                true,
                3,
                null,
                null,
                null,
                categoryId
        ));
    }

    private Long subscriptionId(String serviceName) {
        return jdbcTemplate.queryForObject(
                "SELECT subscription_id FROM subscriptions WHERE user_id = ? AND service_name = ?",
                Long.class,
                userId,
                serviceName
        );
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
