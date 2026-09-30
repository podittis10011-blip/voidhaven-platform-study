# VH06 断点与夹具地图

先读 [README](README.md) 完成环境和探针安装，再配合 [逐站脚本](03-step-runbook.md)。所有路径相对学习仓库根目录。表中行号已对照当前源码；声明行不可停时选下一条可执行语句。

## D1 公开写拦截器与主体

**夹具与输入**：探针 mock 两个限流器和 network，MockHttp 请求真实。主体测试另用真实 ClientNetworkSubject；不要把探针固定返回的地址当作代理解析结果。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PublicWriteInterceptorDebugProbeTest#post_submission_uses_write_bucket` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicWriteInterceptorDebugProbeTest.java:19` |
| `PublicWriteInterceptorDebugProbeTest#get_does_not_consume_either_bucket` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicWriteInterceptorDebugProbeTest.java:34` |
| `PublicWriteInterceptorDebugProbeTest#limiter_failure_propagates_before_prehandle_returns` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicWriteInterceptorDebugProbeTest.java:39` |
| `ClientNetworkSubjectTest#ignores_forwarded_address_when_direct_peer_is_not_a_trusted_proxy` | `apps/api/src/test/java/cn/voidhaven/api/web/ClientNetworkSubjectTest.java:10` |
| `ClientNetworkSubjectTest#accepts_only_a_single_numeric_address_from_a_trusted_proxy` | `apps/api/src/test/java/cn/voidhaven/api/web/ClientNetworkSubjectTest.java:20` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D1-B1 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java:54` | `if (!"POST".equals(request.getMethod()))` |
| D1-B2 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java:58` | `if (WRITE_PATHS.contains(path))` |
| D1-B3 | `apps/api/src/main/java/cn/voidhaven/api/web/ClientNetworkSubject.java:24` | `public String resolve(` |

**证据边界**：手工 preHandle 仅证明分派及异常传播；MVC 顺序、真实额度消耗、Retry-After、最终 HTTP 429 需真实栈或专门 MVC 测试。

## D2 查询桶与公开写桶分离

**夹具与输入**：POST /api/v1/public/submissions/status-query；限流服务测试构造额度 60、窗口 3600 秒。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PublicWriteInterceptorDebugProbeTest#post_status_uses_query_bucket` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicWriteInterceptorDebugProbeTest.java:27` |
| `PublicStatusQueryRateLimiterTest#uses_a_separate_status_query_bucket` | `apps/api/src/test/java/cn/voidhaven/api/service/PublicStatusQueryRateLimiterTest.java:17` |
| `PublicStatusQueryRateLimiterTest#surfaces_status_query_limit_without_consuming_write_quota` | `apps/api/src/test/java/cn/voidhaven/api/service/PublicStatusQueryRateLimiterTest.java:25` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D2-B1 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java:60` | `else if (STATUS_QUERY_PATHS.contains(path))` |
| D2-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicStatusQueryRateLimiter.java:38` | `public void requireAvailable(` |

**证据边界**：委托隔离不等于真实数据库计数隔离；真库见 VH04 D5。

## D3 回执生成、哈希存储与查询

**夹具与输入**：RecordingJdbc 捕获 SQL 参数；幂等 mock 的 Answer 真正调用 Supplier.get；无联系人的 FEEDBACK。状态查询行由已捕获 hash 构造。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PublicCaseDebugProbeTest#creates_receipt_stores_only_hash_and_queries_it` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicCaseDebugProbeTest.java:41` |
| `PublicCaseDebugProbeTest#wrong_blank_and_missing_receipts_share_one_error_code` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicCaseDebugProbeTest.java:55` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D3-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseService.java:83` | `protected CreatedCase createSubmission(` |
| D3-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseService.java:85` | `String queryCode = SecureTokens.randomUrlToken();` |
| D3-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseService.java:121` | `if (!SecureTokens.constantTimeEquals(` |
| D3-B4 | `apps/api/src/main/java/cn/voidhaven/api/support/SecureTokens.java:40` | `public static boolean constantTimeEquals(` |

**证据边界**：16～256 是请求校验允许长度范围，不是本实现生成长度。探针执行回调但不验证真实幂等预约、SQL 或 Spring 事务代理。

## D4 公开投影与 grant 委托

**夹具与输入**：JDBC 查询 mock 返回空列表；有 grant 例 community.requireGrant 也是 mock 放行，不是真实有效凭据。加密例捕获上下文并返回合成密文。

