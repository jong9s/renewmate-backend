package com.renewmate.subscription.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.global.security.JwtProvider;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubscriptionApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("인증되지 않은 구독 목록 요청은 401을 반환한다")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 사용자의 구독 ID로 조회해도 데이터를 노출하지 않는다")
    void shouldNotExposeAnotherUsersSubscription() throws Exception {
        User owner = userRepository.save(User.create(
                "Owner",
                "subscription-owner@example.com",
                "encoded-password"
        ));
        User requester = userRepository.save(User.create(
                "Requester",
                "subscription-requester@example.com",
                "encoded-password"
        ));

        jdbcTemplate.update("""
                INSERT INTO categories
                    (name, display_order, active, created_at, updated_at)
                VALUES
                    (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "API Security Test", 1, true);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM categories WHERE name = ?",
                Long.class,
                "API Security Test"
        );
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        Subscription subscription = subscriptionRepository.save(Subscription.create(
                owner,
                category,
                "Private Subscription",
                new BigDecimal("9900"),
                Currency.KRW,
                BillingCycle.MONTHLY,
                1,
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1),
                true,
                3,
                "Test Card",
                null,
                null
        ));
        entityManager.flush();

        String token = jwtProvider.createAccessToken(requester.getUserId(), requester.getEmail());

        mockMvc.perform(get("/api/subscriptions/{subscriptionId}", subscription.getSubscriptionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SUBSCRIPTION_NOT_FOUND"));
    }
}
