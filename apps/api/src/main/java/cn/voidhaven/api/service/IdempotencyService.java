package cn.voidhaven.api.service;
import cn.voidhaven.api.web.ApiException;
import tools.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private static final String SENSITIVE_BODY = "{\"state\":\"DELIVERED_NO_REPLAY\"}";
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final IdempotencyReservationService reservations;
    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json, IdempotencyReservationService reservations) { this.jdbc = jdbc; this.json = json; this.reservations = reservations; }
    
    
    @Transactional
    //execute的实现：
    //1. 检查幂等性请求标识是否有效
    //2. 计算请求哈希
    //3. 检查幂等性记录是否已存在
    //4. 如果幂等性记录已存在，则恢复幂等性记录
    //5. 如果幂等性记录不存在，则执行操作
    //6. 如果操作执行成功，则更新幂等性记录
    //7. 如果操作执行失败，则释放幂等性记录
    //8. 返回操作结果
    public <T> T execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        //检查幂等性请求标识是否有效
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        //计算请求哈希
        String requestHash = hash(write(request));
        //检查幂等性记录是否已存在
        IdempotencyReservationService.Existing existing = reservations.reserve(key, requestHash);
        //如果幂等性记录已存在，则恢复幂等性记录
        if (existing != null) return restore(existing, requestHash, responseType);
        //如果幂等性记录不存在，则执行操作
        try {
            //执行操作
            T result = action.get();
            //写入响应
            String response = write(result);
            //更新幂等性记录
            int completed = jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json), response_replayable = true, expires_at = ? where idempotency_key = ? and request_hash = ? and response_status = 102",
                    response, Timestamp.from(Instant.now().plusSeconds(86400)), key, requestHash);
            //如果幂等性记录更新失败，则抛出异常
            if (completed != 1) throw new IllegalStateException("Idempotency reservation was lost before completion");
            //返回操作结果
            return result;
        } catch (RuntimeException | Error error) {
            //释放幂等性记录
            reservations.release(key, requestHash);
            //抛出异常
            throw error;
        }
    }
    /**
     * Applies the same duplicate-write protection without ever persisting a one-time secret.
     * A duplicate request deliberately requires an administrator to issue a new credential.
     */
    @Transactional
    //executeSingleDelivery的实现：
    //1. 检查幂等性请求标识是否有效
    //2. 计算请求哈希
    //3. 检查幂等性记录是否已存在
    //4. 如果幂等性记录已存在，则恢复幂等性记录
    //5. 如果幂等性记录不存在，则执行操作
    //6. 如果操作执行成功，则更新幂等性记录
    //7. 如果操作执行失败，则释放幂等性记录
    //8. 返回操作结果
    public <T> T executeSingleDelivery(String key, Object request, Supplier<T> action) {
        //检查幂等性请求标识是否有效
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        //计算请求哈希
        String requestHash = hash(write(request));
        //检查幂等性记录是否已存在
        IdempotencyReservationService.Existing existing = reservations.reserve(key, requestHash);
        //如果幂等性记录已存在，则恢复幂等性记录
        if (existing != null) {
            //如果请求哈希不匹配，则抛出异常
            if (!Objects.equals(existing.requestHash(), requestHash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
            //如果响应状态为102，则抛出异常
            if (existing.responseStatus() == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
            //如果响应可重放性为false，则抛出异常
            throw new ApiException(HttpStatus.CONFLICT, "SENSITIVE_RESULT_NOT_REPLAYABLE", "一次性敏感结果不会被重复返回，请核对当前状态后使用新的请求标识重新签发或提交。");
        }
        try {
            //执行操作
            T result = action.get();
            //更新幂等性记录
            int completed = jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json), response_replayable = false, expires_at = ? where idempotency_key = ? and request_hash = ? and response_status = 102",
                    SENSITIVE_BODY, Timestamp.from(Instant.now().plusSeconds(86400)), key, requestHash);
            //如果幂等性记录更新失败，则抛出异常
            if (completed != 1) throw new IllegalStateException("Idempotency reservation was lost before completion");
            //返回操作结果
            return result;
        } catch (RuntimeException | Error error) {
            //释放幂等性记录
            reservations.release(key, requestHash);
            //抛出异常
            throw error;
        }
    }
    private <T> T restore(IdempotencyReservationService.Existing existing, String hash, Class<T> type) {
        if (!Objects.equals(existing.requestHash(), hash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
        if (existing.responseStatus() == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
        if (!existing.responseReplayable()) throw new ApiException(HttpStatus.CONFLICT, "SENSITIVE_RESULT_NOT_REPLAYABLE", "一次性敏感结果不会被重复返回，请核对当前状态后使用新的请求标识重新签发或提交。");
        try { return json.readValue(existing.responseBody(), type); } catch (Exception error) { throw new IllegalStateException("Stored idempotency response cannot be restored", error); }
    }
    private String write(Object object) { try { return json.writeValueAsString(object); } catch (Exception error) { throw new IllegalStateException("Response cannot be serialized", error); } }
    private String hash(String input) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
}
