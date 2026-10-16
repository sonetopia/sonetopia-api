package com.sonetopia.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sonetopia.entity.User;
import com.sonetopia.repository.UserRepository;
import com.sonetopia.service.ValidationService;

class AdminCommandRunnerTest {

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private ValidationService validationService;
  private AdminConsole console;
  private AdminCommandRunner runner;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    validationService = mock(ValidationService.class);
    console = mock(AdminConsole.class);
    runner = new AdminCommandRunner(userRepository, passwordEncoder, validationService, console);
  }

  @Test
  void testNoAdminCommandDoesNothing() {
    ApplicationArguments args = mock(ApplicationArguments.class);
    when(args.containsOption(AdminCommandRunner.CREATE_ADMIN_OPTION)).thenReturn(false);
    when(args.containsOption(AdminCommandRunner.PROMOTE_ADMIN_OPTION)).thenReturn(false);

    runner.run(args);

    verifyNoInteractions(userRepository, passwordEncoder, validationService, console);
  }

  @Test
  void testCreateAdminSuccess() {
    ApplicationArguments args = createAdminArgs();
    stubCreatePrompts("admin", "admin@example.com", "secret123");

    when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

    runner.run(args);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    verify(validationService).validate(any());

    User saved = captor.getValue();
    assertEquals("admin", saved.getUsername());
    assertEquals("admin@example.com", saved.getEmail());
    assertEquals("encoded-secret", saved.getPassword());
    assertEquals(Long.valueOf(0L), saved.getStorageUsed());
    assertTrue(saved.getIsAdmin());
    assertNotNull(saved.getCreatedAt());
    assertNotNull(saved.getUpdatedAt());
  }

  @Test
  void testCreateAdminRepromptsForBlankUsername() {
    ApplicationArguments args = createAdminArgs();
    stubCreatePrompts("admin", "admin@example.com", "secret123");
    when(console.readLine("Username: ")).thenReturn("", "admin");

    when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

    runner.run(args);

    verify(userRepository).save(any());
  }

  @Test
  void testCreateAdminRepromptsWhenPasswordsDoNotMatch() {
    ApplicationArguments args = createAdminArgs();
    when(console.readLine("Username: ")).thenReturn("admin");
    when(console.readLine("Email: ")).thenReturn("admin@example.com");
    when(console.readPassword("Password: "))
      .thenReturn("first-password".toCharArray(), "secret123".toCharArray());
    when(console.readPassword("Password (again): "))
      .thenReturn("different-password".toCharArray(), "secret123".toCharArray());

    when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

    runner.run(args);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertEquals("encoded-secret", captor.getValue().getPassword());
  }

  @Test
  void testCreateAdminRepromptsWhenUsernameExists() {
    ApplicationArguments args = createAdminArgs();
    when(console.readLine("Username: ")).thenReturn("admin", "newadmin");
    when(console.readLine("Email: ")).thenReturn("first@example.com", "new@example.com");
    when(console.readPassword("Password: "))
      .thenReturn("secret123".toCharArray(), "secret123".toCharArray());
    when(console.readPassword("Password (again): "))
      .thenReturn("secret123".toCharArray(), "secret123".toCharArray());

    when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));
    when(userRepository.findByUsername("newadmin")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

    runner.run(args);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertEquals("newadmin", captor.getValue().getUsername());
  }

  @Test
  void testCreateAdminFailsWhenInputIsExhausted() {
    ApplicationArguments args = createAdminArgs();
    when(console.readLine("Username: ")).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> runner.run(args));
    verify(userRepository, never()).save(any());
  }

  @Test
  void testPromoteExistingUser() {
    User user = new User();
    user.setUsername("test");
    user.setIsAdmin(false);

    ApplicationArguments args = promoteAdminArgs();
    when(console.readLine("Username to promote: ")).thenReturn("test");
    when(userRepository.findByUsername("test")).thenReturn(Optional.of(user));

    runner.run(args);

    assertTrue(user.getIsAdmin());
    assertNotNull(user.getUpdatedAt());
    verify(userRepository).save(user);
  }

  @Test
  void testPromoteRepromptsWhenUserIsNotFound() {
    User user = new User();
    user.setUsername("test");
    user.setIsAdmin(false);

    ApplicationArguments args = promoteAdminArgs();
    when(console.readLine("Username to promote: ")).thenReturn("missing", "test");
    when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
    when(userRepository.findByUsername("test")).thenReturn(Optional.of(user));

    runner.run(args);

    assertTrue(user.getIsAdmin());
    verify(userRepository).save(user);
  }

  @Test
  void testPromoteUserThatIsAlreadyAdminDoesNothing() {
    User user = new User();
    user.setUsername("test");
    user.setIsAdmin(true);

    ApplicationArguments args = promoteAdminArgs();
    when(console.readLine("Username to promote: ")).thenReturn("test");
    when(userRepository.findByUsername("test")).thenReturn(Optional.of(user));

    runner.run(args);

    verify(userRepository, never()).save(any());
  }

  private ApplicationArguments createAdminArgs() {
    ApplicationArguments args = mock(ApplicationArguments.class);
    when(args.containsOption(AdminCommandRunner.CREATE_ADMIN_OPTION)).thenReturn(true);
    when(args.containsOption(AdminCommandRunner.PROMOTE_ADMIN_OPTION)).thenReturn(false);
    return args;
  }

  private ApplicationArguments promoteAdminArgs() {
    ApplicationArguments args = mock(ApplicationArguments.class);
    when(args.containsOption(AdminCommandRunner.CREATE_ADMIN_OPTION)).thenReturn(false);
    when(args.containsOption(AdminCommandRunner.PROMOTE_ADMIN_OPTION)).thenReturn(true);
    return args;
  }

  private void stubCreatePrompts(String username, String email, String password) {
    when(console.readLine("Username: ")).thenReturn(username);
    when(console.readLine("Email: ")).thenReturn(email);
    when(console.readPassword("Password: ")).thenReturn(password.toCharArray());
    when(console.readPassword("Password (again): ")).thenReturn(password.toCharArray());
  }
}