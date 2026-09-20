# VH-03 手动复现运行手册

## 0. 环境与账号

| 项目 | 值 |
|---|---|
| 栈 A | `http://localhost:18080`，compose 项目 `voidhaven-repro-current` |
| 栈 B | `http://localhost:18082`，compose 项目 `voidhaven-lab` |
| 本轮状态 | 两栈均在运行（2026-09-18 实测）；VH-03 探针在两栈结果一致 |
| 账号 | `local-admin`（角色 `ADMIN,SUPER_ADMIN`）、`local-partner`（角色 `PARTNER`） |
| 口令 | 取你启动栈时使用的本地合成口令（`.env` 里的 `VOIDHAVEN_BOOTSTRAP_*_PASSWORD`） |
| 数据 | 本地合成数据；`local-partner` 已绑定一个本地合作方档案 |

先做一次连通性确认：

```powershell
$b='http://localhost:18082/api/v1'
curl.exe -s -i "$b/auth/csrf" | Select-Object -First 8
```

能拿回 `200` + `Set-Cookie: XSRF-TOKEN=...; Path=/; SameSite=Strict` 就说明栈是活的。

**安全边界**：这套凭据只用于本地合成环境。不要把它们写进截图、博客、简历或任何公开材料，也不要用真实账号替换。

## 1. 请求矩阵（全部已实测，可逐条重放）

脚本骨架（PowerShell 7，`-SkipHttpErrorCheck` 才能看到 4xx 的响应体）：

```powershell
$b='http://localhost:18082/api/v1'
$s=New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 登录（会话 cookie 自动进入 $s）
Invoke-WebRequest -Uri "$b/auth/session" -Method POST -WebSession $s -ContentType 'application/json' `
  -Body '{"loginId":"local-admin","password":"<本地口令>"}' -SkipHttpErrorCheck

# 取掩码 CSRF 令牌
$csrf=(Invoke-WebRequest -Uri "$b/auth/csrf" -WebSession $s -SkipHttpErrorCheck).Content | ConvertFrom-Json

# 带令牌的写请求
Invoke-WebRequest -Uri "$b/auth/password/change" -Method POST -WebSession $s -SkipHttpErrorCheck `
  -ContentType 'application/json' -Headers @{'X-XSRF-TOKEN'=$csrf.data.token} `
  -Body '{"currentPassword":"wrong-value","newPassword":"a-long-enough-new-password"}'
```

### 1.1 未登录

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| A1 | `GET /auth/me`（无 cookie） | `401`，响应体为空；响应头带 `Set-Cookie: VOIDHAVEN_SESSION=...; Path=/; HttpOnly; SameSite=Strict` | 未登录是 401 而不是 302 跳登录页；请求缓存顺带创建了匿名会话 |
| A2 | `GET /admin/content-items?pageSize=1`（无 cookie） | `401`，空体，同上带会话 cookie | URL 规则先拦，进不了 Controller |
| A3 | `GET /api/v1/nope`（无 cookie） | `401`，空体 | 未命中 permitAll 的路径一律要求登录 |

### 1.2 登录

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| B1 | `POST /auth/session` 口令错误 | `401` + `{"code":"AUTHENTICATION_FAILED","message":"账号或密码不正确，或账号当前不可用。",...}`；`Set-Cookie: VOIDHAVEN_SESSION=; Max-Age=0` | 不透露账号是否存在；失败立即作废刚建的会话 |
| B2 | `POST /auth/session` 正确 | `200` + `{"data":{"loginId":"local-admin","roles":["ADMIN","SUPER_ADMIN"]}}`，返回新的 `VOIDHAVEN_SESSION` | 会话 ID 已更换（见 E-01） |
| B3 | `POST /auth/session` 同一登录名 6 次口令错误 | 前 5 次 `401`，第 6 次 `429` + `{"code":"AUTH_RATE_LIMITED",...}` + `Retry-After: 900` | 额度是「15 分钟内 5 次」，不是「连续 5 次」 |

### 1.3 已登录

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| C1 | `GET /auth/me`（带会话） | `200` + `{"data":{"loginId":"local-admin","roles":["ADMIN","SUPER_ADMIN"]}}` | `Identity` 只暴露登录名与角色 |
| C2 | `GET /admin/content-items?pageSize=1` | `200` | 角色满足 `ADMIN` |
| C3 | `GET /admin/accounts?pageSize=1` | `200` | 该方法额外要求 `SUPER_ADMIN` |
| G1 | `POST /auth/session` 换 `local-partner` | `200` + `roles":["PARTNER"]` | 角色来自 `account_roles` |
| G2 | `GET /admin/content-items`（PARTNER 会话） | `403` + `{"timestamp":...,"status":403,"error":"Forbidden","path":"/api/v1/admin/content-items"}` | URL 规则拒绝，响应体是 Spring Boot 默认错误体，**不是**项目的 `ApiError` 形状 |
| G3 | `GET /partner/dashboard`（PARTNER 会话） | `200` + `{"data":{"displayName":"本地合作方（合成）","quota":3,...}}` | 合作方走自己的前缀 |

