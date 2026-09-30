# VH05 断点与夹具地图

先读 [README](README.md) 完成环境和探针安装，再配合 [逐站脚本](03-step-runbook.md)。所有路径相对学习仓库根目录。表中行号已对照当前源码；声明行不可停时选下一条可执行语句。

## D1 真实图片解码与重编码

**夹具与输入**：第一例 ImageIO 生成 2×3 ARGB PNG；第二例输入 not an image 的字节。不使用用户图片。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MediaValidatorTest#decodes_real_png_pixels_and_reencodes_without_the_input_metadata` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaValidatorTest.java:14` |
| `MediaValidatorTest#rejects_non_image_bytes_even_when_a_client_may_claim_an_image_content_type` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaValidatorTest.java:28` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D1-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaValidator.java:38` | `public ValidatedImage validate(` |
| D1-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaValidator.java:50` | `BufferedImage decoded = reader.read(0);` |
| D1-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaValidator.java:56` | `byte[] normalized = output.toByteArray();` |

**证据边界**：真实解码/重编码已被驱动；元数据剥离是代码机制，专门的带元数据样本覆盖仍待补，不能以测试方法名替代证据。

## D2 上传、二维码暂存与失败补偿

**夹具与输入**：既有例使用真实内容实体、mock validator/storage/JDBC；新增探针使用 DRAFT 和模拟图片，并分别关闭同步、手动触发回滚、手动触发提交。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MediaServiceTest#attaches_a_normalized_image_only_to_an_editable_draft_and_audits_the_minimum_event` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaServiceTest.java:22` |
| `MediaServiceTest#action_qr_upload_uses_a_reservation_instead_of_revision_media` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaServiceTest.java:46` |
| `MediaServiceTest#action_media_cannot_be_reused_by_another_content_or_account` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaServiceTest.java:75` |
| `MediaRollbackDebugProbeTest#jdbc_failure_without_synchronization_deletes_immediately` | `docs/study-Resource/2026-09-30-debug-labs/vh05/probe/MediaRollbackDebugProbeTest.java:35` |
| `MediaRollbackDebugProbeTest#rollback_callback_deletes_uploaded_object` | `docs/study-Resource/2026-09-30-debug-labs/vh05/probe/MediaRollbackDebugProbeTest.java:44` |
| `MediaRollbackDebugProbeTest#commit_callback_keeps_uploaded_object` | `docs/study-Resource/2026-09-30-debug-labs/vh05/probe/MediaRollbackDebugProbeTest.java:47` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D2-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java:63` | `storage.put(key, image.mediaType(), image.bytes());` |
| D2-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java:65` | `TransactionSynchronizationManager.registerSynchronization(` |
| D2-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java:66` | `@Override public void afterCompletion(int status)` |
| D2-B4 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java:134` | `insert into content_action_media_reservations` |

**证据边界**：手工回调证明补偿分支，不证明事务管理器真实触发或对象真删除。删除异常被吞掉；回滚后的无数据库记录对象不能笼统声称必被现有孤儿扫描找到。

## D3 公开、社群和后台媒体读取

**夹具与输入**：jdbc.query 返回合成 storage key；测试 ArgumentCaptor<String> 捕获完整 SQL；MediaStorage 为 mock。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MediaReadServiceTest#public_action_qr_requires_an_open_published_public_entry` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaReadServiceTest.java:16` |
| `MediaReadServiceTest#community_action_qr_stays_on_the_gated_path_and_still_requires_an_open_item` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaReadServiceTest.java:33` |
| `MediaReadServiceTest#managed_preview_includes_attached_action_images_after_reservation_expiry` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaReadServiceTest.java:48` |
| `MediaReadServiceTest#project_join_public_qr_is_readable_through_the_public_media_path` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaReadServiceTest.java:66` |
| `MediaReadServiceTest#project_join_gated_qr_stays_on_the_community_path` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaReadServiceTest.java:79` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D3-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java:22` | `loadPublic(` |
| D3-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java:34` | `loadCommunity(` |
| D3-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java:28` | `loadManaged(` |

**证据边界**：只证明 SQL 形状与调用参数；SQL 执行、真实授权和索引未验证。

## D4 孤儿清理与租约

**夹具与输入**：与 VH04 D4 同一组三用例；此处独立提供步骤，无需返回前一章才能操作。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MediaOrphanCleanupServiceTest#queues_before_deleting_from_private_storage` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:14` |
| `MediaOrphanCleanupServiceTest#failed_storage_deletion_stays_retryable` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:33` |
| `MediaOrphanCleanupServiceTest#each_cleanup_invocation_uses_a_distinct_claim_id` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:47` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D4-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:30` | `public int queueUnattachedAssets()` |
| D4-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:46` | `public int processDueDeletions()` |
| D4-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:67` | `if (updated == 1) completed++;` |

**证据边界**：扫描起点是数据库 media_assets；无法凭此保证所有仅存在于对象存储的孤儿都能被发现。

## D5 正文媒体引用、富文本与缓存

**夹具与输入**：合成 Markdown 含一个 media:ULID；非法输入为 HTML/远程图片/事件字段；控制器测试直接调用并检查 Cache-Control。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MarkdownDocumentServiceTest#builds_a_safe_document_with_inline_media` | `apps/api/src/test/java/cn/voidhaven/api/service/MarkdownDocumentServiceTest.java:13` |
| `MarkdownDocumentServiceTest#rejects_html_and_remote_images` | `apps/api/src/test/java/cn/voidhaven/api/service/MarkdownDocumentServiceTest.java:19` |
| `RichTextPolicyTest#rejects_html_or_event_attribute_shapes` | `apps/api/src/test/java/cn/voidhaven/api/service/RichTextPolicyTest.java:18` |
| `PublicMediaControllerTest#refuses_to_cache_public_media_so_an_emergency_hide_takes_effect_on_the_next_request` | `apps/api/src/test/java/cn/voidhaven/api/web/PublicMediaControllerTest.java:12` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D5-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MarkdownDocumentService.java:31` | `public ObjectNode parse(` |
| D5-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/RichTextPolicy.java:18` | `public void requireAllowed(` |
| D5-B3 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicMediaController.java:27` | `noStore()` |

**证据边界**：mediaIds 只抽取 ID，不查询数据库，也不验证归属；归属校验在写入修订服务。旧稿所称 legacyToMarkdown 往返不是这两个测试内容。
