package com.renewmate.statistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.dashboard.service.DashboardService;
import com.renewmate.statistics.dto.CategoryStatisticsResponse;
import com.renewmate.statistics.dto.StatisticsSummaryResponse;
import com.renewmate.subscription.dto.CurrencyAmountResponse;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class SpendingQueryIntegrationTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(
                "Spending Query Tester",
                "spending-query-test@example.com",
                "encoded-password"
        ));

        // 서로 다른 카테고리 3개에 구독을 하나씩 두고, 결제일을 역순으로 저장한다
        for (int index = 1; index <= 3; index++) {
            String categoryName = "Spending Query " + index;
            jdbcTemplate.update("""
                    INSERT INTO categories
                        (name, display_order, active, created_at, updated_at)
                    VALUES
                        (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, categoryName, index, true);
            Long categoryId = jdbcTemplate.queryForObject(
                    "SELECT category_id FROM categories WHERE name = ?",
                    Long.class,
                    categoryName
            );
            Category category = categoryRepository.findById(categoryId).orElseThrow();
            subscriptionRepository.save(Subscription.create(
                    user,
                    category,
                    "Subscription " + index,
                    new BigDecimal(index * 10000),
                    Currency.KRW,
                    BillingCycle.MONTHLY,
                    1,
                    LocalDate.now().minusMonths(1),
                    LocalDate.now().plusDays(20 - index * 5L),
                    true,
                    3,
                    null,
                    null,
                    null
            ));
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("카테고리별 통계는 구독과 카테고리를 SQL 한 번으로 조회한다")
    void shouldCalculateCategoryStatisticsWithOneStatement() {
        Statistics statistics = statistics();

        List<CategoryStatisticsResponse> response = statisticsService.getCategoryStatistics(user.getUserId());

        assertEquals(3, response.size());
        assertEquals("Spending Query 3", response.get(0).categoryName());
        assertEquals(1L, statistics.getPrepareStatementCount());
    }

    @Test
    @DisplayName("통화가 다른 구독은 환산하지 않고 통화별로 따로 합산한다")
    void shouldSummarizeSpendingPerCurrency() {
        Category firstCategory = categoryRepository.findAll().stream()
                .filter(category -> category.getName().equals("Spending Query 1"))
                .findFirst()
                .orElseThrow();
        subscriptionRepository.save(Subscription.create(
                userRepository.findById(user.getUserId()).orElseThrow(),
                firstCategory,
                "Yearly USD",
                new BigDecimal("120"),
                Currency.USD,
                BillingCycle.YEARLY,
                1,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(11),
                true,
                3,
                null,
                null,
                null
        ));
        entityManager.flush();
        entityManager.clear();

        StatisticsSummaryResponse summary = statisticsService.getSummary(user.getUserId());

        assertEquals(4, summary.activeSubscriptionCount());
        assertEquals(List.of(Currency.KRW, Currency.USD),
                summary.totals().stream().map(CurrencyAmountResponse::currency).toList());
        assertEquals(0, new BigDecimal("60000.00").compareTo(summary.totals().get(0).monthlyAmount()));
        assertEquals(0, new BigDecimal("10.00").compareTo(summary.totals().get(1).monthlyAmount()));

        List<CategoryStatisticsResponse> categories = statisticsService.getCategoryStatistics(user.getUserId());

        // 같은 카테고리라도 통화가 다르면 별도 항목, KRW 항목이 먼저 온다
        assertEquals(4, categories.size());
        assertEquals(Currency.USD, categories.get(3).currency());
        assertEquals("Spending Query 1", categories.get(3).categoryName());
    }

    @Test
    @DisplayName("대시보드 결제 예정 목록은 카테고리를 포함해 SQL 한 번으로 결제일 순 조회한다")
    void shouldFetchUpcomingWithCategoryInOneStatement() {
        Statistics statistics = statistics();

        List<SubscriptionResponse> response = dashboardService.getUpcoming(user.getUserId(), 5);

        assertEquals(List.of("Subscription 3", "Subscription 2", "Subscription 1"),
                response.stream().map(SubscriptionResponse::serviceName).toList());
        assertEquals(1L, statistics.getPrepareStatementCount());
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
