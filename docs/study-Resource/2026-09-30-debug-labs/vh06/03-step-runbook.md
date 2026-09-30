# VH06 单步脚本

每站先在 [观察表](04-observation-sheets.md) 写预测。以下预期来自源码/断言，交付方执行状态见 [台账](08-evidence-ledger.md)；不等于你已观察。

## D1 公开写拦截器与主体

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PublicWriteInterceptorDebugProbeTest#post_submission_uses_write_bucket`
- `PublicWriteInterceptorDebugProbeTest#get_does_not_consume_either_bucket`
- `PublicWriteInterceptorDebugProbeTest#limiter_failure_propagates_before_prehandle_returns`
- `ClientNetworkSubjectTest#ignores_forwarded_address_when_direct_peer_is_not_a_trusted_proxy`
- `ClientNetworkSubjectTest#accepts_only_a_single_numeric_address_from_a_trusted_proxy`

1. 安装 web 包探针，Debug POST 写入例，在 preHandle 看 method、URI；F8 到 writeLimiter，回测试核对 query 未调用。
2. Debug GET 例，在第一行直接返回 true，network 也不调用。
3. Debug 限流异常例，异常来自 mock；不经过 Controller，记录 ApiException code。
4. 另跑两条 ClientNetworkSubject 测试，在 resolve 入口看直连地址、转发头与受信配置，跟到返回。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 写入例 request.getMethod() / getRequestURI() | POST / /api/v1/public/submissions |
| 写入桶调用 / 查询桶调用 | 1 / 0 |
| GET 时任一限流桶调用 | 0 |
| mock 超额异常 code | PUBLIC_WRITE_RATE_LIMITED |

## D2 查询桶与公开写桶分离

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PublicWriteInterceptorDebugProbeTest#post_status_uses_query_bucket`
- `PublicStatusQueryRateLimiterTest#uses_a_separate_status_query_bucket`
- `PublicStatusQueryRateLimiterTest#surfaces_status_query_limit_without_consuming_write_quota`

1. Debug 探针 query 方法，进入 else-if 查询路径，返回测试 verify query 被调用且 write 不被调用。
2. Debug uses_a_separate 方法，看 requireAvailable 的摘要主体，再回测试查看 reservePublicStatusQuery 的额度 60。
3. Debug surfaces 方法，在锁服务 mock 抛错处和测试异常断言停住，记录查询专用错误码。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 路径 | /api/v1/public/submissions/status-query |
| 探针 write 调用次数 | 0 |
| 锁服务方法 / 额度 | reservePublicStatusQuery / 60 |
| 异常 code | PUBLIC_STATUS_QUERY_RATE_LIMITED |

## D3 回执生成、哈希存储与查询

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PublicCaseDebugProbeTest#creates_receipt_stores_only_hash_and_queries_it`
- `PublicCaseDebugProbeTest#wrong_blank_and_missing_receipts_share_one_error_code`

1. 安装探针，Debug 成功例；在 action.get 行 F7 进入创建回调，观察 id/queryCode 的生成。
2. F8 越过 RecordingJdbc.update，回测试帧看 jdbc.writes.get(0)[6]：64 位 hash，不是原查询码。
3. storedRow 后 F7 进入 submissionStatus→status→constantTimeEquals；看 SHA-256 后的两个值相等。
4. 返回状态 RECEIVED，再次查询仍成功。查询码只在创建时交付一次，但不是消费一次失效的激活令牌。
5. Debug 失败例，分别记录错误码、空码、空结果行三条路径；都应 CASE_QUERY_INVALID。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| created.id().length() / queryCode().length() | 26 / 43（32 随机字节 Base64URL 无填充） |
| jdbc.writes.get(0)[6] | SHA-256，64 字符 |
| status.status() / 第二次查询 | RECEIVED / 仍成功 |
| 三种失败 code | 均 CASE_QUERY_INVALID |

