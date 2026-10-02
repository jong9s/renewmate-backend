package com.renewmate.category.dto;

public record CategoryResponse(
        Long categoryId,
        String name,
        Integer displayOrder
	) {
}