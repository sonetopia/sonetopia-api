package com.sonetopia.command;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sonetopia.entity.User;
import com.sonetopia.model.RegisterRequest;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.service.ValidationService;

import jakarta.validation.ConstraintViolationException;

@Component
public class AdminCommandRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminCommandRunner.class);

  public static final String CREATE_ADMIN_OPTION = "create-admin";
  public static final String PROMOTE_ADMIN_OPTION = "promote-admin";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ValidationService validationService;
  private final AdminConsole console;

  public AdminCommandRunner(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ValidationService validationService,
      AdminConsole console
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.validationService = validationService;
    this.console = console;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    boolean createAdmin = args.containsOption(CREATE_ADMIN_OPTION);
    boolean promoteAdmin = args.containsOption(PROMOTE_ADMIN_OPTION);

    if (!createAdmin && !promoteAdmin) {
      return;
    }

    if (createAdmin) {
      createAdmin();
    }

    if (promoteAdmin) {
      promoteAdmin();
    }
  }

  private void createAdmin() {
    while (true) {
      System.out.println("Creating a new admin user. Press Ctrl+C to cancel.");
      String name = readRequiredLine("Name: ");
      String username = readRequiredLine("Username: ");
      String email = readRequiredLine("Email: ");
      String password = readConfirmedPassword();

      if (userRepository.findByUsername(username).isPresent()) {
        System.out.println("That username is already taken. Try again.");
        continue;
      }

      if (userRepository.findByEmail(email).isPresent()) {
        System.out.println("That email is already in use. Try again.");
        continue;
      }

      RegisterRequest request = RegisterRequest.builder()
          .name(name)
          .username(username)
          .email(email)
          .password(password)
          .build();

      try {
        validationService.validate(request);
      } catch (ConstraintViolationException exception) {
        System.out.println("Invalid input: " + exception.getMessage());
        continue;
      }

      LocalDateTime now = LocalDateTime.now();

      User user = new User();
      user.setUsername(request.getUsername());
      user.setName(request.getName());
      user.setEmail(request.getEmail());
      user.setPassword(passwordEncoder.encode(request.getPassword()));
      user.setStorageUsed(0L);
      user.setIsAdmin(true);
      user.setEmailVerifiedAt(now);
      user.setCreatedAt(now);
      user.setUpdatedAt(now);

      userRepository.save(user);

      log.info("Admin user '{}' created.", user.getUsername());
      return;
    }
  }

  private void promoteAdmin() {
    while (true) {
      String username = readRequiredLine("Username to promote: ");
      User user = userRepository.findByUsername(username).orElse(null);

      if (user == null) {
        System.out.println("User not found. Try again.");
        continue;
      }

      if (Boolean.TRUE.equals(user.getIsAdmin())) {
        log.info("User '{}' is already an admin.", username);
        return;
      }

      user.setIsAdmin(true);
      user.setUpdatedAt(LocalDateTime.now());
      userRepository.save(user);

      log.info("User '{}' promoted to admin.", username);
      return;
    }
  }

  private String readRequiredLine(String prompt) {
    while (true) {
      String value = console.readLine(prompt);

      if (value == null) {
        throw new IllegalStateException("No input available to complete the admin command.");
      }

      value = value.trim();

      if (!value.isEmpty()) {
        return value;
      }

      System.out.println("Value cannot be empty.");
    }
  }

  private String readConfirmedPassword() {
    while (true) {
      char[] password = console.readPassword("Password: ");
      char[] confirmation = console.readPassword("Password (again): ");

      if (password == null || confirmation == null) {
        throw new IllegalStateException("No input available to complete the admin command.");
      }

      boolean matches = Arrays.equals(password, confirmation);

      Arrays.fill(confirmation, '\0');

      if (matches) {
        String value = new String(password);
        Arrays.fill(password, '\0');
        return value;
      }

      Arrays.fill(password, '\0');
      System.out.println("Passwords do not match. Try again.");
    }
  }
}