# VH-04 题单：费曼理解输出 + 秋招面试追问链

使用方式：先合上 `01`～`03`，按题序**闭卷写下来**（不是心里想一遍）。全部答完后再向后对照文末折叠答案。

题目来源标注：`代码` = 本轮源码阅读，`风险` = 测试盲区或设计取舍，`面经` = 公开面经常见问法，`边界` = 官方能力边界（HTTP 语义 / JPA / MySQL）。

---

## 第一层：费曼理解输出

### F1 五个机制各自解决什么问题

- 答题范围：幂等键、乐观锁、悲观锁、唯一约束、限流。
- 可选线索：它们分别防「重复」「丢更新」「服务器内竞态」「脏数据」「滥用」中的哪一类。
- 预期深度：一张 5 行表，每行给一个「不加会怎样」的具体后果。约 250 字。来源：`代码` + `面经`

### F2 `Idempotency-Key` 的作用域和生命周期

- 答题范围：键存在哪张表、和什么一起做唯一、什么时候开始生效、什么时候消失。
- 可选线索：`request_hash` 为什么也要存；`response_status = 102` 是什么状态。
- 预期深度：说清「一个键 = 一次逻辑提交」，并给出在途、完成、过期三种行状态。约 250 字。来源：`代码` + `边界`

### F3 为什么幂等预占要用 `REQUIRES_NEW`

- 答题范围：预占事务与业务事务的关系。
- 可选线索：如果预占和业务在同一个事务里，两个并发请求会各自看到什么。
- 预期深度：说清「并发仲裁需要立即可见」这一点，并说明失败时为什么必须显式 `release`。约 250 字。来源：`代码`

### F4 同一个键换了请求体，为什么返回 409 而不是覆盖

- 答题范围：`request_hash` 的比较时机与语义。
- 可选线索：`IDEMPOTENCY_KEY_REUSED` 与 `SENSITIVE_RESULT_NOT_REPLAYABLE` 的区别。
- 预期深度：解释「幂等键标识一次提交，不是标识一个接口」。约 200 字。来源：`代码` + `面经`

### F5 `execute` 与 `executeSingleDelivery` 的差别

- 答题范围：`response_replayable` 与响应体的存法。
- 可选线索：哪些接口用哪一个；公开投稿为什么属于后者。
- 预期深度：说清「可回放」的边界，并回答：重放拿到 409 算不算违背幂等承诺？约 250 字。来源：`代码` + `风险`

### F6 `If-Match` 算不算乐观锁

- 答题范围：客户端版本比对、JPA `@Version`、悲观锁三者的分工。
- 可选线索：`revise` 为什么既加锁又比对；`decide` 为什么只比对。
- 预期深度：给一张「防线 → 挡住的失败模式 → 用户看到什么」的表。约 300 字。来源：`边界` + `代码`

### F7 并发限流为什么不会超发

- 答题范围：`rate_limit_locks` 与 `select ... for update`。
- 可选线索：先数后插的竞态；`on duplicate key update subject_hash = subject_hash` 的作用。
- 预期深度：用「两条并发请求各走一遍」的方式讲清串行化点，并给出实测数字 8 并发 → 2 通过。约 300 字。来源：`代码` + `边界`

### F8 `GET_LOCK` 和租约列怎么选

- 答题范围：两种互斥实现的可恢复性差异。
- 可选线索：连接级锁 vs 有 `locked_until` 的行；任务能不能重复执行。
- 预期深度：各举一个项目里的真实调用点，并说明「进程崩溃」时各自的表现。约 300 字。来源：`代码` + `面经`

### F9 为什么冲突要分成这么多个错误码

- 答题范围：`ApiExceptionHandler` 的兜底码与各 Service 的业务码。
- 可选线索：`CONTENT_VERSION_CONFLICT`、`ACCOUNT_VERSION_CONFLICT`、`DATA_INTEGRITY_CONFLICT` 的命名规律。
- 预期深度：说清「同一个数据库错误，不同前端处理」这一动机。约 200 字。来源：`代码` + `风险`

---

## 第二层：秋招面试模拟追问链

规则：每条链先给 30 秒结论，再展开 3 分钟；追问链要能连续接住。

### C1 「你们平台怎么保证用户重复提交不会创建两条数据？」

30 秒要求：一句话说明「前端按钮只是体验，服务端幂等键 + 唯一约束才是保证」。
3 分钟要求：讲清 `Idempotency-Key` → `request_hash` → 102 占位 → 业务执行 → 写回响应 → 重放/释放的完整状态机，并说明独立事务。
追问链：

1. 前端已经在按钮上做了防重复点击，为什么还不够？
2. 同一个键同时到两个请求，谁赢？靠什么仲裁？
3. 第一次请求执行到一半进程挂了，用户重试会看到什么？
4. 幂等记录会无限增长吗？谁清理？
5. 响应里带一次性凭据时，重放要不要把凭据再给一次？

