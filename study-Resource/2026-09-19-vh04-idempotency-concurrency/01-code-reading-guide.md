# VH-04 代码阅读路线（七站）

阅读版本：`D:\Projects\voidhaven\voidhaven-platform`，HEAD `2295a41`（2026-09-19），工作树干净。
行号以该版本为准；改动后会漂移，用方法名兜底。

## 0. 一句话链路

```text
POST /api/v1/public/submissions        PATCH /api/v1/admin/content-items/{id}
   │                                      │
   ├─ PublicWriteRateLimitInterceptor     ├─ CSRF / 角色 / 资格（VH-03）
   │    └─ RateLimitLockService           │
   │         （独立事务：占额度）          ├─ IdempotencyService.execute
   │                                      │    └─ IdempotencyReservationService
   ├─ Controller 参数解析与 Bean Validation    （独立事务：占幂等键）
   │                                      │
   ├─ PublicCaseService.createSubmission  ├─ ContentManagementService.revise
   │    └─ IdempotencyService             │    ├─ findByIdForUpdate  ← 悲观锁
   │         .executeSingleDelivery       │    ├─ 比较 If-Match      ← 乐观并发
   │                                      │    └─ saveAndFlush       ← @Version 递增
   └─ 业务事务 / 审计                       └─ 业务事务 / 审计
```

四个判断依次发生，且彼此独立：**能不能发（限流） → 是不是同一次（幂等） → 是不是基于最新状态（版本） → 业务规则**。

前两层用 `REQUIRES_NEW` 独立事务提交，所以「业务回滚但额度已扣」「业务回滚但幂等键仍被占」都是设计内行为，不是 bug。

---

## 第 1 站：最外层是限流，不是幂等

文件：`apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java`（35 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 15～18 | `WRITE_PATHS` / `STATUS_QUERY_PATHS` 两个 `Set` | 按**精确路径**匹配，不是前缀 |
| 33～37 | `preHandle`：只处理 `POST`，命中集合才限流 | 查询与写入用两个不同的桶 |
| 22～26 | 注入 `ClientNetworkSubject` | 取客户端网络标识；本地 profile 信任代理头 |

注册处在 `apps/api/src/main/java/cn/voidhaven/api/config/WebConfiguration.java` 第 15～17 行：`registry.addInterceptor(publicWriteRateLimit)` 没有加路径参数，等于对所有请求生效，实际过滤靠类内的两个 `Set`。

桶的口径（`service/PublicWriteRateLimiter.java`、`service/PublicStatusQueryRateLimiter.java`）：

| 项 | 公开写入 | 状态查询 |
|---|---|---|
| 配置项 | `voidhaven.public-write-rate-limit-max-requests`（默认 8） | `voidhaven.public-status-query-rate-limit-max-requests`（默认 60） |
| 窗口 | `...-window-seconds`（默认 3600） | 同左 |
| 入口 | `PublicWriteRateLimiter.requireAvailable`（第 36 行） | `PublicStatusQueryRateLimiter.requireAvailable`（第 38 行） |
| 主体 | HMAC-SHA256 后的网络标识 | 同左 |

**本轮的实测关键点：**拦截器跑在参数解析与幂等判断之前，所以「缺 `Idempotency-Key` 的 400」和「幂等键重复的 409」也会各消耗一个额度。这不是缺陷，但必须知道——见 `03-manual-repro-runbook.md` 第 3.2 节的 B 组，8 个额度里只有 5 个真正创建了业务记录。

读完自答：

1. 为什么把限流放在拦截器而不是 Service？如果放到 Service，哪个路径能绕过？
2. 限流主体为什么用 HMAC 而不是明文 IP 或裸 SHA-256？
3. `preHandle` 返回 `true` 之后才轮到参数解析。这解释了哪一条实测现象？
4. 状态查询为什么要和公开写入分开计桶？

---

## 第 2 站：限流计数怎么在并发下不超发

文件：`apps/api/src/main/java/cn/voidhaven/api/service/RateLimitLockService.java`（95 行）