| 精确驱动方法 | 文件位置 |
|---|---|
| `CommunityServiceTest#public_listing_without_a_grant_only_loads_public_entries` | `apps/api/src/test/java/cn/voidhaven/api/service/CommunityServiceTest.java:87` |
| `ProjectJoinServiceTest#public_listing_without_a_grant_only_loads_public_entries` | `apps/api/src/test/java/cn/voidhaven/api/service/ProjectJoinServiceTest.java:24` |
| `ProjectJoinServiceTest#public_listing_with_a_grant_reads_controlled_entries_through_the_shared_invite` | `apps/api/src/test/java/cn/voidhaven/api/service/ProjectJoinServiceTest.java:39` |
| `ProjectJoinServiceTest#controlled_values_are_encrypted_with_the_project_join_context` | `apps/api/src/test/java/cn/voidhaven/api/service/ProjectJoinServiceTest.java:77` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D4-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ProjectJoinService.java:48` | `publicEntries(` |
| D4-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ProjectJoinService.java:50` | `community.requireGrant(` |
| D4-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/CommunityService.java:99` | `access_level = 'PUBLIC'` |

**证据边界**：SQL 形状不证明真实数据库已过滤；grant 签发、校验、撤销全链仍需真实栈。

## D5 真实解密与查看审计

**夹具与输入**：使用仅限测试的 32 字节合成密钥和真实 AES-GCM；JDBC 返回加密后的合成联系人；AuditService mock。错 AAD 与空记录单独测试。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PublicContactRevealDebugProbeTest#reveals_with_matching_aad_and_records_minimal_audit` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicContactRevealDebugProbeTest.java:25` |
| `PublicContactRevealDebugProbeTest#wrong_aad_prevents_reveal_and_audit` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicContactRevealDebugProbeTest.java:32` |
| `PublicContactRevealDebugProbeTest#missing_contact_prevents_reveal_and_audit` | `docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicContactRevealDebugProbeTest.java:38` |
| `PrivateValueCipherTest#encrypts_and_decrypts_with_aad_and_never_returns_plaintext_as_ciphertext` | `apps/api/src/test/java/cn/voidhaven/api/service/PrivateValueCipherTest.java:35` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D5-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseAdministrationService.java:80` | `private ContactView reveal(` |
| D5-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseAdministrationService.java:84` | `String contact = privateValues.decrypt(` |
| D5-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseAdministrationService.java:85` | `"PRIVATE_CONTACT_REVEALED"` |
| D5-B4 | `apps/api/src/main/java/cn/voidhaven/api/service/PrivateValueCipher.java:86` | `public String decrypt(` |

**证据边界**：真实密码学 + 模拟仓储/审计；verify 证明发起审计调用，不证明数据库多了一行，也不证明 HTTP 权限。

## D6 人工投递与行动入口候选

**夹具与输入**：人工投递 mock JDBC 返回存在的合作申请/投稿；行动入口用已发布 item、模拟候选克隆器与加密器。没有发送邮件/消息。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ManualDeliveryServiceTest#records_activation_delivery_and_marks_partner_application` | `apps/api/src/test/java/cn/voidhaven/api/service/ManualDeliveryServiceTest.java:16` |
| `ManualDeliveryServiceTest#rejects_unknown_subject_or_type` | `apps/api/src/test/java/cn/voidhaven/api/service/ManualDeliveryServiceTest.java:32` |
| `ManualDeliveryServiceTest#records_submission_status_result_after_public_result_exists` | `apps/api/src/test/java/cn/voidhaven/api/service/ManualDeliveryServiceTest.java:40` |
| `ContentActionServiceTest#replacing_actions_on_published_content_writes_only_to_a_new_candidate` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentActionServiceTest.java:28` |
| `ContentActionServiceTest#review_states_cannot_replace_actions` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentActionServiceTest.java:64` |
| `ContentActionServiceTest#public_qr_without_confirmation_is_rejected_before_a_candidate_is_created` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentActionServiceTest.java:85` |
| `ContentActionServiceTest#link_and_email_cannot_be_combined` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentActionServiceTest.java:96` |
| `ContentActionServiceTest#gated_text_is_stored_as_ciphertext_and_omitted_from_audit` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentActionServiceTest.java:107` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D6-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ManualDeliveryService.java:31` | `public DeliveryRecord record(` |
| D6-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentActionService.java:84` | `public ActionSet replace(` |

**证据边界**：人工投递记录并不等于系统发送消息；现有测试只验证记录请求与业务约束。
