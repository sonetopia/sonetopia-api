package com.sonetopia.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonetopia.entity.User;
import com.sonetopia.model.UpdateUserRequest;
import com.sonetopia.model.UserResponse;
import com.sonetopia.model.WebResponse;
import com.sonetopia.model.StorageUsageResponse;
import com.sonetopia.service.StorageQuotaService;
import com.sonetopia.service.UserService;

@RestController
@RequestMapping("/api/me")
public class UserController {

  @Autowired
  private UserService userService;

  @Autowired
  private StorageQuotaService storageQuotaService;

  @GetMapping(produces = "application/json")
  public WebResponse<UserResponse> get(@AuthenticationPrincipal User user) {
    UserResponse userResponse = userService.get(user);
    return WebResponse.<UserResponse>builder().data(userResponse).build();
  }

  @PutMapping(consumes = "application/json", produces = "application/json")
  public WebResponse<UserResponse> update(
      @AuthenticationPrincipal User user,
      @RequestBody UpdateUserRequest request
  ) {
    UserResponse userResponse = userService.update(user, request);
    return WebResponse.<UserResponse>builder().data(userResponse).build();
  }

  @GetMapping(path = "/storage", produces = "application/json")
  public WebResponse<StorageUsageResponse> storage(@AuthenticationPrincipal User user) {
    return WebResponse.<StorageUsageResponse>builder()
        .data(storageQuotaService.getUsage(user))
        .build();
  }
}
