# VH-03 测试地图：现有测试证明了什么

## 0. 先跑一遍（离线也能跑）

工作目录：`D:\Projects\voidhaven\voidhaven-platform\apps\api`

```powershell
Set-Location 'D:\Projects\voidhaven\voidhaven-platform\apps\api'

# VH-03 相关的 8 个纯单元测试类，不依赖 MySQL / MinIO / Docker
mvn -o -Dtest='AccountEligibilityFilterTest,ClientNetworkSubjectTest,AuthenticationRateLimiterTest,AccountAdministrationServiceTest,ActivationServiceTest,PasswordServiceTest,BootstrapAdminCommandTest,ProductionConfigurationValidatorTest' test
```

本机实测（2026-09-18，JDK 17.0.18 / Maven 3.9.4，离线模式）：

```text
BootstrapAdminCommandTest            Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
ProductionConfigurationValidatorTest Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
AccountAdministrationServiceTest     Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
ActivationServiceTest                Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
AuthenticationRateLimiterTest        Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
PasswordServiceTest                  Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
AccountEligibilityFilterTest         Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
ClientNetworkSubjectTest             Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS（Total time 8.310 s）
```

报告位置：`apps/api/target/surefire-reports/`。先看 `Tests run` 后面的 `Failures / Errors / Skipped`，再看 `Time elapsed`——本轮最慢的一条是 `BootstrapAdminCommandTest`（2.9 s，带输出捕获扩展），其余都在 0.3 s 内，说明它们都是纯逻辑测试，没有任何 IO。

两条提示：

1. 仓库里的 `mvnw.cmd` 没有 wrapper jar，离线环境会尝试下载发行包；本机已有 `D:\Maven\apache-maven-3.9.4`，直接用 `mvn` 更稳。
2. `-o` 是离线模式。本机 `.m2` 已完整，这 8 个类离线可跑。

## 1. 逐条读测试在断言什么

### `web/AccountEligibilityFilterTest.java`（1 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `invalidates_an_existing_session_when_the_account_is_no_longer_active` | 已 `suspend()` 的账号 + 已认证上下文 → 响应 401、响应体含 `ACCOUNT_INACTIVE`、`session.isInvalid()` 为真、`SecurityContextHolder` 被清空 | 资格复查的核心分支 |

注意它的证明边界：测试直接 `new AccountEligibilityFilter(accounts)` 手工调用，**没有**经过 Spring 容器，所以它不证明这条过滤器在过滤链里的位置，也不证明它真的被装上去了。

### `web/ClientNetworkSubjectTest.java`（2 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `ignores_forwarded_address_when_direct_peer_is_not_a_trusted_proxy` | 直连地址不在受信网段时，`X-Forwarded-For` 被忽略 | 不能被伪造头绕过网络额度 |
| `accepts_only_a_single_numeric_address_from_a_trusted_proxy` | 受信代理 + 单个合法数字地址 → 采用；出现逗号（多跳）→ 回落直连地址 | 只接受「一跳、纯数字」的转发地址 |

### `service/AuthenticationRateLimiterTest.java`（1 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `delegates_login_completion_to_the_same_subject_lock_transaction` | `loginFailed` / `loginSucceeded` 分别转成 `completeLoginAttempt(id,false/true)` | 委托关系 |

这条是纯 Mockito 验证，**不覆盖计数与并发**。真正的并发证据在集成测试里（见第 2 节）。

### `service/AccountAdministrationServiceTest.java`（6 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `suspends_an_account_and_writes_a_minimal_audit_event` | 停用后 `qualificationStatus=SUSPENDED`、`isActive()==false`，并且写入 `ACCOUNT_QUALIFICATION_CHANGED` 审计 | 停用 + 审计 |
| `rejects_self_disable_and_invalid_target_status` | 不能操作自身账号；不能把目标改成 `ACTIVE` | 两条业务护栏 |
| `suspended_accounts_can_be_reactivated_but_revoked_accounts_are_final` | `suspend → reactivate → revoke`，再次 `reactivate` 抛 `ACCOUNT_REVOKED_FINAL` | 资格状态机 |
| `creates_a_pending_admin_without_a_partner_profile` | 新建管理员是 `PENDING`、角色 `ADMIN`、返回一次性激活凭据；登录名会做归一化（`" admin.example "` → `admin.example`） | 建号流程 |
| `refuses_to_reissue_activation_for_a_non_pending_account` | 非 `PENDING` 账号补发激活 → `ACCOUNT_NOT_PENDING` | 激活凭据边界 |
| `refuses_to_mutate_the_super_admin_account` | 撤销超管 → `ACCOUNT_SUPER_ADMIN_IMMUTABLE` | 超管不可变 |

### `service/ActivationServiceTest.java`（3 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `stores_only_a_hash_and_allows_the_token_to_be_consumed_once` | 原始 token > 32 字符，库里存的是 64 位摘要且不等于原文；消费一次后账号变 `ACTIVE`、密码被编码；第二次消费报「已使用」 | 凭据存储与单次使用 |
| `refuses_to_activate_a_revoked_account` | 对 `REVOKED` 账号消费激活凭据 → `ACTIVATION_TOKEN_INVALID`，状态不变 | 终态保护 |
| `issuing_or_consuming_activation_does_not_invalidate_password_reset_tokens` | 按类型作废，不误伤 `PASSWORD_RESET`；用重置凭据打激活接口 → 非法 | 凭据类型隔离 |

