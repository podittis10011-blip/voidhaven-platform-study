# 01 脚手架：空项目是怎样变成可运行 API 的

本段对应时间盒 0～2（约 40–55 分钟）。粘贴真源是仓库里的文件，按清单打开对照，不要另写一份。

## 段 0. 先决定，再写代码（10–15 min）

对照：

- `README.md`
- `docs/architecture/scope-and-boundaries.md`
- `docs/architecture/decisions.md`
- `docs/standards/S-04-domain-permissions-content-lifecycle-and-privacy.md`

**落地四条：**

1. 不做学生账号/评论/私信。
2. 官方来源不可被站内导读替代。
3. OpenAPI 是接口真源。
4. Flyway 已发布迁移只追加不改。

### 八股：单体 vs 前后端分离；契约先行；迁移不可改历史

- **分离**：浏览器 SPA 与 API 进程独立，Nginx 同源反代 `/api`。好处是前端静态资源与 Java 发布周期解耦；同源是为了 Cookie `SameSite=Strict` 能带上。
- **契约先行**：改接口先改 `contracts/openapi/voidhaven-v0.yaml`，再改 Controller。面试官问「前后端怎么对齐」就答这一句。
- **迁移不可改**：Flyway 用 checksum 校验。改 V1 的人会让已经上线的库和新建的库变成两套 schema。正确做法是 `V25__...sql`。

## 段 1. 从 Maven 空项目开始（15–20 min）

### 1.1 整文件打开这三份

| 顺序 | 路径 | 你要看懂的点 |
|---|---|---|
| 1 | `apps/api/pom.xml` | parent 4.1.1、Java 17、每个 starter |
| 2 | `apps/api/src/main/java/cn/voidhaven/api/VoidhavenApiApplication.java` | `@SpringBootApplication` + `@EnableScheduling` |
| 3 | `apps/api/src/main/resources/application.yml` | `ddl-auto: validate`、Session JDBC、Cookie 名 |

入口只有 13 行：扫包、自动配置、定时任务（到期关闭/归档）。业务不写在 `main` 里。

### 1.2 每个依赖在本项目里干什么

| 依赖 | 本项目用途 |
|---|---|
| `starter-webmvc` | REST Controller、过滤器、JSON 信封 |
| `starter-data-jpa` | 实体 / Repository；禁止 Hibernate 改表 |
| `starter-validation` | `@NotBlank` / `@Size` 入参校验 |
| `starter-security` | 会话、CSRF、URL 授权、`@PreAuthorize` |
| `starter-session-jdbc` | 会话进 MySQL 表 `SPRING_SESSION` |
| `starter-actuator` | `/actuator/health/readiness` 含 db |
| `starter-flyway` + `flyway-mysql` | 启动时按版本跑迁移 |
| `mysql-connector-j` | runtime 驱动 |
| `bcprov-jdk18on` | Argon2 |
| AWS SDK S3 | MinIO 私有媒体（本课不展开） |
| `imageio-webp` | 上传解码重编码（DR-003） |
| `starter-test` + Testcontainers | 单测 / 真实 MySQL 集成测 |

### 1.3 八股：Maven、starter、自动配置

**生命周期（要能默写）：**

```text
validate → compile → test → package → verify → install → deploy
```

本项目日常：`apps/api` 下 `.\mvnw.cmd -B verify`。`verify` 包含 Surefire（单元）和 Failsafe（IT）。

**parent POM：** `spring-boot-starter-parent` 锁定 Spring / Hibernate / 插件版本，子模块不用自己填一堆 version。

**BOM：** `software.amazon.awssdk:bom` `type=pom` `scope=import`，S3 客户端与传递依赖同一套版本。

**starter ≠ 框架：** starter 只是「依赖集合 + `spring.factories`/自动配置」。Boot 根据 classpath 猜你要 DataSource、DispatcherServlet。安全默认是 formLogin，本项目不适用，所以 `SecurityConfig` 显式声明 `SecurityFilterChain`。

**面试加分句：** 「自动配置能省样板，不能省安全决策。认证方式、CSRF 豁免、Session 策略必须写在自己的 `@Bean` 里。」

### 1.4 `application.yml` 里三条生产纪律

打开 `apps/api/src/main/resources/application.yml`：

1. `spring.jpa.hibernate.ddl-auto: validate`  
   Hibernate 只核对实体与表是否一致。不一致就启动失败。**面试里主动否定 `update`。**
2. `spring.jpa.open-in-view: false`  
   关掉 OSIV，避免 Controller 渲染时懒加载打出 N+1。需要关联就用 `@EntityGraph`。
3. `spring.session.store-type: jdbc` + `jdbc.initialize-schema: never`  
   会话表由 Flyway V10 建，不让 Spring Session 自己 `CREATE TABLE`。

其它要能指出来：Cookie 名 `VOIDHAVEN_SESSION`、`http-only`、`same-site: strict`、超时 8h；错误体不回异常 message（`server.error.include-message: never`）。

### 1.5 Flyway：只精读 V1，其余当索引

打开 `apps/api/src/main/resources/db/migration/V1__initial_schema.sql`，盯这几张表：

| 表 | 为什么是核心 |
|---|---|
| `accounts` / `roles` / `account_roles` | 登录主体；角色是多对多 |
| `partner_profiles` | 内容归属，1:1 账号；`content_quota` 限制同时公开条数 |
| `content_items` | 四维状态 + 双修订指针 + `version` |
| `content_revisions` | 不可变快照；`UNIQUE(content_item_id, revision_number)` |
| `*_revision_details` | 竞赛/招募/资源类型字段，避免一张宽表 |
| `idempotency_records` | 写路径防重 |
| `audit_logs` | 谁在何时把状态从 A 改到 B |

