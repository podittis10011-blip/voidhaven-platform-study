# VH-03 代码阅读路线（六站）

阅读版本：工作树 HEAD `be219ac`。行号以 2026-09-18 的工作树为准；改动后可能漂移，用方法名兜底。
VH-03 涉及的文件**全部已在 HEAD 中且工作树未修改**，所以这一份行号对 18080 与 18082 两个栈都成立。

## 0. 一句话链路

```text
写请求（Cookie + X-CSRF-TOKEN）
  → RequestIdFilter                       （发 requestId，写响应头）
  → SecurityContextHolderFilter (700)     （从会话惰性恢复 SecurityContext）
  → AccountEligibilityFilter    (701)     （复查账号是否仍 ACTIVE，否则 401 并作废会话）
  → CsrfFilter                  (1100)    （状态改变方法比对令牌，失败 403）
  → AnonymousAuthenticationFilter(3700)   （无关身份）
  → ExceptionTranslationFilter  (4000)    （403/401 的分流与入口点）
  → AuthorizationFilter         (4200)    （URL 规则：admin/partner/media/其他）
  → Controller 的 @PreAuthorize           （方法级：SUPER_ADMIN 之类的更细规则）
  → Service / Repository / MySQL
```

登录是唯一绕过第 4 步的写请求：`/api/v1/auth/session` 显式 CSRF 豁免。

## 第 1 站：HTTP 入口

文件：`apps/api/src/main/java/cn/voidhaven/api/web/AuthController.java`（122 行）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 23～24 | `@RestController` + `@RequestMapping("/api/v1/auth")` | 会话相关端点全在这个前缀下 |
| 42 | `record LoginRequest(@NotBlank @Size(max=100) loginId, @NotBlank @Size(max=128) password)` | 只做长度与空值校验，不做业务判断 |
| 51～53 | `POST /session` → `login()` | 第一件事是限流登记，不是认证 |
| 54 | `HttpSession session = request.getSession(true)` | 认证前先拿会话，为后面的换 ID 做准备 |
| 56～57 | `authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(...))` | 真正的密码校验交给 Provider |
| 58 | `request.changeSessionId()` | **会话固定攻击防护**：登录成功后换会话 ID |
| 59～64 | 新建空 `SecurityContext` → 放认证 → 写回 `SPRING_SECURITY_CONTEXT` 会话属性 | 手动登录必须自己落上下文，否则下一个请求认不出你 |
| 65～66 | `loginSucceeded(attemptId)` + 返回 `Identity` | 成功时清掉该登录名的失败计数 |
| 67～72 | 失败分支 | 清上下文 → `session.invalidate()` → 统一 401 `AUTHENTICATION_FAILED`，不区分「账号不存在」和「密码错误」 |
| 75～81 | `DELETE /session` → `logout()`，`204` | 只注销当前会话，天然幂等 |
| 83～86 | `GET /me` | 不在 permitAll 列表里，未登录轮不到它就已经 401 |
| 88～91 | `GET /csrf` → 返回 `{headerName, token}` | 前端写请求前先来这里取令牌 |
| 93～98 | `POST /activation/consume` | 一次性激活凭据消费 |
| 100～105 | `POST /password/change`，`204` | 需要登录 + CSRF，改完清上下文 |
| 107～111 | `POST /password-reset/consume` | 管理员签发的重置凭据 |
| 113～117 | `identity()` | 权限名统一加 `ROLE_` 前缀，响应里再剥掉、排序 |

读完自答：

1. `login()` 里为什么先建会话再认证？如果认证失败不 `invalidate()`，会留下什么？
2. 失败分支为什么统一成 `AUTHENTICATION_FAILED`，而不是「用户不存在」/「密码错误」？
3. `GET /csrf` 为什么可以在未登录时访问？它泄露了什么、没泄露什么？
4. `logout()` 没有做 `SecurityContextHolder` 之外的清理，为什么「其他设备」不受影响？

## 第 2 站：过滤链与授权规则（最重要的一站）

文件：`apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java`（35 行，几乎全是链式配置）