### `service/PasswordServiceTest.java`（2 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `reset_tokens_are_invalidated_only_by_password_reset_type` | 签发重置只作废同类型的旧凭据 | 类型隔离 |
| `consuming_manual_reset_invalidates_remaining_manual_tokens_and_sessions` | 消费重置后作废其余未用的重置凭据，并执行 `delete from SPRING_SESSION where PRINCIPAL_NAME = ?` | **改密即吊销全部会话**，且有测试兜住这条 SQL |

### `config/ProductionConfigurationValidatorTest.java`（5 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `acceptsExplicitSecureProductionConfiguration` | 合规配置可以启动 | 正向路径 |
| `rejectsLocalPublicUrlAndPlaceholderDatabasePassword` | 公网地址写 `http://localhost:8080`、数据库口令还是占位符 → 启动失败 | fail-closed |
| `rejectsRootDatabaseAccountAndTrustAllProxyRange` | 数据库用 `root`、受信代理写 `0.0.0.0/0` → 启动失败 | 最小权限 |
| `rejectsEnabledPrivateContactOrCommunityGateWithoutProductionSecrets` | 开启私密联系方式 / 社群门禁但没配生产密钥 → 启动失败 | 密钥强制 |
| `acceptsCommunityGateWithIndependentProductionKeyRing` | 配了独立密钥环 → 可启动 | 正向路径 |

盲区：这 5 条全部用 `cookieSecure = true` 构造，**没有一条覆盖 `cookieSecure=false` 时生产必须拒启动**的分支（那条断言在 `ProductionConfigurationValidator` 第 88 行）。

### `config/BootstrapAdminCommandTest.java`（5 条）

| 用例 | 断言 | 覆盖的行为 |
|---|---|---|
| `requiresExplicitBootstrapAdminProfile` | 组件只在 `bootstrap-admin` profile 生效 | 避免误跑 |
| `acceptsProductionAndTestServerButRejectsLocal` | 只认 `production` 与 `test-server`，且拒绝形似名（`test-server-lookalike`） | 环境白名单 |
| `rejectsUnsupportedEnvironmentBeforeOpeningATransaction` | 环境不合规时事务根本没开 | 先校验后动手 |
| `refusesToBootstrapWhenAnAccountAlreadyExists` | 账号表非空则拒绝 | 一次性命令 |
| `testServerBootstrapPrintsTheOneTimeActivationUrlAndAuditsWithASyntheticRequestId` | 打印一次性激活链接，审计里带 26 位合成 requestId | 首次管理员交付 |

## 2. 集成测试（需要 Docker，本轮未执行）

| 文件 | 断言要点 | 证明什么 |
|---|---|---|
| `integration/MySqlAndMinioIntegrationTest.java`（`@Tag("integration")`） | Flyway 迁移后存在 `auth_rate_limit_events` 等表；MinIO 私有桶可用 | 迁移与存储真实可跑 |
| `integration/DistributedSafetyIT.java`（`@Tag("integration")`） | 8 线程同时 `beginLogin("admin.example", ...)` → **恰好 5 条放行、3 条被限流**；成功登录会清掉该登录名的 `FAILURE` 行；重复完成同一次尝试不会重复计数 | 限流的**真并发**语义（不是「快速连点」） |

两条测试都用 `Assumptions.assumeTrue(dockerAvailable(), ...)`，Docker 不可用时会跳过而不是失败——所以「集成测试通过」必须附上 Docker 可用这个前提。

想跑的话（需要 Docker 正在运行）：

```powershell
# 集成测试走 failsafe（*IT 命名），单元测试走 surefire
# 这条命令会同时跑单元测试，但只挑出 DistributedSafetyIT 这一条集成测试
mvn -o -Dit.test='DistributedSafetyIT' -DfailIfNoTests=false verify
```

本轮没有执行集成测试，理由是避免新建 Testcontainers 容器与现有学习栈抢端口；这属于你的可选验证。

## 3. 自动化测试的盲区（答辩时必须能自己说出来）

| 盲区 | 现状 | 影响 |
|---|---|---|
| 没有 HTTP 层安全测试 | 全仓库没有 `@SpringBootTest` + `MockMvc` 的 Web 测试 | 「401 / 403 / 429 的状态码与响应体形状」「过滤器顺序」都没有自动化证据，只能靠手动复现 |
| 没有 CSRF 正反用例 | 只有前端 `apps/web/src/lib/contracts.test.ts` 用 Mock 校验「写请求带了 header」 | 后端「缺令牌是否真拒绝、裸 cookie 值是否真拒绝」没有测试 |
| 没有会话固定化用例 | `changeSessionId()` 无测试 | 只能靠手动复现 E-01 证明 |
| 没有角色矩阵用例 | URL 规则与 `@PreAuthorize` 无测试 | PARTNER/ADMIN/SUPER_ADMIN 的组合只能靠手动复现 |
| 没有会话表断言 | 除 `PasswordServiceTest` 断言了删除 SQL，没有测试断言 `SPRING_SESSION` 真被写入/过期 | 会话「存得住」这件事在自动化层面是空的 |
| 过滤器装配无测试 | `AccountEligibilityFilterTest` 手工 new | 链内位置、是否被 Boot 二次注册，都无自动化证据 |
| 生产 cookie 强制无测试 | 见第 1 节盲区 | `cookieSecure=false` 的生产启动校验可能被改坏而无人发现 |

## 4. 一句话总结

现有测试的质量集中在**领域规则**（状态机、凭据类型隔离、配置 fail-closed、审计）和**并发原语**（限流行锁）；而**HTTP 语义**（状态码、cookie、CSRF、过滤器顺序）几乎没有自动化护栏，这部分只能靠你自己在真实栈上复现并留存记录。
