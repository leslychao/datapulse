package io.datapulse.tenancy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

  @Nested
  @DisplayName("generateSlug")
  class GenerateSlug {

    @Test
    @DisplayName("should_transliterate_cyrillic_when_given_russian_name")
    void should_transliterate_cyrillic_when_given_russian_name() {
      assertThat(SlugUtils.generateSlug("Мой Магазин")).isEqualTo("moy-magazin");
    }

    @Test
    @DisplayName("should_lowercase_and_replace_spaces_when_given_english_name")
    void should_lowercase_and_replace_spaces_when_given_english_name() {
      assertThat(SlugUtils.generateSlug("My Workspace")).isEqualTo("my-workspace");
    }

    @Test
    @DisplayName("should_remove_special_characters_when_given_mixed_input")
    void should_remove_special_characters_when_given_mixed_input() {
      assertThat(SlugUtils.generateSlug("Test @#$ Slug!")).isEqualTo("test-slug");
    }

    @Test
    @DisplayName("should_collapse_multiple_dashes_when_given_consecutive_special_chars")
    void should_collapse_multiple_dashes_when_given_consecutive_special_chars() {
      assertThat(SlugUtils.generateSlug("a---b___c")).isEqualTo("a-b-c");
    }

    @Test
    @DisplayName("should_trim_leading_and_trailing_dashes_when_present")
    void should_trim_leading_and_trailing_dashes_when_present() {
      assertThat(SlugUtils.generateSlug("-hello world-")).isEqualTo("hello-world");
    }

    @Test
    @DisplayName("should_return_workspace_when_name_is_empty_after_normalization")
    void should_return_workspace_when_name_is_empty_after_normalization() {
      assertThat(SlugUtils.generateSlug("@#$%^&")).isEqualTo("workspace");
    }

    @Test
    @DisplayName("should_return_workspace_when_name_has_only_spaces")
    void should_return_workspace_when_name_has_only_spaces() {
      assertThat(SlugUtils.generateSlug("   ")).isEqualTo("workspace");
    }

    @Test
    @DisplayName("should_truncate_to_70_chars_when_name_is_very_long")
    void should_truncate_to_70_chars_when_name_is_very_long() {
      String longName = "a".repeat(100);
      String slug = SlugUtils.generateSlug(longName);
      assertThat(slug.length()).isLessThanOrEqualTo(70);
    }

    @Test
    @DisplayName("should_strip_diacritics_when_given_accented_latin_characters")
    void should_strip_diacritics_when_given_accented_latin_characters() {
      assertThat(SlugUtils.generateSlug("café résumé")).isEqualTo("cafe-resume");
    }

    @Test
    @DisplayName("should_handle_mixed_cyrillic_and_latin_when_given_both")
    void should_handle_mixed_cyrillic_and_latin_when_given_both() {
      String slug = SlugUtils.generateSlug("My Магазин 2024");
      assertThat(slug).isEqualTo("my-magazin-2024");
    }

    @Test
    @DisplayName("should_handle_ъ_and_ь_when_present_in_russian_text")
    void should_handle_hard_and_soft_signs_when_present() {
      String slug = SlugUtils.generateSlug("Объём");
      assertThat(slug).isEqualTo("obyom");
    }
  }

  @Nested
  @DisplayName("appendSuffix")
  class AppendSuffix {

    @Test
    @DisplayName("should_append_4_char_suffix_when_called")
    void should_append_4_char_suffix_when_called() {
      String result = SlugUtils.appendSuffix("test-slug");
      assertThat(result).startsWith("test-slug-");
      assertThat(result).hasSize("test-slug-".length() + 4);
    }

    @Test
    @DisplayName("should_produce_alphanumeric_suffix_when_called")
    void should_produce_alphanumeric_suffix_when_called() {
      String result = SlugUtils.appendSuffix("base");
      String suffix = result.substring("base-".length());
      assertThat(suffix).matches("[a-z0-9]{4}");
    }

    @Test
    @DisplayName("should_not_exceed_80_chars_when_base_is_long")
    void should_not_exceed_80_chars_when_base_is_long() {
      String longBase = "a".repeat(78);
      String result = SlugUtils.appendSuffix(longBase);
      assertThat(result.length()).isLessThanOrEqualTo(80);
    }

    @Test
    @DisplayName("should_produce_different_suffixes_on_consecutive_calls")
    void should_produce_different_suffixes_on_consecutive_calls() {
      String first = SlugUtils.appendSuffix("base");
      String second = SlugUtils.appendSuffix("base");
      // probabilistic — 36^4 = ~1.7M, collision probability negligible
      assertThat(first).isNotEqualTo(second);
    }
  }
}