V1 之后用一句话记住即可（详见 `05-copy-paste-index.md`）：

| 版本 | 加了什么 |
|---|---|
| V2 | `publication_status`，和工作流拆开 |
| V6 | 可演进模块 + Markdown |
| V8 | 角色种子 |
| V10 | `SPRING_SESSION` |
| V11 | 限流行锁 |
| V12 | 招募行动入口 + 来源核验 |
| V14 | 社群门禁 |
| V20 | 入口展示/渠道/访问级别（DR-005） |
| V23 | 目录人工排序 |
| V24 | 项目加入入口 |

**从 0 复现时的操作顺序：** 先让 V1 能跑通「账号 + 内容 + 修订」，再整目录复制其余 `V*.sql`。不要试图在 2 小时内手写 24 份 SQL。

## 段 2. 分层与领域模型（15–20 min）

### 2.1 真实包结构（粘贴，不发明）

```text
cn.voidhaven.api
├── VoidhavenApiApplication.java
├── config/       Security、WebMvc、媒体、本地种子、生产校验、bootstrap 命令
├── domain/       JPA 实体 + 枚举 + 状态方法
├── persistence/  仅 6 个 Repository
├── service/      业务、策略、分页、幂等、限流
├── support/      ULID、分页结构、令牌
└── web/          Controller、DTO、Filter、异常信封
```

请求怎么走：

```text
HTTP
  → RequestIdFilter
  → SecurityFilterChain
  → AccountEligibilityFilter
  → PublicWriteRateLimitInterceptor（公开写）
  → Controller（参数 + 信封）
  → Service（@Transactional + 规则）
  → Repository / JdbcTemplate
  → MySQL ；媒体走 MinIO
```

**八股：三层不够当口号。** Controller 不写 SQL，Service 不拼 HTTP 头，实体不依赖 Web 包。本项目复杂查询有两处：目录用 `JdbcTemplate`（动态 SQL），详情用 JPA `@Query` + `@EntityGraph`。

### 2.2 必须默写的 `ContentItem` 字段

文件：`apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java`

四维状态（S-04 原则 4）：

| 维度 | 字段 | 回答的问题 |
|---|---|---|
| 工作流 | `workflow_status` | 稿件走到哪了（草稿/待审/已发布…） |
| 发布 | `publication_status` | 对公众是无/在线/已结束 |
| 可见 | `visibility` | PUBLIC 还是 HIDDEN |
| 记录 | `record_state` | ACTIVE / ARCHIVED / DELETED |

双指针：

| 指针 | 含义 |
|---|---|
| `current_revision_id` | 编辑器正在改的那一版 |
| `published_revision_id` | 公开站正在展示的那一版 |

`publish()` 把 `publishedRevision = currentRevision`，并设 `visibility = PUBLIC`。已发布后再编辑会新建候选修订，只移动 `current`，不碰 `published`。

`@Version private Long version`：JPA 乐观锁。HTTP 层用 `If-Match` 传入同一个数字。两个人同时审同一条，后到的 409 `CONTENT_VERSION_CONFLICT`。

状态迁移写在实体方法里（`submit()` / `approve()` / `publish()` / `hide()` / `archive()`），Service 调用方法而不是 `setWorkflowStatus("PUBLISHED")`。这是领域模型，不是贫血 getter 堆。

### 2.3 本段要整文件打开的实体与仓库

**domain（11 个，全复制）：**

- `ContentItem.java`、`ContentRevision.java`
- `ContentKind.java`、`WorkflowStatus.java`、`PublicationStatus.java`、`Visibility.java`、`RecordState.java`
- `Account.java`、`Role.java`、`PartnerProfile.java`、`ActivationToken.java`

**persistence（6 个，全复制）：**

- `AccountRepository`、`RoleRepository`、`PartnerProfileRepository`
- `ContentItemRepository`、`ContentRevisionRepository`、`ActivationTokenRepository`

公开读的关键查询在 `ContentItemRepository.findPublicById`：必须 `publishedRevision is not null` 且 `visibility = PUBLIC` 且 `recordState = ACTIVE`。草稿在这条查询里根本不存在。

### 2.4 八股：事务与乐观锁

- `@Transactional` 默认传播 `REQUIRED`，只回滚 **RuntimeException / Error**。受检异常要 `rollbackFor`。
- 同类 `this.foo()` 不走代理，事务注解无效。所以幂等预约单独做成 `IdempotencyReservationService`。
- 乐观锁：`UPDATE ... SET version=version+1 WHERE version=?`，影响行数为 0 就抛 `ObjectOptimisticLockingFailureException`，被 `ApiExceptionHandler` 翻成 409。
- 悲观锁：`SELECT ... FOR UPDATE`，用在 `publish` / `revise`（已发布）/ 配额检查。挡住的是「两个事务读到同一个 `revision_number` 再各插一行」。

闭卷题（先自己答）：**结束、下线、归档、删除分别改哪一维？**  
参考：结束 → `publication_status=CLOSED`；下线 → `visibility=HIDDEN`；归档 → `record_state=ARCHIVED` 且隐藏；删除 → `record_state=DELETED` 且隐藏。