| 行 | 内容 | 要点 |
|---:|---|---|
| 19～25 | 7 个 scope 常量 | `PUBLIC_WRITE_NETWORK`、`PUBLIC_STATUS_QUERY`、`LOGIN_ID`、`LOGIN_NETWORK`、`ACTIVATION_NETWORK`、`PASSWORD_RESET_*` |
| 27～34 | 构造器里建 `TransactionTemplate`，显式设 `PROPAGATION_REQUIRES_NEW` | 计数的提交与业务事务解耦 |
| 36～48 | `reservePublicWrite` | 先 `lockSubject` → `count(*)` → 超限抛 429，否则插一条事件 |
| 50～62 | `reservePublicStatusQuery` | 同构，另一张表 |
| 64～78 | `reserveAuth` | 统计口径是 `outcome in ('ATTEMPT','FAILURE')`，`SUCCESS` 不占额度 |
| 80～98 | `completeLoginAttempt` | 把这次尝试改成 `SUCCESS`/`FAILURE`；成功时删掉该登录名的 `FAILURE` 行 |
| 100～104 | `lockSubject` | **这是整个并发正确性的核心两行** |

```sql
insert into rate_limit_locks (scope, subject_hash) values (?, ?)
  on duplicate key update subject_hash = subject_hash;      -- 保证锁行存在
select scope from rate_limit_locks where scope = ? and subject_hash = ?
  for update;                                               -- 串行化同一个主体
```

为什么需要这两行：`count(*)` 再 `insert` 是典型的「检查后行动」竞态。两个请求可能同时数到 7（上限 8）然后各插一条，最终变成 9 条。`select ... for update` 把同一 `(scope, subject_hash)` 的请求排成一队，才让「数—插」变成原子操作。

读完自答：

1. 只用「计数表 + 唯一索引」能不能替代锁行？会给业务带来什么代价？
2. `lockSubject` 的第一条 `insert ... on duplicate key update` 为什么不能省？
3. `reserveAuth` 只用 `count(*)`，如果并发很高会怎样？为什么登录场景可以接受？
4. 计数记录谁负责删？（提示：第 7 站）

---

## 第 3 站：幂等的两种语义

文件：`apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java`（69 行）

### 3.1 通用幂等：`execute`

| 行 | 内容 | 要点 |
|---:|---|---|
| 17 | `SENSITIVE_BODY = {"state":"DELIVERED_NO_REPLAY"}` | 敏感结果的占位体 |
| 21～23 | 入参校验 | key 为空或超过 128 字符 → 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 22 | `requestHash = sha256(json(request))` | 哈希的是**业务对象序列化结果**，不是原始 HTTP 字节 |
| 24～25 | `reservations.reserve(key, requestHash)` | 返回非空 = 这个键已经存在，走重放分支 |
| 26～31 | 执行业务动作，成功后把记录改成 `200` + 序列化响应 + `expires_at = now + 24h` | 完成条件里带 `response_status = 102`，即「只能由占位者完成」 |
| 32～35 | 捕获 `RuntimeException | Error` → `release` | 失败释放，允许用同一个键重试 |
| 62～67 | `restore` | 三重判断：哈希不一致 → `IDEMPOTENCY_KEY_REUSED`；状态 102 → `REQUEST_IN_PROGRESS`；不可回放 → `SENSITIVE_RESULT_NOT_REPLAYABLE` |

### 3.2 一次性交付：`executeSingleDelivery`

第 42～60 行。与 `execute` 的差别只有一处：完成时写 `response_replayable = false`，且响应体固定为占位符。

它用在「响应里含一次性秘密」的接口上：账号激活凭据、管理员签发的密码重置凭据、公开投稿/合作申请返回的 `queryCode`。重复提交时不会把秘密再吐一次，而是 409 `SENSITIVE_RESULT_NOT_REPLAYABLE`，要求人工重新签发。

调用方对照：

| 接口 | 方法（行号） | 语义 |
|---|---|---|
| `POST /api/v1/public/submissions` | `executeSingleDelivery` | 返回 `queryCode`，不可回放 |
| `POST /api/v1/public/partner-applications` | `executeSingleDelivery` | 同上 |
| `POST /api/v1/admin/content-items` | `execute`（`AdminController` 146） | 可回放，重放返回同一份内容 DTO |
| `PATCH /api/v1/admin/content-items/{id}` | `execute`（153） | 可回放 |
| `POST /api/v1/admin/reviews/{id}/decision` | `execute`（169） | 可回放 |
| `PUT /api/v1/admin/content-items/{id}/actions` | `executeSingleDelivery`（221） | 行动入口替换 |
| `POST /api/v1/admin/accounts`（建号） | `executeSingleDelivery`（139） | 返回一次性激活凭据 |
| `POST /api/v1/admin/accounts/{id}/activation-links` | `executeSingleDelivery`（328） | 重新签发一次性凭据 |
| `POST /api/v1/admin/accounts/{id}/password-reset-links` | `executeSingleDelivery`（`PasswordAdminController` 25） | 管理员签发重置凭据 |
| `POST /api/v1/admin/community/invite-batches` | `executeSingleDelivery`（`CommunityAdminController` 93） | 返回邀请码 |