### 1.4 注销

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| D1 | `DELETE /auth/session`（已登录，**不带** CSRF 头） | `204` | `/auth/session` 是**按路径**豁免 CSRF 的，注销被一起豁免 |
| D2 | `DELETE /auth/session`（完全未登录、无 cookie、无 CSRF） | `204` | 该方法在 permitAll 列表里，未登录也走通且幂等 |
| D3 | `GET /auth/me`（注销后） | `401` | 会话真的被作废，不是前端本地清状态 |

### 1.5 CSRF（对照实验最关键的一组）

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| CS1 | `GET /auth/csrf`（未登录） | `200` + `{"headerName":"X-XSRF-TOKEN","token":"<96 字符>"}`；`Set-Cookie: XSRF-TOKEN=<36 字符 UUID>; Path=/; SameSite=Strict` | 响应体里的 token 与 cookie 里的值**不是同一个字符串**；CSRF cookie 没有 `HttpOnly`，也没有 `Secure`（本地配置） |
| CS2 | `POST /auth/password/change`（已登录，无 CSRF 头） | `403` + Boot 默认错误体 | 受保护写请求确实要求令牌 |
| CS3 | 同 CS2，把 **`XSRF-TOKEN` cookie 裸值** 放进 `X-XSRF-TOKEN` | `403` | 前端不能拿 cookie 值当令牌用（掩码机制） |
| CS4 | 同 CS2，把 `/auth/csrf` 返回的**掩码 token** 放进去 | `400 CURRENT_PASSWORD_INVALID` | CSRF 已通过，进入业务校验（旧口令错误时不会真的改密） |
| CS5 | `POST /auth/password/change`（**未登录**，无 CSRF 头） | `401`，空体 | 见 E-04：这里同时暴露了「错误派发也会再走一次授权」的行为 |

### 1.6 网络与限流的其他观察

| # | 请求 | 实测 | 结论 |
|---|---|---|---|
| F1 | 任意请求的响应头 | 都带 `X-Request-Id` | `RequestIdFilter` 在最前面，401/403 的空体响应也有 `requestId`（在响应头里） |
| F2 | 从 `18080` 与 `18082` 各跑一遍 C1/E2/E4/G2/D1/D2 | 结果 6/6 一致 | VH-03 源码在 HEAD 中，两栈行为无差异（与 VH-01 的版本差异不同） |

## 2. 需要你自己动手的六个实验

### E-01 会话固定化防护（只读，最推荐先做）

```powershell
$b='http://localhost:18082/api/v1'

# ① 未登录打一个受保护接口，让服务端先建一个匿名会话
$anon=Invoke-WebRequest -Uri "$b/admin/content-items" -SkipHttpErrorCheck -SessionVariable vs
$a=($vs.Cookies.GetCookies('http://localhost:18082') | Where-Object Name -eq 'VOIDHAVEN_SESSION').Value

# ② 用同一个会话登录
$null=Invoke-WebRequest -Uri "$b/auth/session" -Method POST -WebSession $vs -SkipHttpErrorCheck `
  -ContentType 'application/json' -Body '{"loginId":"local-admin","password":"<本地口令>"}'
