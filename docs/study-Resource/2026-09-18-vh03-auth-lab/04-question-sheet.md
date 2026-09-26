# VH-03 题单（两层：费曼理解输出 + 面试追问链）

## 使用说明

- 先闭卷作答，写完再展开文末参考答案对照。参考答案只用于校准，不要求逐字一致。
- 允许查阅：`01-code-reading-guide.md`、源码、官方文档。查过资料后再作答，本轮记为 `ASSISTED`。
- 想作为候选证据用：第一层手写完成后录一次 3 分钟口述，第二层按 30 秒版录一次。
- 题目来源已逐题标注：`项目代码` / `项目风险` / `公开面经常见考点` / `官方能力边界`。

## 第一层：费曼理解输出

| # | 题目 | 答题范围与线索 | 建议篇幅 | 来源 |
|---:|---|---|---|---|
| F1 | 把「管理员登录成功」这条路径从 HTTP 讲到 MySQL，标出每个组件各自负责什么 | 只讲成功路径；提示：`AuthController.login` → `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService` → `AuthenticationRateLimiter` → `changeSessionId` → `SPRING_SESSION` | 一张图或 8～10 句 | 项目代码 |
| F2 | 为什么登录成功后必须更换会话 ID？给出一个不更换就会被打的场景 | 场景要写清「攻击者在什么时刻拿到了什么」；提示：`AuthController` 第 58 行、会话固定攻击 | 4～5 句 | 公开面经常见考点 + 项目代码 |
| F3 | 会话存 MySQL 换来了哪三个能力、带来哪一个新问题？改密码后其他设备为什么立刻掉线？ | 提示：`spring.session.store-type: jdbc`、`SPRING_SESSION.PRINCIPAL_NAME`、`PasswordService` 第 81 行、Spring Session 自带清理任务 | 5～6 句 + 1 条 SQL | 项目代码 |
| F4 | CSRF 的「cookie 原值」和「响应体里的 token」为什么不是同一个字符串？前端该用哪一个？裸用 cookie 值会怎样？ | 提示：`CookieCsrfTokenRepository`、`XorCsrfTokenRequestAttributeHandler`、`/auth/csrf` 实测长度 96 vs 36 | 4 句 + 一张 3 行对照表 | 项目代码 + 实测 |
| F5 | 列出四个 CSRF 豁免路径，分别说明风险与兜底机制 | 提示：`SecurityConfig` 第 29 行；豁免是**按路径**匹配的，注销也被一起豁免 | 一张 4 行表 | 项目风险 |
| F6 | URL 规则与 `@PreAuthorize` 两层授权各管什么？「PARTNER 访问 `/admin/...`」和「ADMIN 访问仅超管的 `/admin/accounts`」分别在哪里被拒、返回什么形状的响应体？ | 提示：前者由 `AuthorizationFilter` + 默认 `AccessDeniedHandler` 处理；后者由 `ApiExceptionHandler` 的 `AccessDeniedException` 分支处理 | 一段说明 + 一张 2 行表 | 项目代码 |
| F7 | 为什么要在每个已认证请求上复查账号资格？没有这个过滤器会出现什么具体问题？代价是什么？ | 提示：`UserDetailsService` 只在登录时查一次；会话可以活 8 小时 | 5 句 | 项目代码 + 项目风险 |
| F8 | 登录限流有几个作用域、各多少额度、成功之后哪些计数会变？429 的响应头是什么？ | 提示：`LOGIN_NETWORK` / `LOGIN_ID`；`count(*)` 只统计 `ATTEMPT`、`FAILURE`；`completeLoginAttempt` 只处理 `LOGIN_ID` | 5 句 | 项目代码 |
| F9 | 401 / 403 / 429 分别代表哪一类失败？各举一个本项目里能复现的请求 | 提示：未登录、已登录但不够格（两种子情况）、尝试过于频繁 | 3 行表 | 官方能力边界 + 项目代码 |
| F10 | 未登录 + 缺 CSRF 的受保护写请求，为什么返回 401 而不是 403？ | 提示：过滤器顺序、`sendError` 触发的 ERROR 派发、Boot 4 默认让安全链参与 ERROR 派发、`/error` 不在 permitAll 里 | 5～6 句（可以答「我的推断 + 验证方法」） | 项目风险（进阶） |

## 第二层：面试模拟追问链

