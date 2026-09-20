# VH-06 测试地图：现有测试证明了什么

## 0. 先跑一遍（不需要 Docker）

工作目录：`D:\Projects\voidhaven\voidhaven-platform\apps\api`

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform\apps\api'

.\mvnw.cmd -B -Dtest="PublicCaseServiceTest,PublicInputAdministrationServiceTest,PublicCaseAdministrationServiceTest,CommunityServiceTest,PublicWriteRateLimiterTest,PublicStatusQueryRateLimiterTest,ClientNetworkSubjectTest,PrivateValueCipherTest,ContentSourcePolicyTest,ProjectJoinServiceTest,ContentActionServiceTest,PublicMediaControllerTest" -DfailIfNoTests=false test
```

如果 `mvnw.cmd` 因为 wrapper jar 缺失而尝试下载，本机还有 `D:\Maven\apache-maven-3.9.4\bin\mvn.cmd`，把上面的 `.\mvnw.cmd` 换成它即可；`.m2` 已完整时加 `-o` 可离线跑。

### 本机实测（2026-09-19，AI 执行，仅作对照基线）

环境：JDK 17.0.18、Maven Wrapper（Surefire 3.5.6）、Spring Boot 4.1.1。

```text
CommunityServiceTest                    Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0
ContentActionServiceTest                Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0
ContentSourcePolicyTest                 Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0
PrivateValueCipherTest                  Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0
ProjectJoinServiceTest                  Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0
PublicCaseAdministrationServiceTest     Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0
PublicCaseServiceTest                   Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0
PublicInputAdministrationServiceTest    Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0
PublicStatusQueryRateLimiterTest        Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0
PublicWriteRateLimiterTest              Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0
ClientNetworkSubjectTest                Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0
PublicMediaControllerTest               Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0

Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS（Total time 12.761 s）
```

报告位置：`apps/api/target/surefire-reports/`。先看 `Tests run` 后面的 `Failures / Errors / Skipped`，再看 `Time elapsed`：最慢的 `CommunityServiceTest` 是 4.46 s（首次加载 JVM + Mockito），其余都在 1 s 内，说明这批测试是纯逻辑测试，没有真实 IO。

## 1. 逐条读测试在断言什么

### `service/CommunityServiceTest.java`（5 条）——门禁与入口管理的核心

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `update_rejects_stale_versions` | 库里版本 3，请求带 2 → `COMMUNITY_ENTRY_VERSION_CONFLICT` | `If-Match` 版本冲突在**读锁之后、写之前**被拦住 |
| `deactivate_clears_primary_in_the_same_update` | 停用语句里同时出现 `is_primary = false` | 停用主入口必须顺手摘掉"主"标记，否则唯一约束会挡住下一次设主 |
| `setting_primary_without_a_new_value_preserves_the_encrypted_entry` | 只改 `primary`、不带新值时，密文与 keyId 原样写回 | 受控值"缺省保留"语义；不会因为空值把密文覆盖掉 |
| `public_personal_contact_requires_an_explicit_risk_confirmation` | 公开邮箱渠道 + `PUBLIC` + 未确认 → `COMMUNITY_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED` | 高风险公开必须显式确认 |
| `public_listing_without_a_grant_only_loads_public_entries` | 捕获 SQL，断言含 `access_level = 'PUBLIC'` | **受控数据在 SQL 层被排除**（本链路最重要的安全断言之一） |

### `service/ProjectJoinServiceTest.java`（5 条）——与社群入口同构的另一条投影

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `public_listing_without_a_grant_only_loads_public_entries` | SQL 含 `from project_join_entries` 与 `access_level = 'PUBLIC'`；**不调用** `community.requireGrant` | 无 grant 时既不查受控行，也不做无意义的校验 |
| `public_listing_with_a_grant_reads_controlled_entries_through_the_shared_invite` | 调用 `requireGrant`，且 SQL **不再**含 PUBLIC 过滤 | grant 走社群那一套授权表 |
| `update_rejects_stale_versions` | `PROJECT_JOIN_ENTRY_VERSION_CONFLICT` | 版本冲突 |
| `public_personal_contact_requires_an_explicit_risk_confirmation` | `PROJECT_JOIN_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED` | 与社群一致的风险确认 |
| `controlled_values_are_encrypted_with_the_project_join_context` | `encrypt(value, "PROJECT_JOIN_ENTRY:<id>")` 且调用 `requireKeys()` | AAD 上下文按实体类型隔离 |

### `service/PublicCaseServiceTest.java`（2 条）——fail-closed 的边界

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `copyright_and_partner_contacts_fail_closed_without_calling_a_probe_encrypt` | 密钥环未配置时两类必填联系方式都抛 `PRIVATE_CONTACT_DISABLED`，且**不调用** `encrypt`、**不触碰** `JdbcTemplate` 与 `IdempotencyService` | 配置缺失时先拒，不留下半成品记录 |
| `optional_feedback_without_contact_does_not_require_the_cipher` | 无联系方式的反馈不要求 `requireEnabled`，且确实委派给幂等服务 | "可选联系方式"分支不会被误拦 |

证明边界：这两条只覆盖**校验发生的顺序**，不覆盖真实加解密与落库。

### `service/PublicInputAdministrationServiceTest.java`（3 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `treats_legacy_pending_review_filter_as_received` | `PENDING_REVIEW` 与 `null` 都归一为 `RECEIVED`；`ALL` 归一为 `null` | 列表筛选的向后兼容 |
| `maps_only_the_approved_public_input_review_decisions` | 只允许 `APPROVE→RESOLVED`、`REJECT→REJECTED`；`CHANGES_REQUESTED`/`PUBLISH` 抛错 | 简化决策的白名单 |
| `rejects_a_second_decision_after_another_admin_has_already_reviewed_the_submission` | 更新影响 0 行 → `SUBMISSION_NOT_ACTIONABLE`，且**不写审计** | 重复决策既不生效也不留假审计 |

### `service/PublicCaseAdministrationServiceTest.java`（5 条）——截止时间的"缺省保留、显式清除"

| 用例 | 断言 |
|---|---|
| `omitted_due_at_preserves_the_locked_database_value` | 不传 `dueAt` 时，写入参数仍是库里的旧值 |
| `due_at_is_cleared_only_when_explicitly_requested` | `clearDueAt=true` 才写 `null` |
| `setting_and_clearing_due_at_in_the_same_request_is_rejected` | 同时设置与清除 → `CASE_DUE_AT_CONFLICT`，且不执行任何 update |
| `an_explicit_due_at_replaces_the_previous_value` | 传新值则替换 |
| `a_stale_version_does_not_write_status_history` | 版本冲突时**不写** `public_case_status_history` |

### `service/PublicWriteRateLimiterTest.java`（2 条）与 `PublicStatusQueryRateLimiterTest.java`（2 条）

| 用例 | 断言 |
|---|---|
| `limits_a_hashed_network_subject_without_storing_the_raw_value` | 第 3 次超过额度 2 → `PUBLIC_WRITE_RATE_LIMITED`；对 `RateLimitLockService` 只传哈希值 |
| `refuses_the_development_secret_when_the_production_profile_is_active` | production profile + 开发密钥 → 启动期 `IllegalStateException` |
| `uses_a_separate_status_query_bucket` | 查状态走 `reservePublicStatusQuery(..., 60, ...)`，与写桶分离 |
| `surfaces_status_query_limit_without_consuming_write_quota` | 429 码是 `PUBLIC_STATUS_QUERY_RATE_LIMITED` |

这四条是**Mockito 委托验证**，不覆盖计数、并发与真实 SQL——真并发语义要靠 `DistributedSafetyIT`（见第 2 节）。

### `web/ClientNetworkSubjectTest.java`（2 条）

| 用例 | 断言 |
|---|---|
| `ignores_forwarded_address_when_direct_peer_is_not_a_trusted_proxy` | 直连地址不在受信网段 → 忽略 `X-Forwarded-For` |
| `accepts_only_a_single_numeric_address_from_a_trusted_proxy` | 受信代理 + 单值数字地址 → 采用；出现逗号（多跳）→ 回落直连地址 |

### 其余三条

| 用例 | 断言 |
|---|---|
| `ContentSourcePolicyTest`（3 条） | HTTPS/私网/保留域的放行与拒绝矩阵；本地 fixture 开关的例外 |
| `PrivateValueCipherTest`（3 条） | 加解密往返、AAD 不匹配时解密失败、密钥环禁用时的行为 |
| `PublicMediaControllerTest`（1 条） | 公开媒体读取返回 `Cache-Control: no-store` |
| `ContentActionServiceTest`（5 条） | 招募行动入口的准备、校验与公开投影（含 `COMMUNITY_GATED` 分支） |

## 2. 集成测试（需要 Docker，本轮未执行）

| 文件 | 断言要点 | 证明什么 |
|---|---|---|
| `integration/MySqlAndMinioIntegrationTest.java`（`@Tag("integration")`） | Flyway 迁移后表结构存在；MinIO 私有桶可用 | V13/V14/V20/V24 的迁移在真实 MySQL 上可跑 |
| `integration/DistributedSafetyIT.java`（`@Tag("integration")`） | 多线程同时抢同一额度，恰好放行 N 条；成功登录会清理失败计数 | 限流的**真并发**语义——这正是邀请码占额、公开写限流所依赖的同一套原语 |

两条都用 `Assumptions.assumeTrue(dockerAvailable(), ...)`，Docker 不可用时**跳过而不是失败**。所以「集成测试通过」必须附带「Docker 可用」这个前提。

```powershell
# 需要 Docker 正在运行
.\mvnw.cmd -o -Dit.test='DistributedSafetyIT' -DfailIfNoTests=false verify
```

本轮未执行的理由：本机 Docker 未运行，且拉起 Testcontainers 会与学习栈抢占端口与内存。这属于你的可选项。

## 3. 自动化测试的盲区（答辩时要能自己说出来）

| 盲区 | 现状 | 影响 |
|---|---|---|
| 没有 HTTP 层测试 | 全仓库没有 `@SpringBootTest` + `MockMvc` 覆盖 `/public/submissions`、`/community-gate/verify` | 状态码、响应体形状、`Retry-After`、`Cache-Control` 全靠手动复现 |
| 幂等语义无自动化证据 | `IdempotencyServiceTest` 存在，但没有针对 `executeSingleDelivery` 的"重复请求返回 409"端到端用例 | 「查询码不会回放」这条安全属性没有测试护栏 |
| 邀请码占额无并发测试 | 没有针对 `community_invite_batches.use_count` 的多线程用例 | 超发风险只能靠 SQL 写法推理 + 手动并发实验 |
| 失败计数闭环无测试 | `verify` 的冷却分支（≥8 次失败）没有任何单测 | F-01 那条"事件可能被回滚"的疑点因此长期无人发现 |
| grant 生命周期无测试 | 没有断言"撤销批次后旧 grant 立刻失效" | `requireGrant` 的 JOIN 条件改动会静默破坏撤销语义 |
| 私密联系方式端到端无测试 | 只有 `PrivateValueCipherTest` 的算法测试 | `delete_after` 到期删除、后台解密审计没有自动化覆盖 |
| 前端契约只有纯函数测试 | `public-submissions.test.ts`、`partner-applications.test.ts` 只断言状态选项数组 | 表单 → 接口 → 错误提示的联动无测试 |
| 无 `@Valid` 边界用例 | 请求体长度/必填/枚举非法值的真实响应形状无测试 | "400 VALIDATION_FAILED + fieldErrors" 只能手动确认 |

## 4. 一句话总结

现有 38 条测试把**领域规则**（状态白名单、版本冲突、截止时间语义、投影 SQL）和**配置 fail-closed**（生产密钥、密钥环）钉得比较稳；而**凭据生命周期**（幂等回放、邀请码占额、grant 撤销、失败冷却）和**HTTP 语义**几乎没有自动化护栏，这部分必须靠你在真实栈上复现并留记录。