$bCookie=($vs.Cookies.GetCookies('http://localhost:18082') | Where-Object Name -eq 'VOIDHAVEN_SESSION').Value

"登录前 = $a`n登录后 = $bCookie`n是否相同 = $($a -eq $bCookie)"

# ③ 用「登录前」的会话 ID 重放 /auth/me
$replay=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$replay.Cookies.Add((New-Object System.Net.Cookie('VOIDHAVEN_SESSION',$a,'/','localhost')))
(Invoke-WebRequest -Uri "$b/auth/me" -WebSession $replay -SkipHttpErrorCheck).StatusCode
```

本轮实测：登录前后 ID 不同（`False`），重放旧 ID 得到 `401`。**这就是 `request.changeSessionId()` 的作用**：攻击者即使预先知道你的会话 ID，也无法在登录后继续用它冒充你。

### E-02 限流额度归属（只读，但会占用额度 15 分钟）

1. 用**不存在的**登录名（例如 `learn-vh03-probe`）连续错 6 次，记录每次状态码与 `Retry-After`。
2. 换回 `local-admin` 立刻登录一次，观察是否**不受影响**——证明额度按登录名（HMAC 后）分桶，而不是全局。
3. 想做「成功是否清零失败计数」的判别实验：对 `local-partner` 依次做 `4 次失败 → 1 次成功（用正确口令）→ 再 2 次失败`。如果最后那两次都还是 `401`，说明成功确实把失败计数清零了；如果第 6 次（也就是最后那次）变成 `429`，说明没有清零（因为此时计数已经等于 5）。

代价说明：第 3 步可能把 `local-partner` 锁定 15 分钟。需要立刻恢复时，可以只删限流事件表（不影响账号本身）：

```powershell
docker exec -it voidhaven-lab-mysql-1 mysql -u<DB用户> -p<DB口令> voidhaven `
  -e "delete from auth_rate_limit_events where scope='LOGIN_ID'; select count(*) from auth_rate_limit_events;"
```

### E-03 掩码令牌 vs 裸 cookie 值（只读）

把 1.5 节的 CS2/CS3/CS4 三条并排做一遍，并记录 `/auth/csrf` 的 `token` 长度与 `XSRF-TOKEN` cookie 的长度。要点是能解释：为什么「cookie 里的值」和「响应体里的值」不同，以及前端为什么必须用后者。

### E-04 未登录 + 缺 CSRF 为什么是 401 而不是 403（只读，考察机制推理）

观察：CS5 得到 `401`（空体），而已登录的同类请求 CS2 得到 `403`。

可行解释（本包结论，标注为**机制推断**）：

1. `CsrfFilter`（顺序 1100）先拒绝 → `AccessDeniedHandlerImpl` 调 `sendError(403)`；
2. 容器把请求转发到 `/error`，形成 **ERROR 派发**；Spring Boot 4.1.1 默认让安全过滤链参与 ERROR 派发（`spring.security.filter.dispatcher-types` 默认全集），`AuthorizationFilter` 默认 `filterErrorDispatch = true`；
3. `/error` 不在 `SecurityConfig` 的 permitAll 列表里 → 落到 `anyRequest().authenticated()` → 匿名用户再次被拒 → `HttpStatusEntryPoint` 把最终状态改成 **401**，且不走错误页面，于是响应体为空；
4. 已登录用户在 ERROR 派发里是「已认证」，`/error` 放行，所以能拿到 403 的 Boot 默认错误体（对应 G2 与 CS2 的形状）。

想把它从推断变成证据，二选一（都需要临时改动，做之前先想清楚影响）：

- 打开安全日志：`logging.level.org.springframework.security=DEBUG`（`CsrfFilter` 会打印令牌无效的调试日志），观察到底是谁先拒绝；
- 临时把 `/error` 加进 permitAll，如果未登录的缺 CSRF 请求从此变成 `403`，就验证了第 2～4 步。

### E-05 `AccountEligibilityFilter` 会不会被执行两次（推理题）

事实：