| # | 追问链 | 你要给出的关键点 | 来源 |
|---:|---|---|
| I1 | 「你们会话怎么存的？」→「服务重启会掉线吗？」→「多副本呢？」→「怎么让某个人所有设备立刻下线？」→「8 小时是不是太长？」 | JDBC 存 MySQL；重启不掉线；多副本共享同一张表（但要求不混版本滚动）；按 `PRINCIPAL_NAME` 删行；8 小时要结合滑动过期与资格复查来评估 | 项目代码 + 公开面经常见考点 |
| I2 | 「有 CSRF 防护吗？」→「SPA 怎么拿令牌？」→「为什么不能直接读 cookie？」→「登录为什么豁免？」→「注销也豁免你知道吗？」 | 写请求校验；`/auth/csrf` 拿掩码令牌；masking 之后裸值会失配；登录豁免是为了避免引导悖论，靠 `SameSite=Strict` 与 CORS 兜底；注销同路径一起被豁免，属于已知取舍 | 项目代码 + 实测 |
| I3 | 「角色怎么设计的？」→「为什么 URL 规则和方法注解两层？」→「合作方和管理员的边界在哪？」→「为什么超管不允许被停用？」 | `account_roles` 多对多 → `ROLE_*`；粗粒度守边界、细粒度守操作；`/partner/**` 与 `/admin/**` 分流，媒体接口对三方开放；超管不可变是为了避免锁死管理面 | 项目代码 |
| I4 | 「用户被停用后，已经登录的会话怎么办？」→「每请求查一次库不慢吗？」→「如果加缓存，失效窗口怎么处理？」 | `AccountEligibilityFilter` 每请求复查并作废会话；代价是一次索引查询，换来「停用立即生效」；缓存要引入 TTL 与主动失效，权衡是「慢几毫秒」对「多几十秒的越权窗口」 | 项目代码 + 项目风险 |
| I5 | 「登录接口怎么防爆破？」→「按 IP 还是按账号？」→「NAT 后面几百人共用一个出口怎么办？」→「限流表放 MySQL 有什么代价？」 | 双作用域同时限：账号 5 次/15 分钟、网络 20 次/15 分钟；`subject_hash` 是 HMAC，不落明文；NAT 场景要抬网络额度（本地 profile 已经抬到 200）；代价是每次登录多两次数据库写，且需要行锁串行化 | 项目代码 + 项目风险 |
| I6 | 「未登录访问后台返回什么？」→「权限不够呢？」→「为什么有的 403 有 JSON、有的是空？」→「未登录又缺 CSRF 为什么不是 403？」 | 401 空体（入口点直接设状态）；403 分「过滤器层」与「Controller 层」两种形状；过滤器层触发 `sendError` 后会走错误派发，匿名用户在错误派发里再次被拒，最终呈现为 401 | 官方能力边界 + 项目代码 |

## 自评标准

- 及格：F1、F2、F4、F7 能不看材料说清因与果；I1、I2 的追问都能接住。
- 良好：F6 能分清「过滤器层拒绝」和「方法注解拒绝」的响应体差异；F8 能说出「网络额度只增不减」这个非直觉细节；I5 能讲清 NAT 场景。
- 待补：任何一条只能复述结论、说不出「如果不这样会怎样」，或者把 CSRF cookie 值当成令牌用，就还没过关。

## 参考答案与解析

<details>
<summary>先自己答完 F1～F10、口述完 I1～I6，再展开</summary>

### F1

`POST /api/v1/auth/session` 带着 `loginId`、`password` 进入 `AuthController.login`。第一步不是认证，而是 `rateLimiter.beginLogin(loginId, 客户端网络)`：它给「这个登录名」和「这个网络」各占一个额度，额度用尽直接 429。第二步 `request.getSession(true)` 先要到会话对象，为后面的换 ID 做准备——这一步会创建会话，也是失败时必须作废的原因。第三步 `authenticationManager.authenticate(...)` 把校验交给 `DaoAuthenticationProvider`，它用 `UserDetailsService` 按登录名查 `accounts`（顺带过滤 `isActive()`），再用 `Argon2PasswordEncoder` 比对口令摘要。成功后 `request.changeSessionId()` 换掉会话 ID，手工构造 `SecurityContext` 写入 `SPRING_SECURITY_CONTEXT` 会话属性，`loginSucceeded` 清掉该登录名的失败计数，最后返回 `{loginId, roles}`。会话本身由 Spring Session 落到 MySQL 的 `SPRING_SESSION`（`PRINCIPAL_NAME` 是登录名）与 `SPRING_SESSION_ATTRIBUTES` 两张表，响应里只带一个 `HttpOnly` 的 `VOIDHAVEN_SESSION` cookie。

