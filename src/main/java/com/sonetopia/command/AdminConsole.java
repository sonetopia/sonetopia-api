package com.sonetopia.command;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.stereotype.Component;

@Component
public class AdminConsole {

  private final BufferedReader fallbackReader = new BufferedReader(new InputStreamReader(System.in));

  private boolean warnedAboutVisiblePassword;

  public String readLine(String prompt) {
    Console console = System.console();

    if (console != null) {
      return console.readLine(prompt);
    }

    System.out.print(prompt);
    return readFallbackLine();
  }

  public char[] readPassword(String prompt) {
    Console console = System.console();

    if (console != null) {
      return console.readPassword(prompt);
    }

    if (!warnedAboutVisiblePassword) {
      System.out.println("Warning: no interactive console detected; password input will be visible.");
      warnedAboutVisiblePassword = true;
    }

    System.out.print(prompt);
    String value = readFallbackLine();
    return value == null ? null : value.toCharArray();
  }

  private String readFallbackLine() {
    try {
      String line = fallbackReader.readLine();

      if (line == null) {
        throw new IllegalStateException("No input available to complete the admin command.");
      }

      return line;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read command line input", e);
    }
  }
}