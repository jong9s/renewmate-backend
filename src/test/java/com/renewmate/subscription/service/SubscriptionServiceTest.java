
package com.renewmate.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.renewmate.category.entity.Category;
import com.renewmate.category.repository.CategoryRepository;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.subscription.dto.SubscriptionCreateRequest;
import com.renewmate.subscription.dto.SubscriptionUpdateRequest;
import com.renewmate.subscription.entity.BillingCycle;
import com.renewmate.subscription.entity.Currency;
import com.renewmate.subscription.entity.Subscription;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	@Mock
	private SubscriptionRepository subscriptionRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private SubscriptionService subscriptionService;

	@Test
	@DisplayName("다른 사용자의 구독은 삭제할 수 없다")
	void shouldNotDeleteAnotherUsersSubscription() {

		// given
		Long otherUserId = 2L;
		Long subscriptionId = 1L;

		when(subscriptionRepository.findBySubscriptionIdAndUser_UserId(subscriptionId, otherUserId))
				.thenReturn(Optional.empty());

		// when
		BusinessException exception = assertThrows(BusinessException.class,
				() -> subscriptionService.deleteSubscription(otherUserId, subscriptionId));

		// then
		assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.getErrorCode());

		verify(notificationRepository, never()).deleteAllByUser_UserIdAndSubscription_SubscriptionId(any(), any());

		verify(subscriptionRepository, never()).delete(any());
	}

	@Test
	@DisplayName("본인의 구독을 삭제하면 연결된 알림을 먼저 삭제한다")
	void shouldDeleteNotificationsBeforeSubscription() {

		// given
		Long userId = 1L;
		Long subscriptionId = 10L;

		Subscription subscription = org.mockito.Mockito.mock(Subscription.class);

		when(subscriptionRepository.findBySubscriptionIdAndUser_UserId(subscriptionId, userId))
				.thenReturn(Optional.of(subscription));

		// when
		subscriptionService.deleteSubscription(userId, subscriptionId);

		// then: 메서드 호출 순서 확인
		InOrder inOrder = inOrder(notificationRepository, subscriptionRepository);

		inOrder.verify(notificationRepository).deleteAllByUser_UserIdAndSubscription_SubscriptionId(userId,
				subscriptionId);

		inOrder.verify(subscriptionRepository).delete(subscription);
	}

	@Test
	@DisplayName("존재하지 않는 구독은 삭제할 수 없다")
	void shouldNotDeleteNonexistentSubscription() {

		// given
		Long userId = 1L;
		Long nonexistentSubscriptionId = 999999L;

		when(subscriptionRepository.findBySubscriptionIdAndUser_UserId(nonexistentSubscriptionId, userId))
				.thenReturn(Optional.empty());

		// when
		BusinessException exception = assertThrows(BusinessException.class,
				() -> subscriptionService.deleteSubscription(userId, nonexistentSubscriptionId));

		// then
		assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.getErrorCode());

		verify(notificationRepository, never()).deleteAllByUser_UserIdAndSubscription_SubscriptionId(any(), any());

		verify(subscriptionRepository, never()).delete(any());
	}

	@Test
	@DisplayName("구독 생성 시 입력 정보와 다음 결제일을 저장한다")
	void shouldCreateSubscriptionWithNextBillingDate() {

		// Given
		Long userId = 1L;
		Long categoryId = 1L;

		User user = org.mockito.Mockito.mock(User.class);
		Category category = org.mockito.Mockito.mock(Category.class);

		LocalDate startDate = LocalDate.now().plusMonths(1).withDayOfMonth(1);
		LocalDate expectedNextBillingDate = startDate.plusMonths(1);

		SubscriptionCreateRequest request = new SubscriptionCreateRequest("Netflix", new BigDecimal("17000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, startDate, true, 3, "카드", "https://netflix.com", "테스트",
				categoryId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		when(categoryRepository.findByCategoryIdAndActiveTrue(categoryId)).thenReturn(Optional.of(category));

		// When
		subscriptionService.createSubscription(userId, request);

		// Then
		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

		verify(subscriptionRepository).save(captor.capture());

		Subscription savedSubscription = captor.getValue();

		assertEquals("Netflix", savedSubscription.getServiceName());
		assertEquals(0, new BigDecimal("17000").compareTo(savedSubscription.getAmount()));
		assertEquals(Currency.KRW, savedSubscription.getCurrency());
		assertEquals(expectedNextBillingDate, savedSubscription.getNextBillingDate());
		assertEquals(user, savedSubscription.getUser());
		assertEquals(category, savedSubscription.getCategory());
	}

	@Test
	@DisplayName("1월 31일에 시작한 월간 구독은 2월 말일에 결제된다")
	void shouldCalculateLastDayOfFebruary() {

		// Given
		Long userId = 1L;
		Long categoryId = 1L;

		User user = org.mockito.Mockito.mock(User.class);
		Category category = org.mockito.Mockito.mock(Category.class);

		SubscriptionCreateRequest request = new SubscriptionCreateRequest("Month-end Test", new BigDecimal("9900"),
				Currency.KRW, BillingCycle.MONTHLY, 1, LocalDate.of(2031, 1, 31), true, 3, "카드", null, null,
				categoryId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		when(categoryRepository.findByCategoryIdAndActiveTrue(categoryId)).thenReturn(Optional.of(category));

		// When
		subscriptionService.createSubscription(userId, request);

		// Then
		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

		verify(subscriptionRepository).save(captor.capture());

		assertEquals(LocalDate.of(2031, 2, 28), captor.getValue().getNextBillingDate());
	}

	@Test
	@DisplayName("윤년 2월 29일에 시작한 연간 구독의 다음 결제일을 계산한다")
	void shouldCalculateYearlyBillingAfterLeapDay() {

		// Given
		Long userId = 1L;
		Long categoryId = 1L;

		User user = org.mockito.Mockito.mock(User.class);
		Category category = org.mockito.Mockito.mock(Category.class);

		SubscriptionCreateRequest request = new SubscriptionCreateRequest("Yearly Test", new BigDecimal("120000"),
				Currency.KRW, BillingCycle.YEARLY, 1, LocalDate.of(2028, 2, 29), true, 3, "카드", null, null, categoryId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		when(categoryRepository.findByCategoryIdAndActiveTrue(categoryId)).thenReturn(Optional.of(category));

		// When
		subscriptionService.createSubscription(userId, request);

		// Then
		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

		verify(subscriptionRepository).save(captor.capture());

		assertEquals(LocalDate.of(2029, 2, 28), captor.getValue().getNextBillingDate());
	}

	@Test
	@DisplayName("구독 수정 시 변경된 정보와 결제일을 반영한다")
	void shouldUpdateSubscription() {

		// Given
		Long userId = 1L;
		Long subscriptionId = 10L;
		Long categoryId = 2L;

		User user = org.mockito.Mockito.mock(User.class);
		Category originalCategory = org.mockito.Mockito.mock(Category.class);
		Category newCategory = org.mockito.Mockito.mock(Category.class);

		Subscription subscription = Subscription.create(user, originalCategory, "Netflix", new BigDecimal("17000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), true, 3,
				"카드", null, null);

		LocalDate updatedStartDate = LocalDate.now().plusMonths(1).withDayOfMonth(1);

		SubscriptionUpdateRequest request = new SubscriptionUpdateRequest("Netflix Premium", new BigDecimal("20000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, updatedStartDate, true, 5, "새 카드", "https://netflix.com",
				"요금제 변경", categoryId);

		when(subscriptionRepository.findBySubscriptionIdAndUser_UserId(subscriptionId, userId))
				.thenReturn(Optional.of(subscription));

		when(categoryRepository.findByCategoryIdAndActiveTrue(categoryId)).thenReturn(Optional.of(newCategory));

		// When
		subscriptionService.updateSubscription(userId, subscriptionId, request);

		// Then
		assertEquals("Netflix Premium", subscription.getServiceName());
		assertEquals(0, new BigDecimal("20000").compareTo(subscription.getAmount()));
		assertEquals(newCategory, subscription.getCategory());
		assertEquals(5, subscription.getReminderDays());
		assertEquals(updatedStartDate.plusMonths(1), subscription.getNextBillingDate());
	}

	@Test
	@DisplayName("존재하지 않는 사용자는 구독을 생성할 수 없다")
	void shouldRejectSubscriptionCreationForUnknownUser() {

		// Given
		Long userId = 999L;

		SubscriptionCreateRequest request = new SubscriptionCreateRequest("Netflix", new BigDecimal("17000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, LocalDate.of(2031, 1, 1), true, 3, "카드", null, null, 1L);

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		// When
		BusinessException exception = assertThrows(BusinessException.class,
				() -> subscriptionService.createSubscription(userId, request));

		// Then
		assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());

		verify(categoryRepository, never()).findByCategoryIdAndActiveTrue(any());

		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	@DisplayName("존재하지 않는 카테고리로 구독을 생성할 수 없다")
	void shouldRejectSubscriptionCreationForUnknownCategory() {

		// Given
		Long userId = 1L;
		Long categoryId = 999L;

		User user = org.mockito.Mockito.mock(User.class);

		SubscriptionCreateRequest request = new SubscriptionCreateRequest("Netflix", new BigDecimal("17000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, LocalDate.of(2031, 1, 1), true, 3, "카드", null, null, categoryId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		when(categoryRepository.findByCategoryIdAndActiveTrue(categoryId)).thenReturn(Optional.empty());

		// When
		BusinessException exception = assertThrows(BusinessException.class,
				() -> subscriptionService.createSubscription(userId, request));

		// Then
		assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());

		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	@DisplayName("다른 사용자의 구독은 수정할 수 없다")
	void shouldNotUpdateAnotherUsersSubscription() {

		// Given
		Long otherUserId = 2L;
		Long subscriptionId = 10L;

		SubscriptionUpdateRequest request = new SubscriptionUpdateRequest("Netflix Premium", new BigDecimal("20000"),
				Currency.KRW, BillingCycle.MONTHLY, 1, LocalDate.of(2031, 1, 1), true, 5, "새 카드", null, null, 1L);

		when(subscriptionRepository.findBySubscriptionIdAndUser_UserId(subscriptionId, otherUserId))
				.thenReturn(Optional.empty());

		// When
		BusinessException exception = assertThrows(BusinessException.class,
				() -> subscriptionService.updateSubscription(otherUserId, subscriptionId, request));

		// Then
		assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.getErrorCode());

		verify(categoryRepository, never()).findByCategoryIdAndActiveTrue(any());

		verify(subscriptionRepository, never()).save(any());
	}
}