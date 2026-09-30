# VH03 单步脚本

每站先在 [观察表](04-observation-sheets.md) 写预测。以下预期来自源码/断言，交付方执行状态见 [台账](08-evidence-ledger.md)；不等于你已观察。

## D1 资格复查与会话失效

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `AccountEligibilityFilterTest#invalidates_an_existing_session_when_the_account_is_no_longer_active`

1. Debug 方法，停在取 authentication 行；F8 后看 authentication.getName()。查询依据是登录名，不是 accountId。
2. F8 越过账号查询并进入拒绝分支，记录 session 作废前后。
3. 执行 clearContext 后返回测试帧，记录 response.getStatus()、session.isInvalid() 与认证对象；F9 完成。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 过滤器帧 authentication.getName() | partner-one |
| 测试帧 response.getStatus() | 401 |
| 测试帧 session.isInvalid() | true |
| SecurityContextHolder.getContext().getAuthentication() | null |

## D2 真实 Argon2 与密码重置边界

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PasswordEncoderDebugProbeTest#real_encoder_uses_random_salt_and_checks_passwords`
- `PasswordServiceTest#consuming_manual_reset_invalidates_remaining_manual_tokens_and_sessions`

1. 安装 config 包探针，Debug 探针方法。在 String second = encoder.encode(raw) 行暂停，观察 first。
2. F8 完成第二次编码，观察 first.equals(second)；在三个 matches 断言逐个 F8，错误口令返回 false。无需进入 Argon2 库内部循环。
3. 另起 Debug 原重置测试，进入 consumeReset；看 passwords 是 mock，new-hash 是桩值。
4. 跟到 token.consume 与 deleteSessions，再回测试帧核对 PASSWORD_RESET 类型隔离和会话删除调用。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 探针 first.equals(second) | false |
| 探针 encoder.matches(raw, first) | true |
| 探针 encoder.matches("WrongPassword123!", first) | false |
| 重置测试 account.getPasswordHash() | new-hash（模拟值，不是 Argon2 实测输出） |

## D3 登录完成委托

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `AuthenticationRateLimiterTest#delegates_login_completion_to_the_same_subject_lock_transaction`

1. Debug 测试，在 loginFailed 入口看 attemptId，F8 看委托参数 false。
2. F9 到 loginSucceeded，看另一 attemptId 和 true；不要进入 mock 寻找 SQL。
3. 回测试帧执行两个 verify，记录委托方法相同、布尔值不同。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 失败输入 | failed-attempt |
| 失败委托 | completeLoginAttempt(..., false) |
| 成功输入 | successful-attempt |
| 成功委托 | completeLoginAttempt(..., true) |

## D4 账号状态转换

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `AccountAdministrationServiceTest#suspends_an_account_and_writes_a_minimal_audit_event`
- `AccountAdministrationServiceTest#suspended_accounts_can_be_reactivated_but_revoked_accounts_are_final`
- `AccountAdministrationServiceTest#refuses_to_mutate_the_super_admin_account`

1. Debug 停用方法，F7 进 account.suspend，F8 后回测试帧观察资格与活跃性，核对审计 verify。
2. Debug 恢复/终态方法；每次调用前记录当前 qualificationStatus，再运行到异常断点。
3. Debug 超管保护方法，观察拒绝发生在状态写入之前；其余三条账号测试按 README 整类基线运行。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 停用后 account.getQualificationStatus() | SUSPENDED |
| 停用后 account.isActive() | false |
| 撤销后恢复异常 | ACCOUNT_REVOKED_FINAL |
| 超管变更异常 | ACCOUNT_SUPER_ADMIN_IMMUTABLE |

## D5 激活令牌签发与消费

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ActivationServiceTest#stores_only_a_hash_and_allows_the_token_to_be_consumed_once`
- `ActivationServiceTest#refuses_to_activate_a_revoked_account`
- `ActivationServiceTest#issuing_or_consuming_activation_does_not_invalidate_password_reset_tokens`

1. Debug 第一方法，issue 后在 String raw 后续断言处停住，看 saved.getValue() 的 hash/有效期。
2. F7 进入 consume，经过加锁与令牌可用性检查；观察 account 密码和状态，再观察 token 消费。
3. 第二次 consume 会抛异常，由 assertThatThrownBy 接住；另跑撤销账号与 PASSWORD_RESET 类型用例，记录拒绝点。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 测试帧 token.getTokenHash().equals(raw) | false；hash 为 64 字符 |
| 第一次消费后 account.isActive() | true |
| 第二次消费 | 抛激活凭据无效/已使用异常 |
| PASSWORD_RESET 被激活入口消费 | ACTIVATION_TOKEN_INVALID |

## D6 生产配置拒绝路径

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ProductionConfigurationValidatorTest#acceptsExplicitSecureProductionConfiguration`
- `ProductionConfigurationValidatorTest#rejectsLocalPublicUrlAndPlaceholderDatabasePassword`
- `ProductionConfigurationValidatorTest#rejectsRootDatabaseAccountAndTrustAllProxyRange`
- `ProductionConfigurationValidatorTest#rejectsEnabledPrivateContactOrCommunityGateWithoutProductionSecrets`
- `ProductionConfigurationValidatorTest#acceptsCommunityGateWithIndependentProductionKeyRing`

1. 先 Debug 合法配置方法，在 run 入口核对 cookieSecure=true，F9 确认返回。
2. 逐个 Debug 三个 rejects 方法；加 IllegalStateException caught 断点，记录首先失败的配置名。
3. 注意根账号与信任所有网络是两次不同调用，F9 才能观察后一个。
4. 最后跑独立密钥环成功例。cookieSecure=false 不在这五条原用例中，不能把存在该 if 当作已验证。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 合法配置 run | 无异常 |
| localhost 公网地址 | 异常包含 VOIDHAVEN_PUBLIC_BASE_URL |
| 数据库 root / 全信任代理 | 分别拒绝 least-privilege / must not trust every network |
| 缺私有值密钥或门禁 secret | 在对应配置项拒绝 |

## D7 首管引导与本地初始化

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `BootstrapAdminCommandTest#requiresExplicitBootstrapAdminProfile`
- `BootstrapAdminCommandTest#rejectsUnsupportedEnvironmentBeforeOpeningATransaction`
- `BootstrapAdminCommandTest#refusesToBootstrapWhenAnAccountAlreadyExists`
- `BootstrapAdminCommandTest#testServerBootstrapPrintsTheOneTimeActivationUrlAndAuditsWithASyntheticRequestId`
- `LocalDataInitializerTest#assigns_module_key_before_the_first_content_insert`

1. Debug profile 注解测试，只在测试断言处暂停：检查 bootstrap-admin 注解。
2. Debug 不支持环境与非空表方法，观察在事务前/激活前拒绝；“空库”在此具体指 accounts.count()==0。
3. Debug 成功例，记录 count=0、合成激活令牌和 audit 的 requestId 长度；不要访问输出中的地址。
4. 单独 Debug LocalDataInitializerTest，在它的 saveAndFlush 替身回调和最后断言暂停，观察保存前已有 moduleKey；它不证明只在 local 启动。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 注解 profile.value() | 仅 bootstrap-admin |
| 非空 accounts.count() | 1，拒绝且不签发激活 |
| 成功例审计 requestId | 非空 26 字符 |
| 本地初始化首次保存 | moduleKey 已赋值 |
