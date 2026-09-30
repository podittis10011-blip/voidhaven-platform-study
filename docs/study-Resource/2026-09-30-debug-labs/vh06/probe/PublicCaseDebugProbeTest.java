package cn.voidhaven.api.service;

import cn.voidhaven.api.support.SecureTokens;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 执行真实业务回调；RecordingJdbc 只记录 SQL/返回合成行，不执行 SQL。 */
class PublicCaseDebugProbeTest {
    static class RecordingJdbc extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> writes = new ArrayList<>();
        List<Map<String,Object>> rows = List.of();
        @Override public int update(String sql, Object... args) {
            sqls.add(sql); writes.add(args); return 1;
        }
        @Override public List<Map<String,Object>> queryForList(String sql, Object... args) {
            return rows;
        }
    }
    final RecordingJdbc jdbc = new RecordingJdbc();
    final IdempotencyService idempotency = mock(IdempotencyService.class);
    final PrivateValueCipher cipher = mock(PrivateValueCipher.class);
    final PublicCaseService service = new PublicCaseService(jdbc, idempotency, cipher);

    private PublicCaseService.CreatedCase create() {
        when(idempotency.executeSingleDelivery(anyString(), any(), any())).thenAnswer(call -> {
            Supplier<?> action = call.getArgument(2);
            return action.get(); // BP: F7 进入真实创建回调
        });
        return service.createSubmission("synthetic-key", new PublicCaseService.SubmissionCommand(
                PublicCaseService.SubmissionType.FEEDBACK, " 合成标题 ", " 合成反馈 ", null, null, null));
    }
    private void storedRow(PublicCaseService.CreatedCase created) {
        jdbc.rows = List.of(Map.of("id", created.id(), "access_code_hash", jdbc.writes.get(0)[6], "state", "RECEIVED"));
    }
    @Test void creates_receipt_stores_only_hash_and_queries_it() {
        var created = create();
        assertThat(created.id()).hasSize(26);
        assertThat(created.queryCode()).hasSize(43);
        Object storedHash = jdbc.writes.get(0)[6];
        assertThat(storedHash).isEqualTo(SecureTokens.sha256(created.queryCode())).isNotEqualTo(created.queryCode());
        assertThat(jdbc.sqls).hasSize(2);
        assertThat(jdbc.sqls.get(1)).contains("public_case_status_history");
        storedRow(created);
        var status = service.submissionStatus(created.id(), created.queryCode());
        assertThat(status.status()).isEqualTo("RECEIVED");
        // 查询码不是消费即失效：重复查询仍成立。
        assertThat(service.submissionStatus(created.id(), created.queryCode()).id()).isEqualTo(created.id());
    }
    @Test void wrong_blank_and_missing_receipts_share_one_error_code() {
        var created = create();
        storedRow(created);
        assertThatThrownBy(() -> service.submissionStatus(created.id(), "wrong-code"))
                .hasFieldOrPropertyWithValue("code", "CASE_QUERY_INVALID");
        assertThatThrownBy(() -> service.submissionStatus(created.id(), ""))
                .hasFieldOrPropertyWithValue("code", "CASE_QUERY_INVALID");
        jdbc.rows = List.of();
        assertThatThrownBy(() -> service.submissionStatus(created.id(), created.queryCode()))
                .hasFieldOrPropertyWithValue("code", "CASE_QUERY_INVALID");
    }
}
