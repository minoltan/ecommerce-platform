package com.ecommerce.userauth.api.v1.dto;

import com.ecommerce.userauth.domain.User;
import org.springframework.data.domain.Page;

import java.util.List;

public record UserListResponse(List<UserSummaryResponse> users, int page, int pageSize, long totalElements,
                                 int totalPages) {

    public static UserListResponse from(Page<User> page) {
        List<UserSummaryResponse> users = page.getContent().stream().map(UserSummaryResponse::from).toList();
        return new UserListResponse(users, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
