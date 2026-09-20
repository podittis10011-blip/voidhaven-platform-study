# VH-05 测试地图：19 条相关测试证明了什么、没证明什么

## 1. 本轮实测命令与结果

```powershell
cd D:\Projects\voidhaven\voidhaven-platform\apps\api
& 'D:\Maven\apache-maven-3.9.4\bin\mvn.cmd' -o -B clean test `
  '-Dtest=MediaValidatorTest,MediaServiceTest,MediaReadServiceTest,MediaOrphanCleanupServiceTest,PublicMediaControllerTest,RichTextPolicyTest,MarkdownDocumentServiceTest' `
  -DfailIfNoTests=false
```

```text
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS  （2026-09-19 23:46，JDK 17.0.18 / Maven 3.9.4 / HEAD 2295a41，clean 后重新编译）
```

单类结果：

| 测试类 | 条数 | 层次 | 用时 |
|---|---:|---|---:|
| `MarkdownDocumentServiceTest` | 2 | Service（纯函数） | 0.37 s |
| `MediaOrphanCleanupServiceTest` | 3 | Service（mock JdbcTemplate + 假存储） | 3.57 s |
| `MediaReadServiceTest` | 5 | Service（mock JdbcTemplate） | 0.04 s |
| `MediaServiceTest` | 3 | Service（mock 仓储与存储） | 0.28 s |
| `MediaValidatorTest` | 2 | Component（真图片字节） | 0.12 s |
| `RichTextPolicyTest` | 3 | Component | 0.04 s |
| `PublicMediaControllerTest` | 1 | Web 层（直接调 Controller 方法） | 0.06 s |

命令说明：`-o` 走本地 `.m2`，不联网；`clean` 强制重新编译，避免复用旧 `target/` 造成「测的是上个版本」的假证据。带 Docker 的 `MySqlAndMinioIntegrationTest` 不在这条命令里（见第 4 节）。

## 2. 逐条看它们断言了什么

### 2.1 `MediaValidatorTest`（2 条）

| 用例 | 断言 | 证明 |
|---|---|---|
| `decodes_real_png_pixels_and_reencodes_without_the_input_metadata` | 真 PNG 字节进来后：`mediaType=image/png`、`2×3`、输出能被 `ImageIO.read` 读回 | 走的是真解码真编码，不是改个 `Content-Type` 糊过去 |
| `rejects_non_image_bytes_even_when_a_client_may_claim_an_image_content_type` | `"not an image".getBytes()` 抛异常 | 类型判断基于内容 |

### 2.2 `MediaServiceTest`（3 条）

| 用例 | 断言 | 证明 |
|---|---|---|
| `attaches_a_normalized_image_only_to_an_editable_draft_and_audits_the_minimum_event` | 归一化后的媒体被写进修订，并落一条审计 | 正常上传路径的分支顺序 |
| `action_qr_upload_uses_a_reservation_instead_of_revision_media` | 断言 SQL 是 `insert into content_action_media_reservations`，且**从不**写 `revision_media` | 行动入口与正文图片是两套数据模型 |
| `action_media_cannot_be_reused_by_another_content_or_account` | 复用他人内容/账号的资产时抛错 | 暂存资产的归属校验 |

### 2.3 `MediaReadServiceTest`（5 条）

| 用例 | 断言 | 证明 |
|---|---|---|
| `public_action_qr_requires_an_open_published_public_entry` | 捕获到的 SQL 里含 `ca.access_level = 'PUBLIC'`、`c.published_revision_id = ca.revision_id`、`c.operational_status = 'OPEN'`、`c.visibility = 'PUBLIC'`、`c.record_state = 'ACTIVE'`、`ce.access_level = 'PUBLIC'` | 公开读路径包含这 6 个条件 |
| `community_action_qr_stays_on_the_gated_path_and_still_requires_an_open_item` | 含 `ca.access_level = 'COMMUNITY_GATED'`，且**不含** `'PUBLIC'` | 社群读不会顺手把公开条件带进来 |
| `managed_preview_includes_attached_action_images_after_reservation_expiry` | 含 `content_action_entries`、`c.current_revision_id = ca.revision_id`、`content_action_media_reservations` | 暂存过期后，只要已被挂载仍可预览 |
| `project_join_public_qr_is_readable_through_the_public_media_path` | 含 `project_join_entries pje` 与 `pje.access_level = 'PUBLIC'` | 新增的公开读取分支 |
| `project_join_gated_qr_stays_on_the_community_path` | 含 `project_join_entries pje`，且**不含** `pje.access_level` | 社群路径不附加公开限制 |

### 2.4 `MediaOrphanCleanupServiceTest`（3 条）