| 行 | 内容 | 关键点 |
|---:|---|---|
| 21 | `@Configuration @EnableWebSecurity @EnableMethodSecurity` | 同时打开 URL 规则与方法级注解 |
| 23 | `@Value("${voidhaven.cookie-secure:false}")` | 本地 false、生产 true，直接决定 cookie 的 `Secure` |
| 24 | `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` | 口令摘要算法与参数族 |
| 25 | `UserDetailsService` lambda | 查账号 → **`filter(Account::isActive)`** → 映射 `ROLE_<code>`；查不到或未激活都抛 `UsernameNotFoundException` |
| 26 | `DaoAuthenticationProvider` | 把上面的 `UserDetailsService` 与编码器接起来 |
| 28 | `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `sameSite("Strict").secure(cookieSecure)` | 令牌存 cookie，JS 可读；本地不加 `Secure` |
| 29 | `.csrf(... ignoringRequestMatchers(...))` | **豁免是按路径匹配的，不看方法**：`/public/**`、`/auth/session`、`/auth/activation/consume`、`/auth/password-reset/consume` |
| 30 | `.authorizeHttpRequests(...)` | 三条规则 + 兜底，见下表 |
| 31 | `SessionCreationPolicy.IF_REQUIRED`、`HttpStatusEntryPoint(401)`、禁用 httpBasic | 未登录用 401 而不是重定向到登录页 |
| 32 | `addFilterAfter(accountEligibilityFilter, SecurityContextHolderFilter.class)` | 位置 = 701，排在 CSRF 与授权之前 |

URL 授权规则（`SecurityConfig` 第 30 行逐段拆开）：

| 匹配 | 规则 | 谁能进 |
|---|---|---|
| `/healthz`、`/actuator/health/**` | `permitAll` | 任何人 |
| `/api/v1/public/**` | `permitAll` | 任何人（同时 CSRF 豁免） |
| `/api/v1/auth/session`、`/auth/csrf`、`/auth/activation/consume`、`/auth/password-reset/consume` | `permitAll` | 任何人（`/auth/session` 同时 CSRF 豁免） |
| `/api/v1/admin/**` | `hasAnyRole("ADMIN","SUPER_ADMIN")` | 管理员类账号 |
| `/api/v1/partner/**`、`/api/v1/media/**` | `hasAnyRole("PARTNER","ADMIN","SUPER_ADMIN")` | 合作方与管理类账号 |
| 其他 | `authenticated()` | 任何已登录账号（例如 `/api/v1/auth/me`、`/auth/password/change`） |

方法级规则叠加在 URL 规则**之上**，是「更细的那一层」，例如：

| 位置 | 规则 |
|---|---|
| `AdminController` 第 130、138、178、244、327、336、345、354 行 | `@PreAuthorize("hasRole('SUPER_ADMIN')")`：账号管理、重新打开、删除等 |
| `CommunityAdminController` 第 20 行、`PasswordAdminController` 第 18 行、`ManualDeliveryAdminController` 第 20 行 | 同样 `SUPER_ADMIN` |
| `ContentVerificationAdminController` 第 20 行 | `hasAnyRole('ADMIN','SUPER_ADMIN')` |

由此产生一个必须能分清的组合：**URL 规则拒绝 = 进不了 Controller；方法规则拒绝 = 进了 Controller 才被拒**。两者的响应体形状不同（见第 3 站的实测）。

读完自答：

1. `/api/v1/admin/**` 允许 ADMIN，但 `/admin/accounts` 又要求 SUPER_ADMIN。这两层分别在什么时刻执行？哪一层更靠近业务？
2. `ignoringRequestMatchers("/api/v1/auth/session")` 是按路径还是按「路径 + 方法」匹配？这会导致哪些非预期的豁免？（提示：看 `DELETE /session` 的实测结果）
3. `AccountEligibilityFilter` 为什么插在 `SecurityContextHolderFilter` 之后，而不是链尾？如果插在 `AuthorizationFilter` 之后会发生什么？
4. 这个类带 `@Component`。Spring Boot 会把 `Filter` 类型的 Bean 自动注册成 servlet 过滤器。请自己想清楚：它会不会执行两次？为什么现在看不出问题？（验证方法写在 `03-manual-repro-runbook.md` 的 E-05）

## 第 3 站：会话从哪里来、到哪里去

相关文件与行号：

| 位置 | 内容 |
|---|---|
| `apps/api/src/main/resources/application.yml` 第 20～27 行 | `spring.session.store-type: jdbc`、`timeout: 8h`、`initialize-schema: never` |
| `apps/api/src/main/resources/application.yml` 第 30～37 行 | `server.servlet.session.cookie`：名字 `VOIDHAVEN_SESSION`、`http-only: true`、`secure: ${VOIDHAVEN_COOKIE_SECURE:false}`、`same-site: strict` |
| `db/migration/V10__spring_session_jdbc.sql` | `SPRING_SESSION`（主键 `PRIMARY_ID`，索引 `SESSION_ID` / `EXPIRY_TIME` / `PRINCIPAL_NAME`）与 `SPRING_SESSION_ATTRIBUTES`（外键级联删除） |
| `AuthController` 第 58 行 | `request.changeSessionId()`，登录后换 ID |
| `AuthController` 第 70、78～79 行 | 失败与注销都 `invalidate()` |
| `PasswordService` 第 81 行 | `delete from SPRING_SESSION where PRINCIPAL_NAME = ?`：改密/重置后按登录名清空**所有设备**的会话 |
| `AccountEligibilityFilter` 第 31～33 行 | 资格失效时作废当前会话 |

为什么用 JDBC 存会话（`application.yml` 注释里的原话是「重启/重部署或多副本共享登录态」）：会话不再是某个 JVM 的内存状态，而是数据库里的一行。带来三个可观察结果：

1. 重启 API 容器不会把所有人踢下线；
2. 改密码能靠一条 SQL 精确吊销某人的全部会话；
3. 会话表需要一个清理机制——**不是** `RetentionService`（它清的是限流、幂等、凭据等业务表），而是 Spring Session JDBC 自带的定时清理（`spring.session.jdbc.cleanup-cron`，默认 `0 * * * * *`）。

本轮实测到的一个「反直觉但合理」的现象：**未登录访问受保护接口，401 响应里也带 `VOIDHAVEN_SESSION`**。原因是异常转换阶段会先把请求存进会话（便于登录后回到原请求），于是顺带创建了服务端会话：

```text
GET /api/v1/auth/me   （不带 cookie）
→ 401，且 Set-Cookie: VOIDHAVEN_SESSION=...; Path=/; HttpOnly; SameSite=Strict
```

读完自答：

1. 换 ID 之后，旧 ID 还能继续用吗？本轮实测给出的证据是什么？（提示：`03-manual-repro-runbook.md` 的 E-01 第三步）
2. 8 小时超时是从「创建」算还是从「最后访问」算？改这条配置会影响哪些人？
3. 未登录请求会创建会话。这在什么场景下会被滥用？生产上你会怎么收口？
4. 改密时要一次性吊销「这个人所有设备」的会话，`SPRING_SESSION` 里哪个字段让这件事变成一条 SQL？如果未来允许多人共用登录名会怎样？

## 第 4 站：CSRF 是怎么被裁决的

配置只有一行（`SecurityConfig` 第 28～29 行），但行为由 Spring Security 7.1.1 的三个默认值决定，本轮用 `javap` 在 jar 里核对过：

| 组件 | 默认值 | 后果 |
|---|---|---|
| `CookieCsrfTokenRepository`（`withHttpOnlyFalse()`） | cookie 名 `XSRF-TOKEN`、请求头名 `X-XSRF-TOKEN`、参数名 `_csrf`；cookie 原值 = 36 字符 UUID | JS 能读 cookie，但**不应该**用它当请求头值 |
| `CsrfFilter` 的 `requestHandler` | 配置器没显式设置，落到 `CsrfFilter` 的默认 `XorCsrfTokenRequestAttributeHandler` | 请求属性里暴露的是**掩码令牌**（BREACH 防护）；`resolveCsrfTokenValue` 会把请求头里的值异或还原后再比较 |
| `CsrfFilter.DEFAULT_CSRF_MATCHER` | 只放过 `GET/HEAD/TRACE/OPTIONS` | `POST/PATCH/PUT/DELETE` 默认都要令牌 |

于是产生一条**必须自己验证过**的规则：前端不能拿 `XSRF-TOKEN` cookie 的值直接当请求头发出去，必须用 `GET /api/v1/auth/csrf` 返回的 token。本轮实测对照（18082，已登录）：

| 请求 | 结果 |
|---|---|
| `POST /auth/password/change`，不带 CSRF 头 | `403`（Spring Boot 默认错误体：`{"timestamp","status":403,"error":"Forbidden","path"}`） |
| 同上，把 **cookie 裸值** 放进 `X-XSRF-TOKEN` | `403` |
| 同上，把 `/auth/csrf` 返回的**掩码 token** 放进 `X-XSRF-TOKEN` | `400 CURRENT_PASSWORD_INVALID`（说明 CSRF 已通过，进入业务校验） |

前端的对应实现是 `apps/web/src/lib/api.ts` 第 443～445 行的 `protectedRequest`：先 `request('/auth/csrf')`，再把 `[csrf.headerName]: csrf.token` 合进写请求；所有请求都带 `credentials: 'include'`（第 409 行）。

豁免清单里有两个容易讲错的点：

1. **登录豁免是按路径的**，所以同一个路径上的注销也一起豁免（实测：`DELETE /auth/session` 不带 CSRF 也返回 204）。
2. 不能用登录接口去证明「CSRF 拒绝生效」——它本来就不检查。要在**已登录**状态下用 `/auth/password/change` 这类受保护写接口做对照（见第 3 站实测表）。

读完自答：

1. `withHttpOnlyFalse()` 意味着 XSS 可以读走令牌。那 CSRF 防护还成立吗？它防的到底是哪种攻击？
2. `SameSite=Strict` 在这里起了什么作用？它和掩码令牌是不是重复的？
3. 登录、注销、公开投稿都被豁免，各有什么风险、各用什么别的机制兜底（提示：`Idempotency-Key`、限流、`SameSite`，以及 VH-06）？

## 第 5 站：认证、口令与登录限流

| 位置 | 内容 | 要点 |
|---|---|---|
| `SecurityConfig` 第 25 行 | `UserDetailsService` | 账号查询 + `isActive()` 过滤 + 角色映射，三元一处收敛 |
| `SecurityConfig` 第 26 行 | `DaoAuthenticationProvider` | 认证委托方；密码比对使用 `PasswordEncoder.matches` |
| `AuthController` 第 53 行 | `rateLimiter.beginLogin(loginId, networkSubject.resolve(request))` | **先限流，再认证**：无论对错都要占用一次尝试 |
| `AuthenticationRateLimiter` 第 30～35 行 | `beginLogin` | 同时占两个额度：`LOGIN_NETWORK`（默认 20 次/15 分钟/IP）与 `LOGIN_ID`（默认 5 次/15 分钟/登录名） |
| `AuthenticationRateLimiter` 第 46～57 行 | `hash(...)` | HMAC-SHA256 后再入库：数据库里看不到明文登录名与 IP |
| `AuthenticationRateLimiter` 第 37～44 行 | `loginFailed` / `loginSucceeded` | 成功会把该登录名的 `FAILURE` 行删掉 |
| `RateLimitLockService` 第 64～79 行 | `reserveAuth` | `insert ... on duplicate key update` + `select ... for update` 抢同一把行级锁，再 `count(*)` 判断额度；这条路径用 `REQUIRES_NEW` 独立事务，业务回滚不影响计数 |
| `RateLimitLockService` 第 80～97 行 | `completeLoginAttempt` | 只更新 `LOGIN_ID` 作用域的 `ATTEMPT` 行 |
| `ApiExceptionHandler` 第 76～84 行 | 429 响应 | `code=AUTH_RATE_LIMITED`，`Retry-After: 900` |
| `RetentionService` 第 43 行 | 过期凭据清理 | `delete from auth_rate_limit_events where expires_at < current_timestamp(6)` |
| `ActivationService` 第 41～75 行 | 激活 | 库里只存 SHA-256 摘要；单次使用；消费时按类型作废其它未用凭据 |
| `PasswordService` 第 38～48、68～86 行 | 改密/重置 | 改密要验旧口令、新口令长度 12～128、且不得与旧口令相同；成功即删会话 |

两处必须讲清楚的语义细节：

1. `reserveAuth` 的 `count(*)` 只统计 `ATTEMPT` 与 `FAILURE`，`SUCCESS` 不计入；而 `completeLoginAttempt` 只处理 `LOGIN_ID` 作用域——所以 **网络额度只会被消耗，不会因为成功登录而回退**，`LOGIN_ID` 额度才会被成功清空。这不是 bug，但必须知道它对共用出口 IP 的影响（因此 `local` profile 把网络额度抬到 200）。
2. 429 的文案与 `Retry-After` 都写死为「15 分钟 / 900 秒」，而窗口长度是可配置项 `voidhaven.auth-rate-limit-window-minutes`。默认值下自洽，改配置就会说谎——这是一个很好的追问切入口。

读完自答：

1. 为什么限流要在认证之前？如果放在认证之后，攻击者能拿到什么额外信息、造成什么额外开销？
2. `subject_hash` 用 HMAC 而不是明文或裸 SHA-256，分别防住了什么？
3. 「5 次失败」是 5 次连续失败，还是 15 分钟内的累计失败？成功一次之后呢？
4. 限流表放在 MySQL 而不是内存，换来了什么、付出了什么？

## 第 6 站：资格复查与账号生命周期

文件：`apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java`（47 行）

| 行 | 内容 | 要点 |
|---:|---|---|
| 19～21 | 类注释 + `@Component` + `extends OncePerRequestFilter` | 「每个已认证请求都复查资格」写在注释里 |
| 28～30 | 取认证 → 判 `isAuthenticated` 且非匿名 → `findByLoginId(...).filter(isActive).isEmpty()` | 命中才拦；查不到账号也拦 |
| 31～33 | `session.invalidate()` + 清上下文 | 资格失效等于立刻踢下线，不用等 8 小时超时 |
| 34～38 | 手写 401 JSON：`ACCOUNT_INACTIVE` + `Cache-Control: no-store` | 不走 `ApiExceptionHandler`，所以响应体里只有 `code/message/requestId` |
| 39～40 | 否则放行 | 放行前会多一次 `accounts.findByLoginId` 查询 |

为什么需要它：`UserDetailsService` 只在**登录那一刻**检查 `isActive()`，而会话可以活 8 小时。有了这个过滤器，「停用/撤销」对已登录会话立即生效（登录态、资格态、角色态三者分离的落地方式）。

账号资格状态机（`domain/Account.java` + `service/AccountAdministrationService.java`）：

| 状态 | 含义 | 能否登录 | 关键方法 |
|---|---|---|---|
| `PENDING` | 管理员已建号，未激活 | 否 | `activateWithPassword`（只允许从 PENDING 出发） |
| `ACTIVE` | 正常 | 是 | — |
| `SUSPENDED` | 暂停，可恢复 | 否 | `suspend` / `reactivate` |
| `REVOKED` | 永久撤销，终态 | 否 | `revoke`；再次变更抛 `ACCOUNT_REVOKED_FINAL` |

配套的业务规则（`AccountAdministrationService` 第 29～30、46～69、133、149、159～163 行等）：

- 不允许停用/撤销自己（`自身账号`）；
- 不允许把账号改成 `ACTIVE`（只能用 `reactivate`，保证从 `SUSPENDED` 出发）；
- 超级管理员账号不可停用/恢复/撤销（`ACCOUNT_SUPER_ADMIN_IMMUTABLE`）；
- 每次状态变化都写审计记录；
- 并发保护是「先 `findByIdForShare`/`findByIdForUpdate` 锁行，再由 `@Version` 兜底」，冲突时返回 `ACCOUNT_VERSION_CONFLICT`（`ApiExceptionHandler` 第 47～54 行）。

读完自答：

1. 资格复查为什么放在过滤器而不是每个 Controller？放在 Service 层会漏掉什么？
2. 每个已认证请求多一次 `findByLoginId` 的代价，换成缓存后要解决什么问题？
3. 「已登录 + 角色仍在 + 资格被撤销」这三个状态可以组合出几种情形？各自在代码的哪里被拦住？

## 收束：把这六个站压成三句话

- **会话**：登录成功换 ID 并写入 `SPRING_SESSION`；失败即作废；改密按登录名清空全部会话；8 小时滑动超时、JDBC 存储。
- **CSRF**：写请求要 `/auth/csrf` 返回的掩码令牌；豁免按路径（含注销）；裸 cookie 值会被拒。
- **权限**：URL 规则管粗粒度，`@PreAuthorize` 管细粒度，`AccountEligibilityFilter` 管「资格」这一维，与角色、登录态三者独立。
