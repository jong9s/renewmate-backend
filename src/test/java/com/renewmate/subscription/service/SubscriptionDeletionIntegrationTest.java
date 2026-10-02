package com.renewmate.subscription.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.notification.entity.Notification;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionDeletionIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("구독 삭제 시 연결된 알림도 함께 삭제된다")
    void shouldDeleteSubscriptionAndRelatedNotifications() {

        // Given: 사용자 저장
        User user = userRepository.save(
                User.create(
                        "Integration Tester",
                        "integration-test@example.com",
                        "encoded-password"
                )
        );

        // Given: 카테고리 저장
        jdbcTemplate.update("""
                INSERT INTO categories
                    (name, display_order, active, created_at, updated_at)
                VALUES
                    (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "Integration Test",
                1,
                true
        );

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM categories WHERE name = ?",
                Long.class,
                "Integration Test"
        );

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow();

        // Given: 구독 저장
        Subscription subscription = subscriptionRepository.save(
                Subscription.create(
                        user,
                        category,
                        "Netflix Test",
                        new BigDecimal("17000"),
                        Currency.KRW,
                        BillingCycle.MONTHLY,
                        1,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        true,
                        3,
                        "Test Card",
                        null,
                        "Integration test"
                )
        );

        Long subscriptionId = subscription.getSubscriptionId();

        // Given: 구독을 참조하는 알림 2개 저장
        Notification firstNotification = notificationRepository.save(
                Notification.create(
                        user,
                        subscription,
                        "첫 번째 결제 알림"
                )
        );

        Notification secondNotification = notificationRepository.save(
                Notification.create(
                        user,
                        subscription,
                        "두 번째 결제 알림"
                )
        );

        Long firstNotificationId = firstNotification.getNotificationId();
        Long secondNotificationId = secondNotification.getNotificationId();

        // Given: 삭제 전에는 구독과 알림이 모두 존재해야 함
        assertTrue(subscriptionRepository.existsById(subscriptionId));
        assertTrue(notificationRepository.existsById(firstNotificationId));
        assertTrue(notificationRepository.existsById(secondNotificationId));

        // When: 실제 서비스의 구독 삭제 실행
        subscriptionService.deleteSubscription(
                user.getUserId(),
                subscriptionId
        );

        // DB에 삭제 SQL을 반영하고 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // Then: 구독 삭제 확인
        assertFalse(
                subscriptionRepository.existsById(subscriptionId)
        );

        // Then: 해당 구독의 알림 2개도 삭제됐는지 확인
        assertFalse(
                notificationRepository.existsById(firstNotificationId)
        );

        assertFalse(
                notificationRepository.existsById(secondNotificationId)
        );
    }
}