### C2 「两个人同时编辑同一条内容怎么办？」

30 秒要求：`If-Match` 给用户可理解的 409，悲观锁让服务器串行，`@Version` 兜底。
3 分钟要求：讲清 `revise` 的执行顺序（加锁 → 比对版本 → 新建候选修订 → 更新修订号），并给出「一成一败」的实测结果。
追问链：

1. 只加 `If-Match` 不加锁，会退化成什么错误？
2. 只加锁不加 `If-Match`，用户体验会怎么变？
3. `version` 和 `revision_number` 有什么区别？
4. 冲突之后前端应该做什么？为什么不是简单重试？
5. 你怎么证明你的并发保护真的生效了，而不是「测试跑绿了」？

### C3 「你们是单机还是多副本？多副本下限流和定时任务怎么保证正确？」

30 秒要求：状态放在 MySQL，而不是 JVM 内存；互斥靠数据库，而不是 `synchronized`。
3 分钟要求：分别说明限流（行锁表）、定时任务（`GET_LOCK`）、可恢复任务（租约列）、最终兜底（唯一约束）四种手段的适用场景。
追问链：

1. `synchronized` 和 `ReentrantLock` 在多副本下为什么失效？
2. 为什么不用 Redis？如果用了 Redis，要额外解决什么问题？
3. Redis 分布式锁和数据库行锁，各自的失败模式是什么？
4. 定时任务用 `GET_LOCK`，如果实例持有锁时被 kill -9 了呢？
5. 租约过期后同一任务被两个实例同时执行，靠什么保证不出错？

### C4 「幂等和限流的表会不会越写越大？上面存了用户信息吗？」

30 秒要求：有过期时间、有清理任务、主体只存 HMAC 摘要。
3 分钟要求：说明保留周期（幂等 24 小时）、清理任务如何互斥、为什么主体要 HMAC 而不是明文 IP/登录名。
追问链：

1. 清理任务漏跑一天，系统会坏还是只是变慢？
2. 为什么主体用 HMAC 而不是 SHA-256？
3. 幂等记录里存的响应体如果含敏感字段，会有什么风险？
4. `V11` 迁移里为什么有一条 `DELETE ... WHERE response_body LIKE '%activationToken%'`？
5. 如果要做多地域部署，这套方案哪里会先出问题？

---

## 参考答案（请先闭卷作答再看）

<details>
<summary>F1 五个机制的分工</summary>

| 机制 | 解决的问题 | 不加会怎样 |
|---|---|---|
| `Idempotency-Key` + `idempotency_records` | 同一个逻辑提交被重发 | 双击/重试/网络重发各创建一条数据 |
| `If-Match` + `@Version`（乐观） | 用户基于过期状态提交 | 后到的请求静默覆盖先到的修改（丢更新） |
| `findByIdForUpdate`（悲观） | 服务器内部两个请求同时改同一行 | 撞唯一约束，返回 500 或 `DATA_INTEGRITY_CONFLICT` |
| 唯一约束 | 任何漏网的重复写入 | 脏数据（两个同号账号等） |
| 限流 | 单个主体的滥用与刷量 | 数据库与外部资源被打爆 |

顺序上它们是**叠加**而不是替代：限流管「能不能发」，幂等管「是不是同一次」，版本管「是不是最新状态」，唯一约束是最后的数据库级真相。

常见误区：把乐观锁说成「一种锁」。项目里的 `If-Match` 本质是**客户端版本比对**，`@Version` 才是 JPA 的乐观锁实现，两者叠在一起用。
</details>

<details>
<summary>F2 幂等键的作用域与生命周期</summary>

表：`idempotency_records`（`V1__initial_schema.sql` 第 224 行）。

```text
UNIQUE (idempotency_key)          -- 仲裁者：同一个键在库里只可能有一行
request_hash CHAR(64)             -- 请求体 SHA-256，用于识别「同键异体」
response_status SMALLINT          -- 102 = 在途；200 = 已完成
response_body JSON                -- 成功响应的快照
response_replayable BOOLEAN       -- V11 新增：敏感结果标 false
expires_at                        -- 过期时间
```

三种行状态：

| 状态 | 含义 | 再次收到同一个键 |
|---|---|---|
| 不存在 | 没人用过 | 正常执行 |
| `response_status = 102` | 另一个请求正在处理 | 409 `REQUEST_IN_PROGRESS` |
| `response_status = 200` | 已完成 | 哈希一致且可回放 → 返回存的响应；否则 409 |

作用域是**整个表**，不是「每个接口一个命名空间」。所以键应该由客户端按「一次逻辑提交」生成（例如 UUID），不要用会跨接口复用的固定值。

