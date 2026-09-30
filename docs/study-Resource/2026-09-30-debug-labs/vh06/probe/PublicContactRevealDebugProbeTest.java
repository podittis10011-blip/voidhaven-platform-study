package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicContactRevealDebugProbeTest {
    static final String ID = "01JCASE0000000000000000001";
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AuditService audit = mock(AuditService.class);
    // 全零固定密钥仅限内存教学夹具，绝不能用于真实服务。
    final PrivateValueCipher cipher = new PrivateValueCipher(true, "probe",
            "probe:" + Base64.getEncoder().encodeToString(new byte[32]), new MockEnvironment());
    final PublicCaseAdministrationService service = new PublicCaseAdministrationService(jdbc, cipher, audit);

    private void row(String aad) {
        var encrypted = cipher.encrypt("wechat:synthetic-only", aad);
        when(jdbc.queryForList(startsWith("select ciphertext"), eq("SUBMISSION"), eq(ID)))
                .thenReturn(List.of(Map.of("ciphertext", encrypted.ciphertext(), "key_id", encrypted.keyId())));
    }
    @Test void reveals_with_matching_aad_and_records_minimal_audit() {
        row("SUBMISSION:" + ID);
        var result = service.revealSubmissionContact(ID, "合成核验", "synthetic-admin", "ADMIN", "synthetic-request");
        assertThat(result.contact()).isEqualTo("wechat:synthetic-only");
        verify(audit).record(eq("synthetic-admin"), eq("ADMIN"), eq("PRIVATE_CONTACT_REVEALED"),
                eq("CONTENT_SUBMISSION"), eq(ID), eq("{}"), eq("{\"revealed\":true}"), eq("合成核验"), eq("synthetic-request"));
    }
    @Test void wrong_aad_prevents_reveal_and_audit() {
        row("SUBMISSION:another-synthetic-id");
        assertThatThrownBy(() -> service.revealSubmissionContact(ID, "合成核验", "synthetic-admin", "ADMIN", "r"))
                .hasFieldOrPropertyWithValue("code", "PRIVATE_VALUE_DECRYPTION_FAILED");
        verifyNoInteractions(audit);
    }
    @Test void missing_contact_prevents_reveal_and_audit() {
        assertThatThrownBy(() -> service.revealSubmissionContact(ID, "合成核验", "synthetic-admin", "ADMIN", "r"))
                .hasFieldOrPropertyWithValue("code", "PRIVATE_CONTACT_NOT_FOUND");
        verifyNoInteractions(audit);
    }
}
