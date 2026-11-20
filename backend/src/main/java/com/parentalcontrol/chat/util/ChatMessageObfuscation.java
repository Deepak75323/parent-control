package com.parentalcontrol.chat.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Matches web {@code chatMessageObfuscation.ts}: each word becomes 3 random letters + word + 3
 * random letters so locked chat bubbles show padded text (not plaintext).
 */
public final class ChatMessageObfuscation {

  private static final int LETTERS_PER_SIDE = 3;
  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

  private ChatMessageObfuscation() {}

  public static String encode(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return "";
    }
    String[] lines = plaintext.split("\n", -1);
    List<String> encoded = new ArrayList<>(lines.length);
    for (String line : lines) {
      encoded.add(encodeLine(line));
    }
    return String.join("\n", encoded);
  }

  private static String encodeLine(String line) {
    int leadingCount = 0;
    while (leadingCount < line.length() && Character.isWhitespace(line.charAt(leadingCount))) {
      leadingCount++;
    }
    String leading = line.substring(0, leadingCount);
    String core = line.substring(leadingCount).replaceAll("\\s+$", "");
    if (core.isEmpty()) {
      return leading;
    }
    String[] words = core.trim().split("\\s+");
    StringBuilder out = new StringBuilder(leading);
    for (int i = 0; i < words.length; i++) {
      if (i > 0) {
        out.append(' ');
      }
      out.append(randomLetters(LETTERS_PER_SIDE))
          .append(words[i])
          .append(randomLetters(LETTERS_PER_SIDE));
    }
    return out.toString();
  }

  private static String randomLetters(int count) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    StringBuilder sb = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
