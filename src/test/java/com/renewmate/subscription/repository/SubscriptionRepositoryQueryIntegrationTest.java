package com.renewmate.subscription.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class SubscriptionRepositoryQueryIntegrationTest {

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

    @Test
    @DisplayName("구독 목록과 카테고리를 fetch join SQL 한 번으로 조회한다")
    void shouldFetchSubscriptionsAndCategoriesWithOneStatement() {
        User user = userRepository.save(User.create(
                "Query Tester",
                "query-test@example.com",
                "encoded-password"
        ));

        for (int index = 1; index <= 3; index++) {
            String categoryName = "Query Test " + index;
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
                    new BigDecimal("10000"),
                    Currency.KRW,
                    BillingCycle.MONTHLY,
                    1,
                    LocalDate.of(2030, index, 1),
                    LocalDate.of(2030, index + 1, 1),
                    true,
                    3,
                    null,
                    null,
                    null
            ));
        }

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<Subscription> subscriptions = subscriptionRepository.findAllWithCategoryByUserId(user.getUserId());
        subscriptions.forEach(subscription -> subscription.getCategory().getName());

        assertEquals(3, subscriptions.size());
        assertEquals(1L, statistics.getPrepareStatementCount());
    }
}
