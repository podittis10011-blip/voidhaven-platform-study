package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** Commits the short-lived ownership marker independently from the protected business transaction. */
@Service
public class IdempotencyReservationService {
    public record Existing(String requestHash, int responseStatus, String responseBody, boolean responseReplayable) { }

    private final JdbcTemplate jdbc;
    private final long inProgressSeconds;

    public IdempotencyReservationService(JdbcTemplate jdbc,
            @Value("${voidhaven.idempotency-in-progress-seconds:86400}") long inProgressSeconds) {
        if (inProgressSeconds < 1) throw new IllegalStateException("Idempotency in-progress timeout must be positive");
        this.jdbc = jdbc;
        this.inProgressSeconds = inProgressSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    //reserve的实现：
    //1. 删除幂等性记录
    //2. 插入幂等性记录
    //3. 返回幂等性记录
        //4. 如果幂等性记录已存在，则查询幂等性记录
        //5. 如果幂等性记录不存在，则返回null
    public Existing reserve(String key, String requestHash) {
        //删除幂等性记录
        jdbc.update("delete from idempotency_records where idempotency_key = ? and response_status = 102 and expires_at < current_timestamp(6)", key);
        try {
            //插入幂等性记录
            jdbc.update("insert into idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at) values (?, ?, ?, 102, cast(? as json), ?)",
                    Ids.ulid(), key, requestHash, "{\"state\":\"IN_PROGRESS\"}",
                    Timestamp.from(Instant.now().plusSeconds(inProgressSeconds)));
            return null;
        } catch (DuplicateKeyException ignored) {
            //查询幂等性记录
            Map<String, Object> row = jdbc.queryForMap(
                    "select request_hash, response_status, response_body, response_replayable from idempotency_records where idempotency_key = ?", key);
            //获取响应可重放性
            Object replayable = row.get("response_replayable");
            //获取响应可重放性
            boolean replay = replayable instanceof Boolean value ? value : !"0".equals(String.valueOf(replayable));
            //返回幂等性记录
            return new Existing(row.get("request_hash").toString(), Integer.parseInt(row.get("response_status").toString()),
                    row.get("response_body").toString(), replay);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    //release的实现：
    //1. 删除幂等性记录
    //2. 返回幂等性记录
    //3. 如果幂等性记录已存在，则查询幂等性记录
    //4. 如果幂等性记录不存在，则返回null
    public void release(String key, String requestHash) {
        jdbc.update("delete from idempotency_records where idempotency_key = ? and request_hash = ? and response_status = 102", key, requestHash);
    }
}