规律：**响应里含一次性秘密 → `executeSingleDelivery`；其余写操作 → `execute`。**

### 3.3 占位与释放：`IdempotencyReservationService`

文件：`apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyReservationService.java`（45 行）

| 行 | 内容 | 要点 |
|---:|---|---|
| 18 | `record Existing(requestHash, responseStatus, responseBody, responseReplayable)` | 重放分支需要的最小信息 |
| 30 | `@Transactional(propagation = REQUIRES_NEW)` | **独立事务**：预占必须对其它请求立即可见，且不被业务回滚带走 |
| 31～38 | `reserve`：先删过期的 102 行，再 `insert` 状态 102 | 过期即视为无人持有 |
| 39～45 | 捕获 `DuplicateKeyException` → 查出已存在的行返回 | 唯一约束 `UNIQUE (idempotency_key)` 是并发仲裁者 |
| 48～49 | `release`：删掉自己的 102 行 | 只删自己那一行 |

表结构：`db/migration/V1__initial_schema.sql` 第 224～234 行建 `idempotency_records`，关键字段 `idempotency_key VARCHAR(128) UNIQUE`、`request_hash CHAR(64)`、`response_status SMALLINT`、`response_body JSON`、`expires_at`。

后续迁移的两处补丁：

- `V11__account_concurrency_and_distributed_limits.sql` 第 4～5 行加 `response_replayable BOOLEAN NOT NULL DEFAULT TRUE`。
- 同文件第 7～8 行 `DELETE FROM idempotency_records WHERE CAST(response_body AS CHAR) LIKE '%activationToken%'`——把历史版本误存的凭据从缓存里清掉（见 `docs/runbooks/data-preserving-cutover.md`）。

读完自答：

1. `reserve` 为什么必须是 `REQUIRES_NEW`？如果改成默认的 `REQUIRED`，会出什么问题？
2. 业务失败后 `release` 被调用，但如果进程在 `reserve` 之后、`release` 之前直接崩了，这个键会怎样？谁来救它？
3. 请求哈希算的是「业务对象」而不是「HTTP 原始字节」。同一个语义但字段顺序不同的 JSON，会得到什么结果？
4. 为什么重放要返回「存下来的响应」，而不是「重新执行业务再返回」？

---

## 第 4 站：乐观并发——三道防线各挡什么

### 4.1 客户端契约：`If-Match`

`contracts/openapi/voidhaven-v0.yaml` 第 960 行把 `If-Match` 定义成必填头，描述为「JPA 内容版本号」。它不是 HTTP 标准的 ETag 语义，而是直接把实体 `version` 当版本号用。

Controller 侧的解析（`web/AdminController.java` 第 369 行）：

```java
private long version(String value) { try { return Long.parseLong(value.replace("\"", "")); } ... }
```

带不带引号都能过——这是刻意的宽松解析，方便前端与 `curl` 两种调用方。

### 4.2 第一道：显式版本比对

文件：`apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java`

| 行 | 方法 | 比对点 |
|---:|---|---|
| 132 | `revise` | 第 136～139 行先 `lockedOwned`，第 140 行比较 `expectedVersion` |
| 175 | `decide` | 第 178 行比较（**不加锁**） |
| 206 | `publish` | 第 208 行 `findByIdForUpdate`，但**不比对** `If-Match` |
| 227 | `changeOperationalStatus` | 第 229 行加锁 + 第 230 行比对 |

不匹配就抛 `ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", ...)`。

### 4.3 第二道：JPA `@Version`

| 位置 | 内容 |
|---|---|
| `domain/ContentItem.java` 第 24 行 | `@Version private Long version;` |
| `domain/Account.java` 第 15 行 | `@Version private Long version;` |
| `V11` 第 1～2 行 | `ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0` |

实体被并发修改时，Hibernate 在提交阶段发现版本不符，抛 `ObjectOptimisticLockingFailureException`，由 `ApiExceptionHandler` 第 42～49 行转成 409。注意它按实体类型分流：`Account` → `ACCOUNT_VERSION_CONFLICT`，其余 → `CONTENT_VERSION_CONFLICT`。

### 4.4 第三道：悲观锁

