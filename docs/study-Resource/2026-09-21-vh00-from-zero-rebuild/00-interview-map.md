# 00 技术栈 ↔ 八股 ↔ 本项目落点

秋招问「你项目里用过 Spring 吗」，要答的是**这个表里的落点**，不是背官方文档目录。

## 1. 你会被问到的栈

| 层 | 本项目选型 | 版本锚点 | 面试怎么开口 |
|---|---|---|---|
| 语言 | Java 17 | `apps/api/pom.xml` `<java.version>` | 用过 record、switch 表达式、文本块；ID 用 ULID 不是自增 |
| 构建 | Maven Wrapper | `apps/api/mvnw.cmd` | 不依赖本机装 Maven；`verify` 跑单测 + failsafe |
| 框架 | Spring Boot 4.1.1 | parent POM | starter 是约定依赖，不是「另一个框架」 |
| Web | `starter-webmvc` | Controller + Filter | REST + JSON 信封 `{data, requestId}` |
| 持久化 | Spring Data JPA | `ddl-auto: validate` | 表由 Flyway 管，Hibernate 只校验 |
| 迁移 | Flyway V1～V24 | `db/migration/` | 已发布脚本不可改，只能追加 |
| 安全 | Spring Security 7.1.1 | `SecurityConfig` | Cookie Session + CSRF + 方法级授权 |
| 会话 | Spring Session JDBC | `SPRING_SESSION`（V10） | 重启不丢登录；不用 Redis |
| 密码 | Argon2 | `bcprov-jdk18on` | 内存硬、抗 GPU，比 BCrypt 更新一档 |
| 媒体 | S3 兼容 / MinIO | AWS SDK 2.54.7 | 本课不展开，知道私有桶即可 |
| 契约 | OpenAPI 3.1 | `contracts/openapi/voidhaven-v0.yaml` | 接口真源是 YAML，不是 Controller |
| 前端对接 | `apps/web/src/lib/api.ts` | Cookie / CSRF / 幂等 / If-Match | 前端禁止绕过这一层 |

**没有 Redis。** 限流、幂等、分布式锁、会话全部走 MySQL。面试官追问「为什么」→ ADR-V0-008：单机单副本，少一个故障域。

## 2. 八股题 → 项目里哪一段

| 八股题 | 标准答案的项目落地 |
|---|---|
| Maven 生命周期？ | `compile → test → package → verify`。本项目 `mvnw -B verify` 跑 Surefire + Failsafe |
| parent POM / BOM 干什么？ | Boot parent 管版本；AWS SDK 用 BOM import，避免传递依赖打架 |
| starter 是什么？ | 一组约定依赖 + 自动配置。本项目仍显式写 `SecurityFilterChain`，因为默认 formLogin 不适用 |
| IOC / 依赖注入？ | Controller 构造器注入 Service；可选依赖用 `@Autowired(required = false)` setter（如 `ContentActionService`） |
| Bean 作用域？ | 默认单例。不要把请求级状态塞进 Service 字段 |
| `@Transactional` 失效？ | 同类自调用不走代理；幂等预约用 `REQUIRES_NEW` 独立事务，就是为了避免和业务事务绑死 |
| 乐观锁 vs 悲观锁？ | `@Version` + HTTP `If-Match` 防丢失更新；`findByIdForUpdate` 防 `revision_number` 唯一约束竞态 |
| 游标分页 vs OFFSET？ | `CatalogCursor` + `LIMIT pageSize+1`，深页不扫前面所有行 |
| Session vs JWT？ | 管理端/合作方要登出、停用立刻失效 → Cookie Session 进 MySQL |
| CSRF 是什么？ | 浏览器会自动带 Cookie，所以写请求必须再带掩码 token。公开投稿豁免 CSRF，改用幂等+限流 |
| 401 vs 403？ | 没登录 / 会话失效 → 401；登录了但角色不够 → 403 |
| 404 vs 403（资源隐藏）？ | 未发布内容统一 404，避免存在性泄露 |
| Flyway 为什么不能改历史？ | 生产库已经执行过 checksum；改历史会导致新环境与旧环境分叉 |
| 为什么不用 `ddl-auto=update`？ | 不可审计、不可回滚、生产会悄悄改表。本项目 `validate` + Flyway |
| 过滤器顺序？ | `RequestIdFilter` → SecurityContext(700) → `AccountEligibilityFilter`(701) → CSRF(1100) → Authorization(4200) |

## 3. 架构决策（面试「为什么这么做」）

对照 `docs/architecture/decisions.md` 与 `docs/standards/S-04`：

| ID | 一句话 |
|---|---|
| ADR-V0-001 | React/Vite 与 Spring Boot 分离，Nginx 同源代理，避免跨域把 Cookie 搞复杂 |
| ADR-V0-002 | MySQL + Flyway，schema 进 Git |
| ADR-V0-005 | `workflow_status` ≠ `publication_status`；`current_revision` ≠ `published_revision` |
| ADR-V0-008 | 单机；限流/会话/锁走 MySQL |
| S-04 原则 4 | 结束 ≠ 下线 ≠ 归档 ≠ 删除 |
| S-04 原则 5 | 账号 ≠ 内容所有权（合作方负责人可以换，内容归属 `partner_profile`） |
| DR-001 | 1 Account ↔ 1 PartnerProfile，无组织层级 |
| OpenAPI | `docs/standards` > `docs/decisions` > `contracts/openapi` > 实现代码 |

## 4. 产品边界（防止面试吹过头）

产品明确**不做**：普通学生账户、简历、报名、评论、私信、聊天、人才库、双向组队、复杂推荐。

官方来源是竞赛信息的必要字段；站内导读不能替代官方规则。这直接解释了 `ContentSourcePolicy` 为什么 fail-closed。

## 5. 建议你准备的「项目经历」结构

1. **背景**：校园信息散落在公众号/官网，需要可核验的发现平台。
2. **你的职责口径**（复现课后）：能讲清后端分层、读路径、写路径状态机、安全边界。
3. **难点**：双修订指针、游标内嵌 sort、公开读 404 语义、CSRF 掩码、幂等独立事务。
4. **结果**：OpenAPI 契约、Flyway 迁移、Cookie 会话、不引入 Redis。
