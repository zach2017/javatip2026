package com.example.audit.web.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Response for sync user endpoints.
 * @Value  -> immutable, all-args constructor, getters, equals/hashCode/toString.
 * @Builder -> fluent builder.
 */
@Value
@Builder
public class UserResponse {
    Long id;
    String name;
}
