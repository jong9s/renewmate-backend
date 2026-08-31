package com.renewmate.subscription.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.subscription.dto.SubscriptionCreateRequest;
import com.renewmate.subscription.dto.SubscriptionResponse;
import com.renewmate.subscription.dto.SubscriptionStatusUpdateRequest;
import com.renewmate.subscription.dto.SubscriptionUpdateRequest;
import com.renewmate.subscription.service.SubscriptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
	private final SubscriptionService subscriptionService;
	
	@PostMapping
	// JWT -> userId = 1 -> Authentication -> Controller
	public ResponseEntity<Void> createSubscription(Authentication authentication, 
			@Valid @RequestBody SubscriptionCreateRequest request){
		
		Long userId = (Long) authentication.getPrincipal();
		
		subscriptionService.createSubscription(userId, request);
		
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.build();
	}
	
	@GetMapping
	public ResponseEntity<List<SubscriptionResponse>> getSubscription(Authentication authentication){
			Long userId = (Long) authentication.getPrincipal();
			
			List<SubscriptionResponse> response = subscriptionService.getSubscription(userId);
			
			return ResponseEntity.ok(response);
	}
	
	@GetMapping("/{subscriptionId}")
	public ResponseEntity<SubscriptionResponse> getSubscription(Authentication authentication, @PathVariable("subscriptionId") Long subscriptionId){
		
			Long userId = (Long) authentication.getPrincipal();
			
			SubscriptionResponse response = subscriptionService.getSubscription(userId, subscriptionId);
			
			return ResponseEntity.ok(response);
	}
	
	@PutMapping("/{subscriptionId}")
	public ResponseEntity<Void> updateSubscription(Authentication authentication, 
			@PathVariable("subscriptionId") Long subscriptionId, 
			@Valid @RequestBody SubscriptionUpdateRequest request) {
	    Long userId = (Long) authentication.getPrincipal();

	    subscriptionService.updateSubscription(
	            userId,
	            subscriptionId,
	            request
	    );

	    return ResponseEntity.noContent().build();
	}
	
	@DeleteMapping("/{subscriptionId}")
	public ResponseEntity<Void> deleteSubscription(Authentication authentication,
	        @PathVariable("subscriptionId") Long subscriptionId
	) {
	    Long userId = (Long) authentication.getPrincipal();

	    subscriptionService.deleteSubscription(
	            userId,
	            subscriptionId
	    );

	    return ResponseEntity.noContent().build();
	}
	
	@PatchMapping("/{subscriptionId}/status")
	public ResponseEntity<Void> changeStatus(Authentication authentication,
			@PathVariable("subscriptionId") Long subscriptionId,
			@Valid @RequestBody SubscriptionStatusUpdateRequest request
		) {
			Long userId = (Long) authentication.getPrincipal();
			
			subscriptionService.changeStatus(userId, subscriptionId, request);
			
			return ResponseEntity.noContent().build();
	}
	
	@GetMapping("/upcoming")
	public ResponseEntity<List<SubscriptionResponse>> getUpcomingSubscriptions(Authentication authentication,
	        @RequestParam(name = "days", defaultValue = "7") int days) {
		
	    Long userId = (Long) authentication.getPrincipal();

	    List<SubscriptionResponse> response = subscriptionService.getUpcomingSubscriptions(userId, days);

	    return ResponseEntity.ok(response);
	}
}