### F2

不换 ID 时，攻击者可以先让受害者带着一个**已知**的会话 ID 登录：攻击者在自己的浏览器里访问站点、拿到一个匿名会话 ID（例如通过诱导受害者点击一个带 `JSESSIONID`/会话参数的链接，或利用同域下的其他写入点），受害者用这个 ID 完成登录后，服务端就把「登录态」绑在了攻击者已知的那个 ID 上，攻击者随后直接拿它就能冒充受害者。这就是会话固定攻击（Session Fixation）。`request.changeSessionId()` 在认证成功后换一个攻击者不知道的新 ID，让「认证前已知的 ID」与「认证后的会话」脱钩。本轮实测：登录前后 cookie 值不同，用旧 ID 重放 `/auth/me` 得到 401。

### F3

三个能力：**① 重启不丢登录态**（会话是数据库的一行，不是某个 JVM 的堆内存）；**② 多副本共享登录态**（多个 API 实例读同一张表）；**③ 精确吊销**——`PasswordService` 第 81 行直接 `delete from SPRING_SESSION where PRINCIPAL_NAME = ?`，一个人改密码/被重置密码，他在所有设备上的会话一起作废。

一个新问题：会话行会堆积，必须有清理机制。这里**不是**项目的 `RetentionService`（它清理限流事件、幂等记录、激活凭据等业务表），而是 Spring Session JDBC 自带的定时清理（`spring.session.jdbc.cleanup-cron`，默认 `0 * * * * *`）。另外 `V10` 迁移把 `SPRING_SESSION_ATTRIBUTES` 设了 `ON DELETE CASCADE`，删主行即连带属性。

### F4

`CookieCsrfTokenRepository` 把**原始令牌**写进 `XSRF-TOKEN` cookie；而 Spring Security 7 默认的 `XorCsrfTokenRequestAttributeHandler` 在把令牌暴露成请求属性时会做一次异或掩码（防 BREACH 之类的压缩侧信道），`/auth/csrf` 返回的就是这个掩码值。校验时 `resolveCsrfTokenValue` 会把请求头里的值**异或还原**再和原始令牌比对，所以拿裸 cookie 值去比必然失配。

| 取值方式 | 本轮实测值 | 作为 `X-XSRF-TOKEN` 发出去的结果 |
|---|---|---|
| `XSRF-TOKEN` cookie | 36 字符 UUID | `403` |
| `GET /auth/csrf` 的 `data.token` | 96 字符掩码值 | 通过（继续走到业务校验，`400 CURRENT_PASSWORD_INVALID`） |
| 完全不带 | — | `403` |

前端该用第二个：`apps/web/src/lib/api.ts` 第 443～445 行的 `protectedRequest` 就是先取再发。

### F5

| 豁免路径 | 为什么需要 | 风险 | 兜底 |
|---|---|---|---|
| `/api/v1/public/**` | 公开访客没有会话，投稿等公开写入要能工作 | 跨站可发起公开投稿 | 公开写入另有 `Idempotency-Key` 与按网络限流（VH-06） |
| `/api/v1/auth/session` | 登录时还没有令牌，否则形成「要先有令牌才能拿令牌」的悖论 | 登录 CSRF；**同一个路径上的 `DELETE`（注销）也被一起豁免** | `SameSite=Strict` + 未配置跨域 CORS；注销失败只影响自己 |
| `/api/v1/auth/activation/consume` | 激活是一次性凭据、且发生在 `PENDING` 状态 | 抢先用别人的激活链接 | 一次性摘要凭据 + 24 小时过期 + 按网络限流 |
| `/api/v1/auth/password-reset/consume` | 同上，用户此时通常未登录 | 同上 | 一次性摘要凭据 + 30 分钟过期 + 每次签发作废旧凭据 |

### F6

两层的关系是「先粗后细」：`AuthorizationFilter` 的 URL 规则在**过滤器链**里执行，决定**能不能进 Controller**；`@PreAuthorize` 在**方法调用前**由 AOP 执行，决定**这个操作能不能做**。

