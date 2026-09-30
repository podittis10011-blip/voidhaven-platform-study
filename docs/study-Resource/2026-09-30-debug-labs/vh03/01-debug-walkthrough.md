# VH03 断点与夹具地图

先读 [README](README.md) 完成环境和探针安装，再配合 [逐站脚本](03-step-runbook.md)。所有路径相对学习仓库根目录。表中行号已对照当前源码；声明行不可停时选下一条可执行语句。

## D1 资格复查与会话失效

**夹具与输入**：认证 principal=partner-one，仓储按 loginId 返回已 suspend 的账号；MockHttpSession 和 MockHttpServletResponse 为真实测试对象。

| 精确驱动方法 | 文件位置 |
|---|---|
| `AccountEligibilityFilterTest#invalidates_an_existing_session_when_the_account_is_no_longer_active` | `apps/api/src/test/java/cn/voidhaven/api/web/AccountEligibilityFilterTest.java:25` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D1-B1 | `apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java:46` | `Authentication authentication =` |
| D1-B2 | `apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java:49` | `accounts.findByLoginId(` |
| D1-B3 | `apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java:54` | `if (session != null) session.invalidate();` |
| D1-B4 | `apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java:56` | `SecurityContextHolder.clearContext();` |

**证据边界**：手工 doFilter；不证明 Spring 注册顺序、Cookie 传输及下一个真实 HTTP 请求。

## D2 真实 Argon2 与密码重置边界