| 位置 | 内容 |
|---|---|
| `persistence/ContentItemRepository.java` 第 42～44 行 | `@Lock(PESSIMISTIC_WRITE) findByIdForUpdate` |
| `persistence/AccountRepository.java` 第 15～17 行 | `@Lock(PESSIMISTIC_READ) findByIdForShare` |
| `persistence/AccountRepository.java` 第 19～21 行 | `@Lock(PESSIMISTIC_WRITE) findByIdForUpdate` |

`ContentManagementService` 第 134～139 行留了一段注释，说明为什么 `revise` 必须加锁：两个并发请求都会从同一个 `revision_number` 出发算出同一个新修订号，第二条 `saveAndFlush` 会去撞 `UNIQUE (content_item_id, revision_number)`，返回的将是数据库完整性错误，而不是可解释的业务冲突。

三道防线的分工：

| 防线 | 挡住的失败模式 | 用户看到什么 |
|---|---|---|
| 悲观锁 | 服务器内部两个请求同时改同一行 | 看不见；请求被排队 |
| `If-Match` 比对 | 用户手里是过期页面 | 409，可刷新后重试 |
| `@Version` | 代码路径漏比对、或跨事务的丢更新 | 409，同义兜底 |

读完自答：

1. 只留 `If-Match`、去掉悲观锁，会发生什么？（提示：`revision_number` 的唯一约束）
2. 只留悲观锁、去掉 `If-Match`，用户会遇到什么？
3. `decide` 为什么不加锁？它和 `revise` 的差异在哪？
4. `revision`（DTO 字段）和 `revision_number`（修订序号）是两个东西。它们各自在哪个表、各自谁在加一？

---

## 第 5 站：跨进程互斥的三种实现

### 5.1 行锁表：限流与计数

`rate_limit_locks (scope, subject_hash)` 主键 + `select ... for update`，见第 2 站。适用于「同一主体、短事务、要拿数再判断」的场景。

### 5.2 命名锁：定时任务只跑一个

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MysqlAdvisoryLock.java`（42 行）

| 行 | 内容 | 要点 |
|---:|---|---|
| 18～30 | `runExclusive(name, work)` | 拿不到锁直接返回 `false`，**不等待** |
| 32～38 | `acquire` → `select get_lock(?, 0)` | 第二个参数 `0` = 立即返回 |
| 41～46 | `release` → `select release_lock(?)`，失败静默 | 连接关闭会自动释放，`finally` 里释放是双保险 |

调用方：

| 位置 | 锁名 |
|---|---|
| `service/ContentLifecycleService.java` 第 25、53 行 | `content-lifecycle` |
| `service/RetentionService.java` 第 11、33 行 | `approved-retention-cleanup` |

要点：`get_lock` 是**连接级**的。所以它包在一次性 `dataSource.getConnection()` 里，而不是复用连接池里的长连接——否则锁会跟着池化连接漂移。

### 5.3 租约列：可恢复的后台任务

文件：`apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java`

```sql
update media_deletion_jobs
   set locked_by = ?, locked_until = date_add(current_timestamp(6), interval 10 minute)
 where id = ? and state in ('PENDING','FAILED')
   and (locked_until is null or locked_until < current_timestamp(6));
