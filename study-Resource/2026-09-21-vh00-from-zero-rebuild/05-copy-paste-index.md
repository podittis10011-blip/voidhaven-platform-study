# 05 外围整文件对照表

核心三链路讲完后，其余按组 **从本仓库整文件复制**。面试只记「这组干什么」，不要背实现。

基础路径：`apps/api/src/main/java/cn/voidhaven/api/`  
资源路径：`apps/api/src/main/resources/`

复制时保持包名和目录不变。依赖关系：先信封/配置，再领域，再核心 Service，再外围 Controller。

## A. 能启动的最小骨架

| 文件 | 一句职责 |
|---|---|
| `VoidhavenApiApplication.java` | 启动类 |
| `config/SecurityConfig.java` | 会话 / CSRF / URL 授权 |
| `config/WebConfiguration.java` | 拦截器、CORS 关闭、静态与转换 |
| `config/ProductionConfigurationValidator.java` | 生产必须 Secure Cookie、密钥非空 |
| `config/MediaStorageConfiguration.java` | MinIO/S3 客户端 Bean |
| `config/LocalDataInitializer.java` | local profile 种子账号（合成数据） |
| `config/BootstrapAdminCommand.java` | 生产 bootstrap 不走 Web 进程 |
| `web/RequestIdFilter.java` | 每请求 ULID，写入头与 MDC |
| `web/NoStoreResponseFilter.java` | 管理响应禁缓存 |
| `web/ApiResponse.java` | 成功信封 |
| `web/ApiError.java` | 错误体 |
| `web/ApiException.java` | 业务异常（status + code） |
| `web/ApiExceptionHandler.java` | 异常 → 稳定 JSON；乐观锁 → 409 |
| `web/HealthController.java` | `/healthz` |
| `support/Ids.java` | ULID |
| `support/PageResult.java` | 管理端分页结构 |
| `support/LoginIds.java` | 登录名规范化 |
| `support/SecureTokens.java` | 激活/重置令牌哈希 |

配套资源：

- `resources/application.yml`
- `resources/application-local.yml`
- `resources/application-production.yml`
- `pom.xml`

## B. 领域 + 仓储（必须全要）

`domain/` 11 个、`persistence/` 6 个，见 `01-scaffold.md`。缺一个实体，JPA `validate` 就会启动失败。

## C. 核心读/写（精讲过，仍要整文件）

| 文件 | 一句职责 |
|---|---|
| `web/PublicContentController.java` | 公开列表/详情/首页 |
| `web/PublicContentDto.java` | 公开投影 |
| `web/PublicMediaDto.java` | 公开媒体卡片 |
| `service/ContentService.java` | 目录、详情、sitemap 谓词 |
| `service/CatalogCursor.java` | 游标编解码 |
| `service/CatalogSort.java` | 排序白名单 |
| `service/CatalogQuery.java` | 动态 SQL |
| `service/ContentSourcePolicy.java` | 来源 HTTPS fail-closed |
| `service/ContentLanguagePolicy.java` | 语言标签与外语导读 |
| `service/ContentModuleRegistry.java` | 模块 schema 注册表 |
| `service/ContentManagementService.java` | 草稿到发布状态机 |
| `web/PartnerController.java` | 合作方 CRUD + 送审 |
| `web/AdminController.java` | 审核/发布/隐藏/账号 |
| `web/AuthController.java` | 登录登出 CSRF 激活改密 |
| `web/AccountEligibilityFilter.java` | 停用立即 401 |
| `web/ClientNetworkSubject.java` | 限流用的网络主体（是否信任代理） |

## D. 信封级横切（建议第二批复制）

| 文件 | 一句职责 |
|---|---|
| `service/IdempotencyService.java` | 幂等执行 / 一次性秘密 |
| `service/IdempotencyReservationService.java` | `REQUIRES_NEW` 预约行 |
| `service/AuditService.java` | 写审计与审核记录 |
| `service/AuditQueryService.java` | 管理端查审计 |
| `service/MarkdownDocumentService.java` | Markdown → 安全 Document JSON |
| `service/RichTextPolicy.java` | 富文本消毒 |
| `service/RateLimitLockService.java` | MySQL 行锁限流 |
| `service/AuthenticationRateLimiter.java` | 登录失败/网段限制 |
| `service/PublicWriteRateLimiter.java` | 公开写窗口 |
| `service/PublicStatusQueryRateLimiter.java` | 回执查询限流 |
| `web/PublicWriteRateLimitInterceptor.java` | 挂到公开写路径 |
| `service/AccountLockService.java` | 操作前锁账号行 |
| `service/MysqlAdvisoryLock.java` | `GET_LOCK` 给定时任务 |
| `service/PasswordService.java` | 改密/人工重置消费 |