**夹具与输入**：探针直接调用 SecurityConfig.passwordEncoder()，真实编码器；raw 为合成字符串。原 PasswordServiceTest 则使用 mock PasswordEncoder，两者不能混淆。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PasswordEncoderDebugProbeTest#real_encoder_uses_random_salt_and_checks_passwords` | `docs/study-Resource/2026-09-30-debug-labs/vh03/probe/PasswordEncoderDebugProbeTest.java:8` |
| `PasswordServiceTest#consuming_manual_reset_invalidates_remaining_manual_tokens_and_sessions` | `apps/api/src/test/java/cn/voidhaven/api/service/PasswordServiceTest.java:40` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D2-B1 | `apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java:40` | `passwordEncoder() { return Argon2PasswordEncoder` |
| D2-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/PasswordService.java:68` | `public String consumeReset(` |
| D2-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/PasswordService.java:77` | `deleteSessions(account.getLoginId());` |

**证据边界**：真实编码器行为已可测试；直接 new SecurityConfig 不证明容器装配，密码策略另在服务/请求校验层。

## D3 登录完成委托

**夹具与输入**：RateLimitLockService 为 mock；依次调用 loginFailed("failed-attempt") 与 loginSucceeded("successful-attempt")。

| 精确驱动方法 | 文件位置 |
|---|---|
| `AuthenticationRateLimiterTest#delegates_login_completion_to_the_same_subject_lock_transaction` | `apps/api/src/test/java/cn/voidhaven/api/service/AuthenticationRateLimiterTest.java:9` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D3-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/AuthenticationRateLimiter.java:56` | `public void loginFailed(` |
| D3-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/AuthenticationRateLimiter.java:60` | `public void loginSucceeded(` |

**证据边界**：只能证明委托协议，不能据此写“已证明并发计数不丢”；真实事务和锁在 VH04 D5。

## D4 账号状态转换

**夹具与输入**：既有夹具提供账号与 mock 仓储/审计；分别覆盖停用、恢复、撤销终态和超管保护。

| 精确驱动方法 | 文件位置 |
|---|---|
| `AccountAdministrationServiceTest#suspends_an_account_and_writes_a_minimal_audit_event` | `apps/api/src/test/java/cn/voidhaven/api/service/AccountAdministrationServiceTest.java:20` |
| `AccountAdministrationServiceTest#suspended_accounts_can_be_reactivated_but_revoked_accounts_are_final` | `apps/api/src/test/java/cn/voidhaven/api/service/AccountAdministrationServiceTest.java:41` |
| `AccountAdministrationServiceTest#refuses_to_mutate_the_super_admin_account` | `apps/api/src/test/java/cn/voidhaven/api/service/AccountAdministrationServiceTest.java:82` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D4-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/AccountAdministrationService.java:133` | `ACCOUNT_REVOKED_FINAL` |
| D4-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/AccountAdministrationService.java:163` | `ACCOUNT_SUPER_ADMIN_IMMUTABLE` |
| D4-B3 | `apps/api/src/main/java/cn/voidhaven/api/domain/Account.java:39` | `public void suspend()` |

**证据边界**：管理员操作的服务规则；HTTP 角色授权和会话失效全链需另验。

## D5 激活令牌签发与消费

**夹具与输入**：内存 ActivationToken 实体真实；仓储 mock 按测试返回捕获到的 token；PasswordEncoder mock 返回 argon2-hash。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ActivationServiceTest#stores_only_a_hash_and_allows_the_token_to_be_consumed_once` | `apps/api/src/test/java/cn/voidhaven/api/service/ActivationServiceTest.java:22` |
| `ActivationServiceTest#refuses_to_activate_a_revoked_account` | `apps/api/src/test/java/cn/voidhaven/api/service/ActivationServiceTest.java:56` |
| `ActivationServiceTest#issuing_or_consuming_activation_does_not_invalidate_password_reset_tokens` | `apps/api/src/test/java/cn/voidhaven/api/service/ActivationServiceTest.java:71` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D5-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ActivationService.java:61` | `public Account consume(` |
| D5-B2 | `apps/api/src/main/java/cn/voidhaven/api/domain/ActivationToken.java:24` | `public void consume(` |

**证据边界**：mock 查询不验证哈希 WHERE 是否真实匹配；没有过期夹具的分支不写成已测。

## D6 生产配置拒绝路径

**夹具与输入**：测试构造校验器并直接 run；所有地址和字符串仅作为内存校验输入，不发起数据库/对象存储连接。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ProductionConfigurationValidatorTest#acceptsExplicitSecureProductionConfiguration` | `apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:11` |
| `ProductionConfigurationValidatorTest#rejectsLocalPublicUrlAndPlaceholderDatabasePassword` | `apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:20` |
| `ProductionConfigurationValidatorTest#rejectsRootDatabaseAccountAndTrustAllProxyRange` | `apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:37` |
| `ProductionConfigurationValidatorTest#rejectsEnabledPrivateContactOrCommunityGateWithoutProductionSecrets` | `apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:65` |
| `ProductionConfigurationValidatorTest#acceptsCommunityGateWithIndependentProductionKeyRing` | `apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:95` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D6-B1 | `apps/api/src/main/java/cn/voidhaven/api/config/ProductionConfigurationValidator.java:77` | `public void run(` |
| D6-B2 | `apps/api/src/main/java/cn/voidhaven/api/config/ProductionConfigurationValidator.java:88` | `if (!cookieSecure)` |

**证据边界**：直接 run 证明校验逻辑；自动启动执行与 profile 装配是源码/真实栈项目。

## D7 首管引导与本地初始化

**夹具与输入**：Bootstrap fixture mock TransactionTemplate/仓储；count=1 拒绝，count=0 创建；LocalDataInitializerTest 手工构造，不是 profile 启动测试。

| 精确驱动方法 | 文件位置 |
|---|---|
| `BootstrapAdminCommandTest#requiresExplicitBootstrapAdminProfile` | `apps/api/src/test/java/cn/voidhaven/api/config/BootstrapAdminCommandTest.java:70` |
| `BootstrapAdminCommandTest#rejectsUnsupportedEnvironmentBeforeOpeningATransaction` | `apps/api/src/test/java/cn/voidhaven/api/config/BootstrapAdminCommandTest.java:99` |
| `BootstrapAdminCommandTest#refusesToBootstrapWhenAnAccountAlreadyExists` | `apps/api/src/test/java/cn/voidhaven/api/config/BootstrapAdminCommandTest.java:110` |
| `BootstrapAdminCommandTest#testServerBootstrapPrintsTheOneTimeActivationUrlAndAuditsWithASyntheticRequestId` | `apps/api/src/test/java/cn/voidhaven/api/config/BootstrapAdminCommandTest.java:122` |
| `LocalDataInitializerTest#assigns_module_key_before_the_first_content_insert` | `apps/api/src/test/java/cn/voidhaven/api/config/LocalDataInitializerTest.java:19` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D7-B1 | `apps/api/src/main/java/cn/voidhaven/api/config/BootstrapAdminCommand.java:53` | `public void run(` |
| D7-B2 | `apps/api/src/main/java/cn/voidhaven/api/config/BootstrapAdminCommand.java:61` | `accounts.count()` |

**证据边界**：首管支持 production/test-server 且需显式 bootstrap-admin，不是“只在 local”；本次仅跑 mock，不执行真实初始化命令。