```

（第 44～53 行，`locked_by` 取值形如 `instance-a:01J...`。）

它和 `GET_LOCK` 的差别是**可恢复**：进程崩溃后锁不会永久残留，只是要等到 `locked_until` 过期；但代价是同一份工作可能被重复领取，所以任务本身必须幂等（第 63、67 行的完成条件里都带 `and locked_by = ?`，防止 A 的完成覆盖 B 的结果）。

表结构在 `V11` 第 27～30 行：`locked_by`、`locked_until`、索引 `idx_media_deletion_claim (state, next_attempt_at, locked_until)`。

### 5.4 唯一约束：最后一道

`accounts.login_id` 唯一。并发建同号账号时，只有一个 `insert` 成功，另一个拿到 `DataIntegrityViolationException` → 409 `DATA_INTEGRITY_CONFLICT`（`ApiExceptionHandler` 第 51～54 行）。这是「不用锁也不会脏」的兜底，但错误语义最粗，只适合做最后防线。

读完自答：

1. 为什么 `RetentionService` 用 `GET_LOCK`，而媒体清理用租约列？两种任务的可恢复性要求差在哪？
2. `release_lock` 有异常时为什么可以静默吞掉？
3. 如果 `media_deletion_jobs` 的完成条件不带 `and locked_by = ?`，会出什么事故？
4. 唯一约束能不能替代前面的所有机制？为什么项目仍然写了限流、幂等和版本校验？

---

## 第 6 站：冲突怎么变成可解释的响应

文件：`apps/api/src/main/java/cn/voidhaven/api/web/ApiExceptionHandler.java`

| 行 | 异常 | 响应 |
|---:|---|---|
| 42～49 | `ObjectOptimisticLockingFailureException` | 409 `ACCOUNT_VERSION_CONFLICT` / `CONTENT_VERSION_CONFLICT`，按实体名分流 |
| 51～54 | `DataIntegrityViolationException` | 409 `DATA_INTEGRITY_CONFLICT`，并 `log.warn` 带 `requestId` |
| 57～61 | `MissingRequestHeaderException` | 头名是 `Idempotency-Key` → 400 `IDEMPOTENCY_KEY_REQUIRED`；其它头 → `REQUEST_HEADER_REQUIRED` |
| 74～78 | `response(...)` | `retryable = 429 或 5xx`；429 固定加 `Retry-After: 900` |

业务码不在这里，而在各 Service 里用 `ApiException` 抛出。必须能背出这几组「同一个数据库冲突、不同业务含义」的码：

| 码 | 触发位置 |
|---|---|
| `CONTENT_VERSION_CONFLICT` | `ContentManagementService` 第 140、178、230 行 |
| `ACCOUNT_VERSION_CONFLICT` | `@Version` 兜底，实体名以 `.Account` 结尾 |
| `COMMUNITY_ENTRY_VERSION_CONFLICT` | `CommunityService` 第 150、164、175 行 |
| `HOMEPAGE_VERSION_CONFLICT` | `HomepageAdministrationService` 第 84 行 |
| `CATALOG_VERSION_CONFLICT` | `CatalogOrderService` 第 78 行 |
| `PROJECT_JOIN_ENTRY_VERSION_CONFLICT` | `ProjectJoinService` 第 102、121、137 行 |
| `CASE_VERSION_CONFLICT` | `PublicCaseAdministrationService` 第 71 行 |
| `CONTENT_SCHEMA_VERSION_CONFLICT` | 编辑器模块版本不一致（第 316 行） |

命名规律：`<聚合>_VERSION_CONFLICT`。面试里能按这条规律列举，比死记强。

读完自答：

1. 为什么 409 的 `retryable` 是 `false`，而 429 是 `true`？
2. 前端拿到 409 与拿到 429，正确的处理方式分别是什么？
3. `DATA_INTEGRITY_CONFLICT` 的文案为什么要引导用户提供 `requestId`？

---

## 第 7 站：这些记录谁来清理

文件：`apps/api/src/main/java/cn/voidhaven/api/service/RetentionService.java`

| 行 | 内容 |
|---:|---|
| 11 | `TASK_LOCK = "approved-retention-cleanup"` |
| 33 | `advisoryLocks.runExclusive(TASK_LOCK, this::run)` |
| 38 | `delete from idempotency_records where expires_at < current_timestamp(6)` |
| 43～45 | 分别清理 `auth_rate_limit_events`、`public_status_query_rate_limit_events`、`public_write_rate_limit_events` |
| 70 | 把本轮清理条数写进 `retention_runs.detail_summary` |

保留周期基线（`docs/privacy/data-handling.md`）：幂等记录 24 小时、社群验证事件 7 天、非敏感异常摘要 14 天。**幂等记录的 `expires_at` 有两个：** 在途占位用 `voidhaven.idempotency-in-progress-seconds`（默认 86400），完成后的可回放记录固定 `now + 86400`（`IdempotencyService` 第 28、53 行）。

读完自答：

1. 如果清理任务漏跑一天，幂等表会怎样？会出错还是只是变慢？
2. 为什么在途占位也要有过期时间？不过期会出什么事故？
3. 清理任务的互斥为什么必须跨进程？（提示：多副本部署 + 定时任务在每个副本都会触发）

---

## 收束：把七站压成四句话

- **限流**：网络的 HMAC 摘要进 `rate_limit_locks` 抢行锁，再数 1 小时窗口内的事件数；写在业务之前的独立事务里。
- **幂等**：`Idempotency-Key` + 请求体 SHA-256 进 `idempotency_records`，唯一约束仲裁并发；成功写响应、失败释放、敏感结果标 `response_replayable = false`。
- **并发**：`If-Match` 给用户可理解的 409，悲观锁让服务器串行，`@Version` 兜住漏网之鱼；三者叠加而不是三选一。
- **分布式**：行锁管短事务计数，`GET_LOCK` 管不可并行的定时任务，租约列管可恢复的任务领取，唯一约束做最后兜底。
