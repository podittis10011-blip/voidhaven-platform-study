# VH05 单步脚本

每站先在 [观察表](04-observation-sheets.md) 写预测。以下预期来自源码/断言，交付方执行状态见 [台账](08-evidence-ledger.md)；不等于你已观察。

## D1 真实图片解码与重编码

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MediaValidatorTest#decodes_real_png_pixels_and_reencodes_without_the_input_metadata`
- `MediaValidatorTest#rejects_non_image_bytes_even_when_a_client_may_claim_an_image_content_type`

1. Debug 第一例，进入 validate，观察 reader 格式、width、height。
2. F8 越过解码，看 alpha=true，于是输出 PNG；越过 ImageIO.write 后看 normalized，回测试帧看 validated。
3. 记录输入输出长度即可，不预设一定不同；现有样本没有特制元数据，不能用长度差声称 EXIF 清理已被测试。
4. Debug 非图片例，在 readers.hasNext 拒绝处观察 MEDIA_REJECTED，随后由测试接住异常。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 测试帧 validated.width() / height() | 2 / 3 |
| validated.mediaType() | image/png |
| ImageIO.read(validated.bytes) 的结果 | 可解码图片 |
| 非图片异常 code | MEDIA_REJECTED |

## D2 上传、二维码暂存与失败补偿

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MediaServiceTest#attaches_a_normalized_image_only_to_an_editable_draft_and_audits_the_minimum_event`
- `MediaServiceTest#action_qr_upload_uses_a_reservation_instead_of_revision_media`
- `MediaServiceTest#action_media_cannot_be_reused_by_another_content_or_account`
- `MediaRollbackDebugProbeTest#jdbc_failure_without_synchronization_deletes_immediately`
- `MediaRollbackDebugProbeTest#rollback_callback_deletes_uploaded_object`
- `MediaRollbackDebugProbeTest#commit_callback_keeps_uploaded_object`

1. 先 Debug 正文上传方法，跟到 put 前，确认归属/状态/配额检查已经完成；F8 记录 put、两次写库、审计的顺序。
2. Debug 二维码例，比较 reservations 与 revision_media 两种写入；归属错误例应在 requireActionMedia 抛 CONTENT_ACTION_MEDIA_INVALID。
3. 安装探针，Debug 无同步失败例，JDBC 抛错后 catch 立即请求 storage.delete，原异常继续抛出。
4. Debug rollback 例：upload 返回后尚未 delete；在 callbacks.get(0).afterCompletion(status) 行 F7，观察 status=1 后 delete 被调用。
5. 换 commit 例 status=0，delete 不调用；finally 清理线程同步状态，每次重跑用新 Debug 进程。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 正文上传主顺序 | 校验→put→media_assets→revision_media→audit |
| 二维码暂存表 | content_action_media_reservations |
| 无同步失败 / 回滚完成后 delete 次数 | 各 1 |
| 提交完成后 delete 次数 | 0 |

## D3 公开、社群和后台媒体读取

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MediaReadServiceTest#public_action_qr_requires_an_open_published_public_entry`
- `MediaReadServiceTest#community_action_qr_stays_on_the_gated_path_and_still_requires_an_open_item`
- `MediaReadServiceTest#managed_preview_includes_attached_action_images_after_reservation_expiry`
- `MediaReadServiceTest#project_join_public_qr_is_readable_through_the_public_media_path`
- `MediaReadServiceTest#project_join_gated_qr_stays_on_the_community_path`

1. 依次 Debug 五个方法，在对应 load 方法入口停住，然后 F8 到 JDBC 调用；不要进入 Mockito。
2. 返回测试帧，先 F8 执行 verify(...sql.capture())，再在下一条断言处用 sql.getValue()。捕获前求值会失败。
3. 公开路径逐项查 ca.access_level、published_revision、OPEN、visibility、record_state，以及社群入口分支的 ce.access_level；这些属于不同 EXISTS/OR 分支，不能当作所有记录必须同时具备两个入口。
4. 社群例只要求不含 ca.access_level = PUBLIC；项目门禁例的不含条件限定为 pje.access_level；不要扩大成整个 SQL 不能含 PUBLIC。
5. 后台例看 current_revision 与 reservations 两个路径，最后 F9 完成断言。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 公开 ca 分支 | PUBLIC + 已发布修订 + OPEN + PUBLIC 可见 + ACTIVE |
| 社群 ca 分支 | COMMUNITY_GATED，仍要求 OPEN/已发布 |
| 后台路径 | current_revision 与暂存 reservations |
| 项目公开 / 社群路径 | 公开含 pje.access_level；社群测试不含该过滤 |

## D4 孤儿清理与租约

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MediaOrphanCleanupServiceTest#queues_before_deleting_from_private_storage`
- `MediaOrphanCleanupServiceTest#failed_storage_deletion_stays_retryable`
- `MediaOrphanCleanupServiceTest#each_cleanup_invocation_uses_a_distinct_claim_id`

1. Debug queues_before 方法，queueUnattachedAssets 先标记 DELETION_PENDING 再登记任务；记录 JDBC 请求顺序。
2. 进入 processDueDeletions 看 claim，再跟到 storage.delete、媒体 DELETED 更新和任务 SUCCEEDED 更新。
3. Debug failed_storage 方法，delete 抛异常后记 FAILED 与 removed=0；这表示以后可重试，不是本次已删除。
4. Debug distinct_claim 方法，在 claims.capture 完成后观察两个不同 claim。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 成功 removed | 1 |
| 失败 removed | 0 |
| 成功 SQL 状态顺序 | DELETION_PENDING → SUCCEEDED / DELETED |
| 失败任务状态 | FAILED，可重试 |

## D5 正文媒体引用、富文本与缓存

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MarkdownDocumentServiceTest#builds_a_safe_document_with_inline_media`
- `MarkdownDocumentServiceTest#rejects_html_and_remote_images`
- `RichTextPolicyTest#rejects_html_or_event_attribute_shapes`
- `PublicMediaControllerTest#refuses_to_cache_public_media_so_an_emergency_hide_takes_effect_on_the_next_request`

1. Debug Markdown 成功例，在 parse 返回后观察 document.version=2；到 mediaIds 返回看唯一 ID。
2. Debug rejects_html_and_remote_images，逐次到 ApiException，区分 HTML 与远程图片两个拒绝输入。
3. Debug 富文本非法形状用例，观察 html 块与 onClick 被拒绝。
4. Debug 控制器缓存例，在构造响应处与测试断言处暂停，记录 no-store；该例不证明 Service 授权 SQL。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 测试帧 document.path("version").asInt() | 2 |
| markdown.mediaIds(document) | 一个合成 ULID |
| HTML / 远程图片 / 事件字段 | 拒绝 |
| 媒体响应缓存头 | no-store |
