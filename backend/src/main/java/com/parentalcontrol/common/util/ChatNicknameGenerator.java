package com.parentalcontrol.common.util;

import java.util.Locale;

/** Deterministic, email-based chat nicknames (e.g. Golden Aurora). */
public final class ChatNicknameGenerator {

  private static final String[] ADJECTIVES = {
    "golden", "silver", "gentle", "radiant", "serene", "mystic", "crystal", "lunar",
    "solar", "emerald", "sapphire", "amber", "coral", "ivory", "pearl", "azure",
    "violet", "opal", "starlit", "moonlit", "luminous", "tranquil", "ethereal", "velvet",
    "graceful", "blossoming", "shimmering", "harmonious", "enchanted", "dawn", "dusk", "wild"
  };

  private static final String[] NOUNS = {
    "aurora", "willow", "cedar", "sage", "river", "meadow", "falcon", "lark",
    "swan", "dove", "orchid", "lily", "jasmine", "violet", "rose", "lotus",
    "blossom", "breeze", "horizon", "comet", "nova", "star", "moon", "cloud",
    "tide", "brook", "glen", "vale", "peak", "grove", "harbor", "lantern"
  };

  private ChatNicknameGenerator() {}

  public static String fromEmail(String email) {
    if (email == null || email.isBlank()) {
      return "Unknown Parent";
    }
    SeededRng rng = new SeededRng(normalizeEmail(email));
    String adjective = ADJECTIVES[rng.nextIndex(ADJECTIVES.length)];
    String noun = NOUNS[rng.nextIndex(NOUNS.length)];
    return capitalize(adjective) + " " + capitalize(noun);
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String capitalize(String word) {
    if (word.isEmpty()) {
      return word;
    }
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  private static final class SeededRng {
    private long state;

    SeededRng(String seed) {
      long computed = 0;
      for (int index = 0; index < seed.length(); index++) {
        computed = (computed * 31 + seed.charAt(index)) & 0xFFFFFFFFL;
      }
      this.state = computed == 0 ? 1 : computed;
    }

    int nextIndex(int bound) {
      state = (state * 1664525 + 1013904223) & 0xFFFFFFFFL;
      return (int) ((state * bound) >>> 32);
    }
  }
}
