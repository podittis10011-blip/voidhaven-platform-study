# VH-04 测试地图：现有测试证明了什么

版本：HEAD `2295a41`（2026-09-19），JDK 17.0.18，Maven Wrapper，MySQL 8.4.11（Testcontainers）。

## 1. 与 VH-04 相关的测试资产

| 测试类 | 条数 | 覆盖的 VH-04 行为 |
|---|---:|---|
| `service/IdempotencyServiceTest` | 5 | 在途拒绝、键复用、失败释放、敏感不可回放、可回放响应的类型化还原 |
| `service/PublicWriteRateLimiterTest` | 2 | 公开写入桶的额度与主体哈希 |
| `service/PublicStatusQueryRateLimiterTest` | 2 | 状态查询独立桶 |
| `service/AuthenticationRateLimiterTest` | 1 | 登录尝试登记 |
| `service/ContentManagementServiceTest` | 4 | `CONTENT_VERSION_CONFLICT`（含「拒绝基于过期版本审核」） |
| `service/CommunityServiceTest` | 5 | `COMMUNITY_ENTRY_VERSION_CONFLICT` |
| `service/CatalogOrderServiceTest` | 3 | `CATALOG_VERSION_CONFLICT` |
| `service/HomepageAdministrationServiceTest` | 4 | `HOMEPAGE_VERSION_CONFLICT` |
| `service/ProjectJoinServiceTest` | 5 | `PROJECT_JOIN_ENTRY_VERSION_CONFLICT` |
| `service/PublicCaseAdministrationServiceTest` | 5 | `CASE_VERSION_CONFLICT` |
| `service/MediaOrphanCleanupServiceTest` | 3 | 媒体删除任务的租约领取与完成条件 |
| `integration/DistributedSafetyIT` | 1（长测试） | **真实 MySQL**：并发限流、在途锁、邀请码并发、`GET_LOCK`、媒体租约、唯一约束 |
| `integration/MySqlAndMinioIntegrationTest` | — | 断言 `media_deletion_jobs.locked_by` 列存在（迁移生效） |

`DistributedSafetyIT` 的断言是 VH-04 最硬的一条证据，它用 8 个线程 + `CyclicBarrier` 同时起跑，逐项验证：

| 断言 | 期望 |
|---|---|
| 8 个并发公开写入，上限 2 | 通过 2、拒绝 6 |
| 状态查询上限 2 | 第 3 次抛 `PUBLIC_STATUS_QUERY_RATE_LIMITED`，且**不污染**公开写入表 |
| 8 个并发登录，上限 5 | 通过 5、拒绝 3 |
| 失败后成功登录 | 该登录名的 `FAILURE` 行被清空 |
| 重复调用 `loginSucceeded` | 不会把后来的失败记录一并清掉 |
| 8 个并发兑换同一邀请码（上限 2） | 通过 2、拒绝 6，`use_count = 2` |
| `GET_LOCK` 互斥 | 第二个 `runExclusive` 直接返回 `false` |
| 媒体删除任务租约 | A 领取成功、B 领取失败，`locked_by` 仍是 A |
| 并发插同一个 `login_id` | 恰好 1 条成功、1 条冲突，表里只有 1 行 |

## 2. 本轮实跑命令与结果

工作目录：`D:\Projects\voidhaven\voidhaven-platform\apps\api`

### 2.1 定向跑 VH-04 相关单测

```powershell
.\mvnw.cmd -o -B test "-Dtest=IdempotencyServiceTest,PublicWriteRateLimiterTest,PublicStatusQueryRateLimiterTest,AuthenticationRateLimiterTest,ContentManagementServiceTest,CommunityServiceTest,CatalogOrderServiceTest,HomepageAdministrationServiceTest,ProjectJoinServiceTest,PublicCaseAdministrationServiceTest,MediaOrphanCleanupServiceTest" "-DfailIfNoSpecifiedTests=false"
```

结果（2026-09-19）：

```text
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   Total time: 10.4 s
```

### 2.2 全量 verify（含集成测试）

```powershell
.\mvnw.cmd -o -B verify
```

结果（2026-09-19）：