- 它带 `@Component`，而 Spring Boot 会把 `Filter` 类型的 Bean 自动注册成 servlet 过滤器；
- 它又被 `addFilterAfter(..., SecurityContextHolderFilter.class)` 装进了安全过滤链，位置是 701；
- `springSecurityFilterChain` 的注册顺序是 `-100`，普通 Filter Bean 的注册顺序是 `Ordered.LOWEST_PRECEDENCE`，所以 servlet 层的那份一定排在安全链**后面**；
- 那也就意味着它处于安全链内那份 `OncePerRequestFilter` 的 `try/finally` **内部**，`already-filtered` 属性还在，于是被跳过。

结论：在当前装配下不会重复执行，但这是一条**依赖顺序的隐式保证**。如果哪天有人给这个 Bean 加上 `FilterRegistrationBean` 并把顺序提到 `-100` 之前，就会出现重复查询（甚至重复写响应体）。

验证办法（可选）：临时在 `doFilterInternal` 开头加一行日志，或加一个 `FilterRegistrationBean` 把 servlet 层注册显式关闭（`setEnabled(false)`）再对比行为。本轮没有实测，所以这条按「设计推断」记，不按「实测事实」记。

### E-06 停用账号后，已登录会话会怎样（**写操作，做之前请确认**）

这是 VH-03 最有说服力的一条端到端证据：**登录态、角色态、资格态三者分离**。

```powershell
# ① 用 local-admin 登录并取 CSRF 令牌，查出 local-partner 的账号 id
$b='http://localhost:18082/api/v1'
# GET /admin/accounts?pageSize=20   → 复制 local-partner 的 id

# ② 停用合作方账号（需要 CSRF + Idempotency-Key + 原因）
# POST /admin/accounts/<partnerAccountId>/suspend
# Headers: X-XSRF-TOKEN, Idempotency-Key
# Body: {"reason":"VH-03 学习实验：资格停用对已登录会话的影响"}

# ③ 用 local-partner 的旧会话请求 /auth/me
#    预期：401 + {"code":"ACCOUNT_INACTIVE","message":"当前账号已停用或资格不可用。","requestId":"..."}
#    同时该会话被作废（再用它请求仍然是 401）

# ④ 恢复现场
# POST /admin/accounts/<partnerAccountId>/reactivate  （同样带 CSRF + Idempotency-Key + 原因）
# 恢复后 local-partner 需要重新登录
```

记录要点：停用**之前**该会话是 200；停用**之后第一次**请求就已经是 401（不用等 8 小时）；响应体是过滤器手写的 JSON，字段比 `ApiError` 少（没有 `fieldErrors`、没有 `retryable`）。

## 3. 记录模板

每条记录都按这个格式留档（放在你自己的学习记录里，不要只留在对话里）：

```yaml
case_id: VH-03-<场景>-<编号>
at: 2026-09-18T12:00:00+08:00
stack: 18082 / voidhaven-lab
git: be219ac
request: "POST /api/v1/auth/session"          # 方法 + 路径
preconditions: "无 cookie；loginId=local-admin"  # 前置状态
sent: ["Content-Type: application/json"]        # 实际发出的关键头
expected: "401 AUTHENTICATION_FAILED"
observed_status: 401
observed_body: '{"code":"AUTHENTICATION_FAILED",...}'
observed_headers: ["X-Request-Id: ..."]
request_id: "..."
conclusion: "失败即作废刚创建的会话；不区分账号不存在与密码错误"
evidence_class: RUNTIME_CURRENT_SOURCE        # 见 07-visual-test-chain 的证据标签定义
notes: "本地合成账号；未使用真实凭据"
```

## 4. 清理与边界

- 学习期间可以保持两栈运行；结束后停容器但**保留数据卷**，不要执行带 `-v` 的删除。
- E-06 会产生账号状态变更与审计记录（本地合成库），做完要恢复 `reactivate`。
- E-02 会写入限流事件行，15 分钟后自然过期；`RetentionService` 夜间任务也会清理。
- 所有截图与笔记都要检查：不要出现 cookie 值、CSRF 令牌、口令、激活凭据或私密联系方式。