| 用例 | 断言 | 证明 |
|---|---|---|
| `queues_before_deleting_from_private_storage` | 先入队、再调存储删除，返回 1 | 删除有阶段划分，不是「查到就删」 |
| `failed_storage_deletion_stays_retryable` | 存储抛异常时返回 0，任务留在可重试状态 | 失败不会被静默吞掉 |
| `each_cleanup_invocation_uses_a_distinct_claim_id` | 两次调用的 claim 都以实例前缀开头且互不相同 | 租约是「实例 + 每次调用」粒度，避免互相踩 |

### 2.5 `PublicMediaControllerTest`（1 条）

| 用例 | 断言 | 证明 |
|---|---|---|
| `refuses_to_cache_public_media_so_an_emergency_hide_takes_effect_on_the_next_request` | `Cache-Control: no-store`、`Content-Type: image/png` | Controller 自己负责这条路径的禁用缓存 |

### 2.6 另外两组（正文引用相关）

| 类 | 条数 | 断言 | 证明 |
|---|---:|---|---|
| `RichTextPolicyTest` | 3 | 只允许版本化段落文档；`<script>`/事件属性形状被拒；服务端生成的 image 块合法，但块上出现未声明属性被拒 | 正文结构白名单 |
| `MarkdownDocumentServiceTest` | 2 | 合法 Markdown 能生成带 `mediaId` 的安全文档；HTML 与远程图片被拒 | `![alt](media:id)` 是唯一图片入口 |

前端另有 7 条 `apps/web/src/lib/editor-media.test.ts`（token 检测、插入、删除、按草稿状态禁用上传、alt 重写与截断），以及 `apps/web/e2e/full-stack.spec.ts` 第 156～196 行的真实产品闭环：上传行动入口二维码 → 断言后台预览里**不出现**该资产（因为走的是暂存表）→ 发布后公开详情里出现 `/api/v1/public/media/{qrId}`。注意那一层是 Playwright 的 **Mock E2E**，fixture 拦截了 API，不证明 Spring Boot 的真实响应。

## 3. 一句话记忆

| 层次 | 代表 | 证明 | 不证明 |
|---|---|---|---|
| Component | `MediaValidatorTest` | 解码/重编码/拒绝非图片 | HTTP、配额、事务 |
| Service（纯 mock） | `MediaServiceTest` | 分支与依赖调用顺序 | 真实 SQL、真实锁、真实对象存储 |
| Service（读路径） | `MediaReadServiceTest` | **SQL 文本里有这些条件** | SQL 在数据库里执行后的结果 |
| Web（直调方法） | `PublicMediaControllerTest` | 响应头与内容类型 | 安全过滤链是否放行 |
| Frontend Mock E2E | `full-stack.spec.ts` | 前端与契约的适配 | 后端真实行为 |
| 真实栈手动复现 | 本目录 `03` | 当前镜像下多层协作 | 未执行的组合与长期稳定性 |

## 4. 盲区（写进面试答案反而加分）

| 盲区 | 具体表现 | 怎么补 |
|---|---|---|
| 读路径测试只比对 SQL 字符串 | `MediaReadServiceTest` 全部用 `ArgumentCaptor` 抓 SQL 再 `contains`，**没有一条 SQL 真在 MySQL 上跑过**；条件写错、字段名写错、JOIN 写错都可能仍然全绿 | 用真实库做集成测试，或至少把 SQL 拿到 MySQL 里手工执行一遍对照 |
| 没有 WebP 用例 | 白名单里有 WebP，但 `MediaValidatorTest` 只有 PNG 与非图片 | 造一张真 WebP 走一次上传 |
| 体积与像素上限没有用例 | 5 MB、6000 px、2500 万像素这三道阈值没有对应测试 | 直接构造边界文件跑 `validate` |
| 没有集成测试覆盖上传链路 | `MySqlAndMinioIntegrationTest` 只验证 Flyway 迁移结果、`media_deletion_jobs` 列、以及 `S3MediaStorage` 的 put/get/delete 往返 | 需要 Docker；本地栈的 `03` E-05/E-06 是替代品 |
| 配额与并发没有测试 | 「每修订 10 张 / 20 MB」只在代码里，靠 `findByIdForUpdate` 串行化 | 真并发上传才是证据，快速连点不算 |
| 异常映射没有测试 | `HandlerMethodValidationException`、`MissingServletRequestParameterException`、`FileSizeLimitExceededException` 都没有对应断言，所以第 4 节那三处 `500` 才能长期存在 | 为 `ApiExceptionHandler` 补一组「参数校验失败返回 400」的 MockMvc 测试 |

## 5. 边界声明

- 19 条通过只说明**这些断言在某台机器、某个 JDK/Maven 版本、某个提交下成立**，不等于「媒体功能全部正确」。
- 本轮结果属于 `TEST_CURRENT`：工作树干净、HEAD `2295a41`、`clean` 后重跑。若工作树发生变化，或有人改了 `pom.xml` 依赖版本，需要重跑才能沿用。
- 测试通过不构成掌握证据，也不触发任何学习状态变更。