生命周期：在途占位默认 86400 秒；完成后写 `expires_at = now + 86400`；由 `RetentionService` 定期删除过期行。
</details>

<details>
<summary>F3 为什么预占必须 REQUIRES_NEW</summary>

关键在于「可见性」和「提交时机」：

1. 如果预占和业务在同一个事务里（默认 `REQUIRED`），那么第二个并发请求在第一个事务**提交之前**查不到那行占位——唯一索引在未提交时不会让其它事务看见冲突行，于是两个请求可能都认为自己拿到了所有权。
2. 用 `REQUIRES_NEW` 后，预占立刻提交，其它事务马上能看到 `idempotency_key` 的唯一冲突，接管竞态。
3. 代价是：**业务事务回滚不会带走预占**。因此 `IdempotencyService` 在 `catch (RuntimeException | Error)` 里显式调用 `reservations.release`（第 32～35 行）。
4. 如果连 `release` 都没跑到（进程被杀），就轮到过期机制兜底——这就是为什么在途占位也必须带 `expires_at`，且 `reserve` 开头要删过期的 102 行。

一句话：**短事务预占 + 显式释放 + 过期自愈**，三者缺一不可。
</details>

<details>
<summary>F4 同键异体为什么是 409</summary>

`request_hash = sha256(json(业务对象))`，存进表里和键一起做判断。

语义是：「这个键代表**某一次**提交，不是**某个接口**」。同一个键配不同内容，说明客户端要么复用了键，要么改完内容没换键——两种情况下服务端都无法判断用户到底想要哪一次，于是拒绝并提示换新键。

实测：

```text
同键同体  → 409 SENSITIVE_RESULT_NOT_REPLAYABLE（公开投稿路径）或 200 回放（管理员路径）
同键异体  → 409 IDEMPOTENCY_KEY_REUSED
```

注意哈希的对象是**业务对象的序列化结果**，不是原始 HTTP 字节：字段顺序由服务端序列化决定，多余空白与无关字段不会造成误判。代价是——同一个语义但字段名不同的两种请求体（例如前端历史上改过字段名）会被判成「异体」。
</details>

<details>
<summary>F5 execute 与 executeSingleDelivery</summary>

| 维度 | `execute` | `executeSingleDelivery` |
|---|---|---|
| 完成后写入 | `response_replayable = true` + 真实响应快照 | `response_replayable = false` + `{"state":"DELIVERED_NO_REPLAY"}` |
| 重放行为 | 反序列化快照返回 200 | 409 `SENSITIVE_RESULT_NOT_REPLAYABLE` |
| 适用接口 | 建内容、改内容、审核决定 | 公开投稿/合作申请（返回 `queryCode`）、激活凭据、密码重置凭据、邀请码 |

「重放拿到 409」**不违背**幂等承诺：幂等保证的是「不会产生第二条业务记录」，而不是「必须返回同一个响应体」。这一点在面试里区分度很高——服务端宁可让管理员重新签发，也不愿把一次性秘密重复吐给可能是攻击者的调用方。

实测证据：A2 返回 409，而 `content_submissions` 里没有产生第二行。
</details>

<details>
<summary>F6 If-Match 与三道防线</summary>

| 防线 | 位置 | 挡住的失败模式 | 用户看到 |
|---|---|---|---|
| 悲观锁 | `findByIdForUpdate`（`ContentManagementService` 第 136、208、229 行） | 两个请求同时改同一行，算出同一个 `revision_number` | 无感，被排队 |
| `If-Match` 比对 | 第 140、178、230 行 | 用户手里是过期页面 | 409 `CONTENT_VERSION_CONFLICT` |
| JPA `@Version` | `ContentItem` 第 24 行 / `Account` 第 15 行 | 漏比对、跨事务丢更新 | 409，同义兜底 |

严格说：`If-Match` 是**应用层的显式版本校验**，`@Version` 才是 JPA 的乐观锁。项目两者都用，因为它们回答的是不同问题——前者回答「用户看的是哪一版」，后者回答「数据库里这一版被别人改过没有」。

实测：两个并发 `PATCH` 带同一个 `If-Match: 2`，一个 200（`revision → 3`），一个 409，没有 500。
</details>

<details>
<summary>F7 并发限流为什么不会超发</summary>

失败模式：`count(*)` 和 `insert` 之间有时间窗。两个线程可能同时数到 7（上限 8），然后各插一条 → 实际 9 条。

项目用一张专门的锁表把「同一主体」串行化：

```sql
-- 1) 保证锁行存在（第一次插入，之后变成空更新）
insert into rate_limit_locks (scope, subject_hash) values (?, ?)
  on duplicate key update subject_hash = subject_hash;
-- 2) 抢这一行的写锁；抢不到的请求在这里等
select scope from rate_limit_locks where scope = ? and subject_hash = ? for update;
-- 3) 此时再数再插，已经互斥
```