```text
Surefire（单元测试）：46 个测试类，133 项，Failures 0 / Errors 0 / Skipped 0
Failsafe（集成测试）：cn.voidhaven.api.integration.DistributedSafetyIT
    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   Time elapsed: 29.82 s
    → 容器 mysql:8.4 启动成功，Flyway 空库 V1→V24（24 个迁移）成功
BUILD SUCCESS   Total time: 02:08 min
```

读报告的两点常识：

1. `target/surefire-reports/` 放单元测试结果，`target/failsafe-reports/` 放 `*IT` 集成测试结果。默认 `*IT` 不会被 surefire 执行，所以「只跑 `test`」看不到分布式安全这一条。
2. `DistributedSafetyIT` 开头有 `Assumptions.assumeTrue(dockerAvailable(), ...)`。Docker 不可用时它**不是失败而是跳过**——如果只看「构建成功」就以为并发被测过，是常见误判。本轮实测它是真跑了的（日志里有 `Container mysql:8.4 started`）。

## 3. 行为 × 证据层矩阵

图例：✔ 有自动化证据；△ 只有代码阅读或手动构造；✘ 无。

| VH-04 行为 | 单元测试 | 真实 MySQL 集成 | 本轮 HTTP 实测 |
|---|:--:|:--:|:--:|
| 同键同体重放返回同一响应 | ✔ | ✘ | ✔（C3） |
| 同键异体 → `IDEMPOTENCY_KEY_REUSED` | ✔ | ✘ | ✔（A3、C4） |
| 在途占位 → `REQUEST_IN_PROGRESS` | ✔（mock） | ✘ | ✔（D1，手工构造 102 行） |
| 敏感结果不回放 | ✔ | ✘ | ✔（A2） |
| 业务失败释放预占 | ✔ | ✘ | ✔（C2 后 DB 中无该键） |
| 缺幂等键 → 400 | ✘ | ✘ | ✔（A4） |
| 幂等键超长 → 400 | ✘ | ✘ | △（代码第 22 行，未实测） |
| 过期 102 行被自愈删除 | ✘ | ✘ | ✔（D2，改 `expires_at` 后重发成功） |
| 并发限流不超发 | ✘ | ✔ | ✔（B5 达到 8 后 429） |
| 登录失败计数与成功清零 | ✔ | ✔ | ✔（F 组 5×401 → 429） |
| `GET_LOCK` 跨进程互斥 | ✘ | ✔（同 JVM 两线程） | ✘ |
| 媒体租约互斥 / 完成条件带 `locked_by` | ✔ | ✔ | ✘ |
| 唯一约束并发兜底 | ✘ | ✔ | ✘ |
| `If-Match` 过期 → 409 | ✔ | ✘ | ✔（C2） |
| 并发同版本 → 一成一败 | ✘ | ✘ | ✔（E1） |
| `@Version` 作为独立兜底 | ✘ | ✘ | ✘ |
| 24 小时过期与 `RetentionService` 清理 | ✘ | ✘ | △（仅读代码与 `data-handling.md`） |

## 4. 盲区（面试里被追问「怎么保证」时不要吹）

1. **`RetentionService` 的清理 SQL 没有测试覆盖。** 幂等记录、限流事件的过期删除只在 `DistributedSafetyIT` 之外的地方，靠代码阅读。
2. **在途占位（102）没有自动化测试。** 单元测试用 mock 模拟 `Existing(102)`，`DistributedSafetyIT` 不覆盖它。本轮 HTTP 实测是靠手工 `insert` 一行 102 造出来的。
3. **`DistributedSafetyIT` 全部在同一 JVM 内。** 「8 个线程」证明的是数据库层串行化，不是「两个真实实例」。真正跨实例要起两个进程连同一个库。
4. **媒体租约只验证了 SQL 语义。** `claimedA = 1 / claimedB = 0` 是直接跑 SQL 断言的，没有经过 `MediaOrphanCleanupService` 的完整路径。
5. **前端对 409 / 429 的处理没有对齐测试。** e2e 用 mock 数据，冲突分支基本不可达。
6. **`returnTo` 之类的旧结论不要复用。** 早期文档里的「未覆盖项」清单已随 V22～V24 变化，引用前先看当前报告。

## 5. 一句话自检

如果只能记一句：**「单元测试证明状态机，集成测试证明并发，HTTP 实测证明契约」**。三层都不能互相替代；本轮三层都有，但第 4 节列出的位置仍然只有代码阅读。