| 情形 | 在哪里被拒 | 实测/推断的状态码与响应体 |
|---|---|---|
| PARTNER 访问 `GET /admin/content-items` | `AuthorizationFilter`（URL 规则 `hasAnyRole("ADMIN","SUPER_ADMIN")`） | 实测 `403` + Boot 默认错误体 `{"timestamp","status":403,"error":"Forbidden","path":...}` |
| ADMIN（无 SUPER_ADMIN）访问 `GET /admin/accounts` | Controller 方法上的 `@PreAuthorize("hasRole('SUPER_ADMIN')")` | 代码推断 `403` + 项目 `ApiError` 形状 `{"code":"ACCESS_DENIED","message":"当前账号无权执行该操作。","requestId":...,"fieldErrors":{},"retryable":false}`（`ApiExceptionHandler` 的 `AccessDeniedException` 分支）。本地栈没有 ADMIN-only 账号，这条**未实测**；要验证可以用超管建一个只授 `ADMIN` 的账号再登录对照 |

### F7

`UserDetailsService` 只在**登录那一刻**查库并过滤 `isActive()`，而会话可以活 8 小时。这中间管理员把某人的资格改成 `SUSPENDED` 或 `REVOKED`，如果没有复查，对方还能继续操作最多 8 小时。`AccountEligibilityFilter` 在每个已认证请求上重新查一次账号，一旦不再 `ACTIVE` 就作废当前会话、清上下文、直接返回 401 `ACCOUNT_INACTIVE`，把「停用立即生效」变成事实。代价是每个已认证请求多一次 `findByLoginId` 索引查询；好处是把「登录态」「角色态」「资格态」三个维度真正解耦——角色还在、会话还有效，但资格没了，就该被拦住。

### F8

两个作用域同时限量：`LOGIN_NETWORK`（默认 20 次/15 分钟/网络）与 `LOGIN_ID`（默认 5 次/15 分钟/登录名）。计数 SQL 只统计 `outcome in ('ATTEMPT','FAILURE')`，`SUCCESS` 不计入；`reserveAuth` 用「唯一键 + `select ... for update`」把并发串行化。

成功之后：`completeLoginAttempt(..., true)` 会把该登录名的 `ATTEMPT` 改成 `SUCCESS`，并**删除同一登录名的所有 `FAILURE` 行**——所以「4 次失败 + 1 次成功」等于清零。但它只处理 `LOGIN_ID` 作用域，网络作用域的行永远是 `ATTEMPT`，只能等 15 分钟过期。也就是说网络额度**只消耗不归还**，这也是共用出口 IP（NAT/代理）会被误伤的原因，`local` profile 因此把网络额度抬到 200。

限额用尽时返回 `429` + `{"code":"AUTH_RATE_LIMITED",...}` + `Retry-After: 900`，且这条判断在认证之前，所以触发限流后即使口令正确也登不进去。

### F9

| 状态码 | 语义 | 本项目可复现请求 |
|---|---|---|
| `401` | 没有有效身份（或身份已失效） | `GET /api/v1/auth/me` 不带 cookie；被停用账号的旧会话 |
| `403` | 身份有效但不够格 | PARTNER 访问 `/api/v1/admin/content-items`；已登录但缺 CSRF 的写请求 |
| `429` | 次数超限，稍后重试 | 同一登录名 15 分钟内第 6 次错误登录 |
| （附加）`400` | 请求本身有问题 | 带合法 CSRF 但旧口令错误的 `POST /auth/password/change` |

### F10

先说事实：未登录 + 不带 CSRF 的受保护写请求得到的是 `401` **空体**；已登录的同类请求才是 `403`。

解释（机制推断）：`CsrfFilter` 在过滤器顺序里是 1100，`AuthorizationFilter` 是 4200，所以 CSRF 先判。它拒绝时由默认的 `AccessDeniedHandlerImpl` 触发 `sendError(403)`，容器随即将请求转发到 `/error`，形成 ERROR 派发。Spring Boot 4.1.1 默认让安全过滤链参与 ERROR 派发，而 `AuthorizationFilter` 默认也过滤 ERROR 派发（`filterErrorDispatch = true`）；`/error` 不在 `SecurityConfig` 的 permitAll 列表里，于是落到 `anyRequest().authenticated()`。匿名用户在 ERROR 派发里依然匿名，被再次拒绝，这次由 `HttpStatusEntryPoint` 直接把状态设成 401 并且不写响应体，最终暴露给客户端的就是「401 空体」。已登录用户在 ERROR 派发里是已认证的，`/error` 放行，所以拿到的是 403 的 Boot 默认错误体。

要把它变成实测证据，二选一：打开 `logging.level.org.springframework.security=DEBUG` 看究竟谁先拒绝；或临时把 `/error` 加进 permitAll，若未登录的缺 CSRF 请求变成 403，就验证了上述链路。

</details>
