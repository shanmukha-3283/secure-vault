package com.shanmukha.vault.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrengthCheckerTest {

    private static StrengthChecker.Assessment assess(String password) {
        return StrengthChecker.assess(password.toCharArray());
    }

    @Test
    void nullOrEmptyIsWeak() {
        assertThat(StrengthChecker.assess(null).strength())
                .isEqualTo(StrengthChecker.Strength.WEAK);
        assertThat(assess("").strength()).isEqualTo(StrengthChecker.Strength.WEAK);
    }

    @Test
    void shortPasswordIsWeak() {
        assertThat(assess("abc123").strength()).isEqualTo(StrengthChecker.Strength.WEAK);
    }

    @Test
    void longLowercaseOnlyIsWeak() {
        assertThat(assess("aaaaaaaaaaaa").strength()).isEqualTo(StrengthChecker.Strength.WEAK);
    }

    @Test
    void twelveCharactersWithThreeClassesIsMedium() {
        assertThat(assess("Abcdefgh1234").strength()).isEqualTo(StrengthChecker.Strength.MEDIUM);
    }

    @Test
    void sixteenCharactersWithAllClassesIsStrong() {
        assertThat(assess("Abcdefgh1234!xyz").strength()).isEqualTo(StrengthChecker.Strength.STRONG);
    }

    @Test
    void longPassphraseWithTwoClassesIsStrong() {
        assertThat(assess("correct horse battery staple").strength())
                .isEqualTo(StrengthChecker.Strength.STRONG);
    }

    @Test
    void everyAssessmentHasAUsefulReason() {
        assertThat(assess("").reason()).isNotBlank();
        assertThat(assess("abc").reason()).isNotBlank();
        assertThat(assess("aaaaaaaaaaaa").reason()).isNotBlank();
        assertThat(assess("Abcdefgh1234").reason()).isNotBlank();
        assertThat(assess("Abcdefgh1234!xyz").reason()).isNotBlank();
    }
}