## E. 媒体（整组复制，本课不讲）

| 文件 | 一句职责 |
|---|---|
| `service/MediaStorage.java` | 存储接口 |
| `service/S3MediaStorage.java` | MinIO |
| `service/MediaValidator.java` | 类型/尺寸/解码 |
| `service/MediaService.java` | 上传、绑定修订 |
| `service/MediaReadService.java` | 授权读 URL |
| `service/MediaOrphanCleanupService.java` | 孤儿对象清理 |
| `web/MediaController.java` | 登录用户上传 |
| `web/PublicMediaController.java` | 公开/社群授权 GET |

## F. 公开参与 / 社群 / 首页 / 目录（一句职责）

| Controller | Service | 职责 |
|---|---|---|
| `PublicInputController` | `PublicCaseService`、`PublicCaseStates`、`PublicCaseAdministrationService`、`PublicInputAdministrationService` | 投稿/合作申请 + 回执查询 |
| `CommunityController` / `CommunityAdminController` | `CommunityService` | HMAC 邀请码、grant |
| `HomepageAdminController` | `HomepageAdministrationService`、`HomepageProjectionService`、`HomepageAssembler`、`HomepageEligibility` | 首页四槽 |
| `CatalogOrderAdminController` | `CatalogOrderService` | 人工排序/默认 sort |
| `ContentVerificationAdminController` | `ContentVerificationService` | 来源核验记录 |
| `ContentModuleController` | `ContentModuleRegistry` | 模块定义 JSON |
| `ProductCapabilitiesController` | （配置开关） | 前端探测能力 |
| `ProjectJoinController` / `ProjectJoinAdminController` | `ProjectJoinService` | 项目加入入口 |
| `PasswordAdminController` | `PasswordService` | 人工发重置链 |
| `ManualDeliveryAdminController` | `ManualDeliveryService` | 人工凭据交付 |
| `AdminTaskController` | `OperationalTaskService` | 触发到期关闭等 |
| `SitemapController` | `ContentService.publicSitemapEntries` | 搜索引擎 |
| — | `ContentActionService` | 招募行动入口（发布前置条件） |
| — | `ContentLifecycleService` | 到期关闭/归档 |
| — | `ContentRevisionCandidateService` | 候选修订辅助 |
| — | `RelatedContentIds` | 详情关联 |
| — | `PartnerAdministrationService`、`PartnerAccountProvisioningService`、`AccountAdministrationService` | 账号/配额/过户 |
| — | `ActivationService` | 激活令牌 |
| — | `RetentionService` | 保留期清理 |
| — | `PrivateValueCipher` | 私人联系方式加密 |

## G. Flyway 一句话索引

文件都在 `apps/api/src/main/resources/db/migration/`。从 0 复现：先 V1，其余整目录复制。

| 版本 | 一句话 |
|---|---|
| V1 | 账号、内容、修订、媒体、审核、投稿、幂等、审计 |
| V2 | `publication_status` |
| V3 | 公开输入审核元数据 |
| V4 | 媒体 alt |
| V5 | 结构化语言与权利 |
| V6 | 模块、Markdown、筛选值 |
| V7 | 媒体删除任务、认证限流事件 |
| V8 | 角色种子 |
| V9 | utf8mb4 |
| V10 | `SPRING_SESSION` |
| V11 | `rate_limit_locks`、公开写限流 |
| V12 | 行动入口、核验记录 |
| V13 | 公开案件状态史 |
| V14 | 社群入口/邀请/grant |
| V15 | 密码重置、人工交付 |
| V16 | 首页策展 |
| V17 | 发布阻塞与状态查询限流 |
| V18–V19 | 自助找回上线后又退役 |
| V20 | 入口展示/渠道/访问级别 |
| V21 | 行动媒体租约 |
| V22 | 首页四槽 |
| V23 | 目录排序 |
| V24 | 项目加入 |

合并阅读快照（非 Flyway）：`study-Resource/2026-09-20-db-schema/voidhaven-current-schema.sql`。

## H. 契约与前端（不手写，要知道在哪）

| 路径 | 职责 |
|---|---|
| `contracts/openapi/voidhaven-v0.yaml` | 接口真源 |
| `apps/web/src/lib/api.ts` | 会话、CSRF、幂等、If-Match |
| `infra/compose/compose.local.yml` | MySQL + MinIO + api + web + nginx |

## 面试怎么用这张表

被问「还有什么模块」时按 **公开读 / 内容生命周期 / 安全 / 公开投稿 / 媒体 / 运营配置** 六组各一句。不要开始背 Controller 名。
