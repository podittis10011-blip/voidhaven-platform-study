# 04 核心链路 C：Session、CSRF、授权与幂等

时间盒 20–25 分钟。Security 精讲整文件思路；幂等只记口诀和独立事务。对照 VH-03、VH-04。

## 本段要打开的文件

| 角色 | 路径 |
|---|---|
| 安全配置 | `apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java` |
| 登录 | `.../web/AuthController.java` |
| 停用复查 | `.../web/AccountEligibilityFilter.java` |
| 幂等 | `.../service/IdempotencyService.java`、`IdempotencyReservationService.java` |
| 公开写限流 | `.../web/PublicWriteRateLimitInterceptor.java` |
| 前端唯一入口 | `apps/web/src/lib/api.ts` |

## 1. `SecurityConfig` 在干什么

一个 `@Bean SecurityFilterChain` 里做完四件事：密码器、用户查找、CSRF、URL 授权。

**密码：** `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`。面试对比 BCrypt：Argon2 可调内存，抗 GPU 彩虹表更狠；代价是 CPU/内存更高，本项目管理员/合作方登录量很小，换得来。

**UserDetailsService：** 按 `loginId` 查 `accounts`，必须 `isActive()`，角色映射成 `ROLE_ADMIN` 这种 `SimpleGrantedAuthority`。查不到抛 `UsernameNotFoundException`，对外统一 401，不暴露「用户是否存在」。

**Session：** `SessionCreationPolicy.IF_REQUIRED`。存在 MySQL（`application.yml` + V10）。Cookie `VOIDHAVEN_SESSION`，HttpOnly + SameSite=Strict。本地可不 Secure；production profile 强制 Secure。

**登录防固定：** `AuthController.login` 成功后 `request.changeSessionId()`。否则攻击者先种一个 session id 再诱你登录，就能劫持。

## 2. URL 规则 vs 方法安全

`@EnableMethodSecurity` + URL `authorizeHttpRequests` 是两层。

| 前缀 | 授权 | CSRF |
|---|---|---|
| `/healthz`、`/actuator/health/**` | permitAll | 默认（这些是 GET） |
| `/api/v1/public/**` | permitAll | **豁免** |
| `/api/v1/auth/session`、激活、消费重置 | permitAll | **豁免**（登录请求还没有 token） |
| `/api/v1/auth/csrf` | permitAll | GET，用于取掩码 |
| `/api/v1/admin/**` | ADMIN / SUPER_ADMIN | 需要 |
| `/api/v1/partner/**`、`/api/v1/media/**` | PARTNER / ADMIN / SUPER_ADMIN | 需要 |
| 其余（`/auth/me`、改密） | authenticated | 需要 |

更细的权限在方法上：例如创建管理员账号要求 `SUPER_ADMIN`，URL 层只要求 ADMIN。面试要说「URL 是粗粒度闸门，注解是细粒度」。

未登录打到受保护资源：`HttpStatusEntryPoint(401)`，不是跳转登录页（这是 API，不是服务端渲染）。

## 3. CSRF：cookie 原值 + 接口掩码

仓库用 `CookieCsrfTokenRepository.withHttpOnlyFalse()`（前端 JS 必须读到 cookie 名，但**不能把裸值当 header**）。

Spring Security 7 默认 XOR 处理器：

- `XSRF-TOKEN` cookie：约 36 字符 UUID（原值）
- `GET /api/v1/auth/csrf` 返回的 `token`：约 96 字符（掩码）
- 写请求 header（通常 `X-XSRF-TOKEN`）必须是**掩码**

把 cookie 原值当 header 会 403。这是 VH-03 的对照实验，面试提到会加分。

**八股：CSRF 原理。** 浏览器对同源请求自动带 Cookie，恶意站点的表单也能让浏览器带上你的登录 Cookie。额外的自定义 header 受同源策略限制，伪造站点读不到。`SameSite=Strict` 是另一层，但不能替代 CSRF token（老浏览器、部分 WebView）。

**为什么公开写豁免 CSRF：** 投稿/合作申请没有登录会话，CSRF 要防的是「已登录用户的浏览器被跨站使唤」。匿名写改用：

