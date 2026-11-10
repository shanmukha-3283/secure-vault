package com.shanmukha.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test proving the build toolchain and the runtime dependencies are
 * wired correctly. No display or vault data is needed here.
 */
class AppSmokeTest {

    @Test
    void flatLafIsOnTheClasspath() {
        assertThat(FlatLightLaf.class.getName())
                .isEqualTo("com.formdev.flatlaf.FlatLightLaf");
    }

    @Test
    void jacksonIsOnTheClasspath() {
        assertThat(new ObjectMapper()).isNotNull();
    }
}
