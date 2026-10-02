package com.renewmate.user.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.notification.entity.Notification;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.settings.entity.UserSettings;
import com.renewmate.settings.repository.UserSettingsRepository;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.dto.UserWithdrawalRequest;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserWithdrawalIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("회원 탈퇴 시 사용자와 모든 연관 데이터를 삭제한다")
    void shouldDeleteUserAndAllRelatedData() {
        User user = userRepository.save(
                User.create("Withdrawal Tester", "withdrawal@example.com", passwordEncoder.encode("password123"))
        );
        UserSettings settings = userSettingsRepository.save(UserSettings.create(user));

        jdbcTemplate.update("""
                INSERT INTO categories
                    (name, display_order, active, created_at, updated_at)
                VALUES
                    (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Withdrawal Test", 99, true);

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM categories WHERE name = ?",
                Long.class,
                "Withdrawal Test"
        );
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        Subscription subscription = subscriptionRepository.save(
                Subscription.create(
                        user,
                        category,
                        "Withdrawal Subscription",
                        new BigDecimal("9900"),
                        Currency.KRW,
                        BillingCycle.MONTHLY,
                        1,
                        LocalDate.of(2031, 1, 1),
                        LocalDate.of(2031, 2, 1),
                        true,
                        3,
                        "Test Card",
                        null,
                        null
                )
        );
        Notification notification = notificationRepository.save(
                Notification.create(user, subscription, "탈퇴 테스트 알림")
        );

        Long userId = user.getUserId();
        Long subscriptionId = subscription.getSubscriptionId();
        Long notificationId = notification.getNotificationId();
        Long settingId = settings.getSettingId();

        entityManager.flush();
        userService.withdraw(userId, new UserWithdrawalRequest("password123"));
        entityManager.flush();
        entityManager.clear();

        assertFalse(notificationRepository.existsById(notificationId));
        assertFalse(subscriptionRepository.existsById(subscriptionId));
        assertFalse(userSettingsRepository.existsById(settingId));
        assertFalse(userRepository.existsById(userId));
    }
}