`on duplicate key update` 这个「空更新」的写法很关键：它既保证行存在，又不会改变任何数据，还能顺便拿到行锁。

实测（`DistributedSafetyIT`）：8 个线程同一屏障起跑，上限 2 → 通过 2、拒绝 6。

常见误区：说「加了唯一索引就不会超发」。唯一索引只能防「同一行重复」，防不住「多插了几行不同的记录」。
</details>

<details>
<summary>F8 GET_LOCK 与租约列怎么选</summary>

| 维度 | `GET_LOCK`（`MysqlAdvisoryLock`） | 租约列（`media_deletion_jobs.locked_until`） |
|---|---|---|
| 粒度 | 连接级命名锁 | 单行字段 |
| 拿不到锁 | 立刻返回 `false`，本次不执行 | 该行本轮跳过，等下个周期 |
| 进程崩溃 | 连接断开自动释放，**不会残留** | 锁留在行上，要等 `locked_until` 过期 |
| 是否需要任务幂等 | 需要（但不重复执行） | **必须**（同一任务可能被领取两次） |
| 项目用法 | `content-lifecycle`、`approved-retention-cleanup` 定时任务 | 媒体孤儿清理的逐条领取 |

选择依据是「任务能不能容忍重复执行」：内容生命周期与保留期清理是扫描+批量更新，重复执行代价高且可能产生重复审计，所以用命名锁做整体互斥；媒体删除是逐条幂等操作，用租约能换来崩溃后可恢复。

`GET_LOCK` 有个必须知道的坑：锁跟随**连接**。项目里特意用一次性的 `dataSource.getConnection()` 包住整个任务，而不是用连接池里的长连接——否则「锁还在、连接已归还」会让锁的归属变得不可预测。
</details>

<details>
<summary>F9 为什么要这么多冲突码</summary>

数据库层面的「冲突」其实是好几种不同的事故：

| 事故 | 兜底异常 | 响应码 |
|---|---|---|
| 实体被并发修改 | `ObjectOptimisticLockingFailureException` | `CONTENT_VERSION_CONFLICT` / `ACCOUNT_VERSION_CONFLICT` |
| 唯一键或外键被违反 | `DataIntegrityViolationException` | `DATA_INTEGRITY_CONFLICT` |
| 业务层显式版本比对失败 | `ApiException` | 各聚合自己的 `*_VERSION_CONFLICT` |

分码的动机是**让前端能分流**：`CONTENT_VERSION_CONFLICT` 提示「刷新后重试」，`IDEMPOTENCY_KEY_REUSED` 提示「换个请求标识」，`REQUEST_IN_PROGRESS` 提示「稍后用同一个标识重试」，429 则要按 `Retry-After` 等待。

命名规律：`<聚合>_VERSION_CONFLICT`。能按规律列举，比死记更稳。
</details>

<details>
<summary>C1～C4 容易被打断的点</summary>

C1 幂等：

- 前端防重复点击只是体验优化，绕过前端（脚本、重放、离线队列）就失效。
- 仲裁者是数据库唯一约束 `UNIQUE (idempotency_key)`，不是 Java 里的 `if` 判断。
- 挂掉后重试的结果取决于记录状态：102 未过期 → 409 `REQUEST_IN_PROGRESS`；已过期 → 自愈重跑。

C2 并发编辑：

- 只加 `If-Match` 会退化成 `DATA_INTEGRITY_CONFLICT`（撞修订号唯一约束），语义变粗。
- 只加锁则用户拿旧页面提交会被静默接受，等于丢更新。
- `version`（`content_items.version`，`@Version` 自增）≠ `revision_number`（`content_revisions` 的序号），实测中前者到 3 而后者只有 2 行。
- 冲突后前端应该保留用户输入并提示刷新，不是无脑重试。

C3 多副本：

- `synchronized` / `ReentrantLock` 只对单个 JVM 有效。
- 用 Redis 要额外解决锁续期、时钟漂移、Redis 自身故障时的语义（Redlock 的争议）；项目选 MySQL 是因为它本来就是唯一真相源，少一个组件、少一种一致性模型。
- 用测试证明「真并发」：多线程 + `CyclicBarrier` + 真实数据库（`DistributedSafetyIT`），而不是「快速点两下」。

C4 表增长与隐私：

- 幂等 24 小时、社群验证事件 7 天、非敏感异常摘要 14 天。
- 主体存 HMAC-SHA256：明文 IP/登录名会变成可关联的个人画像；裸 SHA-256 与 HMAC 的差别是「拿到数据库的人能不能反查」。
- 清理任务在多副本下每个实例都会触发，所以 `RetentionService` 用 `GET_LOCK` 保证同一时刻只有一个在跑。
