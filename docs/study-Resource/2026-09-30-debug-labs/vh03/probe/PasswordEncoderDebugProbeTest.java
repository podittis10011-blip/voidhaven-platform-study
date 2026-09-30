package cn.voidhaven.api.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 纯内存真实编码器；直接调用配置工厂，不代表 Spring 装配已验证。 */
class PasswordEncoderDebugProbeTest {
    @Test void real_encoder_uses_random_salt_and_checks_passwords() {
        var encoder = new SecurityConfig().passwordEncoder();
        String raw = "SyntheticPassword123!";
        String first = encoder.encode(raw); // BP: 第二次 encode 前观察 first
        String second = encoder.encode(raw);
        assertThat(first).startsWith("$argon2id$").isNotEqualTo(second);
        assertThat(encoder.matches(raw, first)).isTrue();
        assertThat(encoder.matches(raw, second)).isTrue();
        assertThat(encoder.matches("WrongPassword123!", first)).isFalse();
    }
}