- `Idempotency-Key` 防双击和重放
- `PublicWriteRateLimitInterceptor` + MySQL 计数防刷
- 不在公开响应里回私人联系方式

## 4. `AccountEligibilityFilter`

挂在 `SecurityContextHolderFilter` 之后（约 701）。每个已认证请求再查一次账号是否仍 ACTIVE。停用/吊销后：

1. `session.invalidate()`
2. 清 `SecurityContext`
3. 立刻 401 `ACCOUNT_INACTIVE`

只靠登录时查一次不够：管理员吊销后，8 小时会话否则仍然能改内容。

## 5. 幂等与并发（口诀，不逐行敲）

口诀：**幂等防重复提交，乐观锁防丢失更新，限流独立事务防超发；单机所以用 MySQL 不用 Redis。**

`IdempotencyService.execute`：

1. 校验 key 非空且 ≤128。
2. 请求体 SHA-256。
3. `reservations.reserve` 在 **`REQUIRES_NEW`** 事务里插入或锁住 `idempotency_records` 行（状态 102 表示在途）。
4. 已完成且可回放 → 直接返回上次 body。
5. 同 key 不同 hash → 409 `IDEMPOTENCY_KEY_REUSED`。
6. 在途 → 409 `REQUEST_IN_PROGRESS`（可重试，但不要换 key）。
7. 业务成功后把行更新为 200 + JSON。
8. 业务失败 → `release`，允许同一 key 重试。

一次性秘密（激活链接、重置密码）走 `executeSingleDelivery`：成功后 `response_replayable=false`，再打同一 key 得到 409 `SENSITIVE_RESULT_NOT_REPLAYABLE`，避免响应里的 token 被日志/代理回放。

**为何 REQUIRES_NEW：** 预约行必须在业务事务提交前就对其它请求可见。如果和 `create()` 一个事务，并发第二发会看不到在途记录，于是插入第二条投稿。

状态码语义：

| 码 | 含义 | 客户端 |
|---|---|---|
| 409 `CONTENT_VERSION_CONFLICT` | 版本旧 | 刷新后再提交，换 If-Match |
| 409 `REQUEST_IN_PROGRESS` | 同 key 还在跑 | 稍后用**同一** key 重试 |
| 429 | 限流 | 看 `Retry-After`，retryable |

## 6. 前端 `api.ts` 四件事（3 分钟）

文件：`apps/web/src/lib/api.ts`。前端约定：**不得绕过这一层。**

1. 所有 `fetch` 带 `credentials: 'include'`，否则 Cookie 会话建了也带不回。
2. `protectedRequest`：先 `GET /auth/csrf`，把返回的掩码写入 header。
3. `jsonWrite`：自动生成 `Idempotency-Key`。
4. `revise`：`PATCH` 时带 `If-Match: String(revision)`。

公开 GET 走 `request()`，不拉 CSRF。管理写走 `write()` → `protectedRequest` + 幂等。

## 7. 八股速记

- **Session vs JWT：** 需要吊销、需要服务端超时、同域 Cookie 即可 → Session。JWT 适合纯移动端多端、无会话存储；本项目管理人数少，吊销比无状态更重要。
- **401 vs 403：** 你是谁还没成立 / 资格没了 → 401；你是谁已经成立但角色不够 → 403。
- **过滤器顺序：** RequestId → SecurityContext → 资格复查 → CSRF → 授权。资格复查必须在授权前，才能把「已登录但停用」变成 401 而不是带着旧角色往下走。
- **为什么公开写仍要防重放：** 没登录不等于没成本。双击、网关重试、脚本刷申请都会产生重复案件。

## 闭卷题

**问：** 合作方登录后用 Postman 把 `XSRF-TOKEN` cookie 的裸值放到 `X-XSRF-TOKEN` 去 PATCH 草稿，会发生什么？正确做法是什么？

**答：** 403 CSRF 失败。应先 `GET /api/v1/auth/csrf`，把 JSON 里的掩码 token 放到 header，同时保留会话 Cookie。