## D4 公开投影与 grant 委托

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `CommunityServiceTest#public_listing_without_a_grant_only_loads_public_entries`
- `ProjectJoinServiceTest#public_listing_without_a_grant_only_loads_public_entries`
- `ProjectJoinServiceTest#public_listing_with_a_grant_reads_controlled_entries_through_the_shared_invite`
- `ProjectJoinServiceTest#controlled_values_are_encrypted_with_the_project_join_context`

1. Debug 无 grant 两个方法，在 SQL 构建处和测试 verify 后停住，查看 sql.getValue() 中 PUBLIC 限制。
2. Debug 有 grant 方法，观察 shared-grant 被委托给 requireGrant，随后 SQL 不含 PUBLIC 限制。
3. 不能把 mock 放行说成 grant 有效性已证明；记录这一替身边界。
4. Debug 加密上下文例，在 context.capture 完成后查看 PROJECT_JOIN_ENTRY: 前缀。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 无 grant 项目 SQL | 包含 access_level = PUBLIC |
| 无 grant requireGrant 调用次数 | 0 |
| 有 grant SQL / 委托 | 无 PUBLIC 限制 / requireGrant(shared-grant) |
| 加密 AAD 前缀 | PROJECT_JOIN_ENTRY: |

## D5 真实解密与查看审计

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PublicContactRevealDebugProbeTest#reveals_with_matching_aad_and_records_minimal_audit`
- `PublicContactRevealDebugProbeTest#wrong_aad_prevents_reveal_and_audit`
- `PublicContactRevealDebugProbeTest#missing_contact_prevents_reveal_and_audit`
- `PrivateValueCipherTest#encrypts_and_decrypts_with_aad_and_never_returns_plaintext_as_ciphertext`

1. Debug 成功探针，row helper 用 SUBMISSION:ID 加密；进入 revealSubmissionContact→reveal。
2. 在 decrypt 前比对 subjectType+":"+id 与生成密文时 AAD；F8 后 contact 为合成明文。
3. 停在 audit.record，观察 before={}、after={revealed:true}，不写明文；回测试帧完成精确 verify。
4. Debug 错 AAD 与空记录用例，分别观察 PRIVATE_VALUE_DECRYPTION_FAILED 与 PRIVATE_CONTACT_NOT_FOUND，审计未调用。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| result.contact() | wechat:synthetic-only |
| 审计 action | PRIVATE_CONTACT_REVEALED |
| 审计 after JSON | {"revealed":true}，无联系值 |
| 错误 AAD / 空记录 | 解密失败 / 联系记录不存在；均无审计调用 |

## D6 人工投递与行动入口候选

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ManualDeliveryServiceTest#records_activation_delivery_and_marks_partner_application`
- `ManualDeliveryServiceTest#rejects_unknown_subject_or_type`
- `ManualDeliveryServiceTest#records_submission_status_result_after_public_result_exists`
- `ContentActionServiceTest#replacing_actions_on_published_content_writes_only_to_a_new_candidate`
- `ContentActionServiceTest#review_states_cannot_replace_actions`
- `ContentActionServiceTest#public_qr_without_confirmation_is_rejected_before_a_candidate_is_created`
- `ContentActionServiceTest#link_and_email_cannot_be_combined`
- `ContentActionServiceTest#gated_text_is_stored_as_ciphertext_and_omitted_from_audit`

1. Debug records_activation 方法，跟到 manual_delivery_records 插入、activation_delivered_at 更新、审计；三步分别记录。
2. Debug unknown 方法看 CONTENT 不是允许主体而拒绝；Debug status_result 方法看先有公开处理结果才能登记。
3. Debug replacing_actions 方法，比较原 published 与 candidate；写入的是候选修订，不修改公开指针。
4. 再运行四条行动入口拒绝/密文方法：记录审核状态、未确认公开二维码、LINK+EMAIL 冲突、受控文本不进入明文审计。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 人工投递审计 action | MANUAL_DELIVERY_RECORDED |
| 未知主体错误码 | MANUAL_DELIVERY_SUBJECT_INVALID |
| 行动入口写入修订 | 新的 candidate，published 保持原对象 |
| 受控文本存储 / 审计 | 密文 / 不含明文联系值 |
