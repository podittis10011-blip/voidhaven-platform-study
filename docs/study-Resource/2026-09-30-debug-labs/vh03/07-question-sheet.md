# VH03 闭卷题

先根据观察记录回答，再展开参考。每站都要能说明哪一部分是替身。

## D1 资格复查与会话失效

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

过滤器帧 authentication.getName() → partner-one；测试帧 response.getStatus() → 401；测试帧 session.isInvalid() → true；SecurityContextHolder.getContext().getAuthentication() → null。

边界：手工 doFilter；不证明 Spring 注册顺序、Cookie 传输及下一个真实 HTTP 请求。

</details>

## D2 真实 Argon2 与密码重置边界

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

探针 first.equals(second) → false；探针 encoder.matches(raw, first) → true；探针 encoder.matches("WrongPassword123!", first) → false；重置测试 account.getPasswordHash() → new-hash（模拟值，不是 Argon2 实测输出）。

边界：真实编码器行为已可测试；直接 new SecurityConfig 不证明容器装配，密码策略另在服务/请求校验层。

</details>

## D3 登录完成委托

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

失败输入 → failed-attempt；失败委托 → completeLoginAttempt(..., false)；成功输入 → successful-attempt；成功委托 → completeLoginAttempt(..., true)。

边界：只能证明委托协议，不能据此写“已证明并发计数不丢”；真实事务和锁在 VH04 D5。

</details>

## D4 账号状态转换

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

停用后 account.getQualificationStatus() → SUSPENDED；停用后 account.isActive() → false；撤销后恢复异常 → ACCOUNT_REVOKED_FINAL；超管变更异常 → ACCOUNT_SUPER_ADMIN_IMMUTABLE。

边界：管理员操作的服务规则；HTTP 角色授权和会话失效全链需另验。

</details>

## D5 激活令牌签发与消费

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

测试帧 token.getTokenHash().equals(raw) → false；hash 为 64 字符；第一次消费后 account.isActive() → true；第二次消费 → 抛激活凭据无效/已使用异常；PASSWORD_RESET 被激活入口消费 → ACTIVATION_TOKEN_INVALID。

边界：mock 查询不验证哈希 WHERE 是否真实匹配；没有过期夹具的分支不写成已测。

</details>

## D6 生产配置拒绝路径

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

合法配置 run → 无异常；localhost 公网地址 → 异常包含 VOIDHAVEN_PUBLIC_BASE_URL；数据库 root / 全信任代理 → 分别拒绝 least-privilege / must not trust every network；缺私有值密钥或门禁 secret → 在对应配置项拒绝。

边界：直接 run 证明校验逻辑；自动启动执行与 profile 装配是源码/真实栈项目。

</details>

## D7 首管引导与本地初始化

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

注解 profile.value() → 仅 bootstrap-admin；非空 accounts.count() → 1，拒绝且不签发激活；成功例审计 requestId → 非空 26 字符；本地初始化首次保存 → moduleKey 已赋值。

边界：首管支持 production/test-server 且需显式 bootstrap-admin，不是“只在 local”；本次仅跑 mock，不执行真实初始化命令。

</details>
