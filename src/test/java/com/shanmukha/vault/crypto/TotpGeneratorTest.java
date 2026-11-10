package com.shanmukha.vault.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the TOTP implementation against the RFC 6238 appendix-B test
 * vector (SHA-1, seed "12345678901234567890", T = 59 seconds — 8-digit code
 * 94287082). The 6-digit display code is the last six digits of the same
 * dynamic truncation, so the vector also pins it.
 */
class TotpGeneratorTest {

    /** RFC 6238 B.2 vector: SHA-1 seed, unix time 59 s. */
    private static final byte[] RFC_SEED =
            "12345678901234567890".getBytes(StandardCharsets.UTF_8);
    private static final long T59_MS = 59_000L;

    @Test
    void eightDigitCodeMatchesRfcVector() {
        assertThat(TotpGenerator.generate8(RFC_SEED, T59_MS)).isEqualTo("94287082");
    }

    @Test
    void sixDigitCodeIsLastSixOfVector() {
        assertThat(TotpGenerator.generate(RFC_SEED, T59_MS)).isEqualTo("287082");
    }

    @Test
    void base32DecodingMatchesAsciiSeed() {
        byte[] decoded = TotpGenerator.decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(decoded).isEqualTo(RFC_SEED);
        assertThat(TotpGenerator.generate(decoded, T59_MS)).isEqualTo("287082");
    }

    @Test
    void remainingSecondsCountsDownWithinWindow() {
        assertThat(TotpGenerator.remainingSeconds(60_000L)).isEqualTo(30);
        assertThat(TotpGenerator.remainingSeconds(75_001L)).isEqualTo(15);
        assertThat(TotpGenerator.remainingSeconds(89_999L)).isEqualTo(1);
    }

    @Test
    void invalidBase32IsRejected() {
        assertThatThrownBy(() -> TotpGenerator.decodeBase32("abc1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
