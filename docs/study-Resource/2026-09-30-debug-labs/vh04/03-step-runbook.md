# VH04 单步脚本

每站先在 [观察表](04-observation-sheets.md) 写预测。以下预期来自源码/断言，交付方执行状态见 [台账](08-evidence-ledger.md)；不等于你已观察。

## D1 幂等拒绝、失败释放与回放

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `IdempotencyServiceTest#rejects_a_matching_request_while_the_first_request_owns_the_key`
- `IdempotencyServiceTest#rejects_reusing_a_key_for_a_different_payload`
- `IdempotencyServiceTest#releases_the_reservation_when_the_business_action_fails`
- `IdempotencyServiceTest#refuses_to_replay_a_sensitive_single_delivery_result`
- `IdempotencyServiceTest#restores_homepage_replacement_as_typed_records_instead_of_raw_lists`

1. 依次 Debug 五个方法；先在 execute 的 reserve 返回后观察 existing，再 F7 进 restore。
2. 在途例看 responseStatus=102；F9 回到测试断言区观察 actions.get()==0。
3. 异体例先比较 hash，再看错误码；失败例在 action.get() 抛错后跟到 catch/release。
4. 敏感例从 executeSingleDelivery 进入，观察不可回放拒绝；最后的类型化回放例检查返回记录类型，不重新执行 action。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 在途测试 actions.get() | 0 |
| 异体异常 code | IDEMPOTENCY_KEY_REUSED |
| 业务失败后 release 调用次数 | 1 |
| 敏感例异常 / 回放例对象 | SENSITIVE_RESULT_NOT_REPLAYABLE / 类型化 record |

## D2 六个聚合的版本冲突

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentManagementServiceTest#refuses_to_review_a_stale_item_version`
- `CommunityServiceTest#update_rejects_stale_versions`
- `CatalogOrderServiceTest#rejects_stale_version_and_duplicate_content`
- `HomepageAdministrationServiceTest#rejects_stale_curation_version`
- `ProjectJoinServiceTest#update_rejects_stale_versions`
- `PublicCaseAdministrationServiceTest#a_stale_version_does_not_write_status_history`

1. 逐个 Debug 六个方法，仅启用当前聚合的断点和 ApiException caught 断点。
2. 内容审核例由 findWithCurrentRevisionById 读取后比较；项目入口从 for update 读取后比较；在各自帧记录 expectedVersion。
3. 回执例先执行带 where version=? 的更新，再看 updated=0 触发冲突；这是影响行数判定，不是统一的 entity.getVersion 比较。
4. 返回测试帧完成异常断言；回执还验证不写 public_case_status_history。将六个结果分别记入观察表。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 内容冲突 code | CONTENT_VERSION_CONFLICT |
| 社群 / 目录冲突 code | COMMUNITY_ENTRY_VERSION_CONFLICT / CATALOG_VERSION_CONFLICT |
| 首页 / 项目冲突 code | HOMEPAGE_VERSION_CONFLICT / PROJECT_JOIN_ENTRY_VERSION_CONFLICT |
| 回执 updated / code / 历史写入次数 | 0 / CASE_VERSION_CONFLICT / 0 |

## D3 三个限流委托

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `PublicWriteRateLimiterTest#limits_a_hashed_network_subject_without_storing_the_raw_value`
- `PublicStatusQueryRateLimiterTest#uses_a_separate_status_query_bucket`
- `AuthenticationRateLimiterTest#delegates_login_completion_to_the_same_subject_lock_transaction`

1. Debug 公开写方法，在 requireAvailable 看原主体与转换后传给 reservePublicWrite 的参数；连按 F9 观察三次调用。
2. 第三次异常来自 mock RateLimitLockService，不是内存计数器计算。
3. Debug 查询用例，回测试帧检查 reservePublicStatusQuery(...,60,Duration)；再 Debug 登录例看 true/false。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 公开写输入主体 | 203.0.113.55 |
| 公开写锁服务参数 | 主体摘要、2、1 小时 |
| 查询委托方法 / 额度 | reservePublicStatusQuery / 60 |
| 登录完成方法 | completeLoginAttempt |

## D4 删除任务租约与重试

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `MediaOrphanCleanupServiceTest#queues_before_deleting_from_private_storage`
- `MediaOrphanCleanupServiceTest#failed_storage_deletion_stays_retryable`
- `MediaOrphanCleanupServiceTest#each_cleanup_invocation_uses_a_distinct_claim_id`

1. Debug 第一例，跟进 queueUnattachedAssets 再 processDueDeletions，观察先登记任务再删除存储。
2. 在 SUCCEEDED 更新后看 updated=1，completed 加 1；记录 SQL 的 locked_by 条件，而不是假装 SQL 已执行。
3. Debug 失败例，在 storage.delete 异常后跟到 FAILED 更新，回测试帧看 removed=0。
4. Debug 第三例，在最后断言看 claims.getAllValues()：同 instance-a 前缀、每次调用不同 claim。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 成功测试 removed | 1 |
| 失败测试 removed | 0 |
| 失败写入任务状态 | FAILED，携带异常类名 |
| 第三例 claims.getAllValues() | 两个不同的 instance-a: 开头值 |

## D5 真实 MySQL 并发（环境门槛）

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `DistributedSafetyIT#serializes_rate_limits_advisory_locks_and_media_leases`

1. 先检查环境；未具备 Docker 则在台账写 BLOCKED_ENV，不要为凑数将本站勾成完成。执行本章专用 IT 命令，读 failsafe XML 的 skipped。
2. 在 IT 的 Assumptions.assumeTrue 行打断点，Debug 精确方法；通过后只在 Future.get 全部结束之后的断言处暂停，避免把工作线程停在 barrier/锁内造成超时。
3. 记录公开写 allowed=2、limited=6，再对照查询桶第三次拒绝、登录 5/3、邀请码 2/6。
4. 继续到咨询锁、租约、唯一约束断言。实际看见的结果才填观察表；真库测试也不覆盖整个 HTTP 过滤链。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 公开写 / 登录 并发允许-拒绝 | 2-6 / 5-3 |
| 查询超额 / 写桶影响 | PUBLIC_STATUS_QUERY_RATE_LIMITED / 不增加对应公开写记录 |
| 邀请码成功数 / use_count | 2 / 2 |
| 咨询锁 / 租约 / login_id 唯一竞争 | 第二执行者拒绝 / A 持有 / 1 成功 1 冲突 |
