# VH04 断点与夹具地图

先读 [README](README.md) 完成环境和探针安装，再配合 [逐站脚本](03-step-runbook.md)。所有路径相对学习仓库根目录。表中行号已对照当前源码；声明行不可停时选下一条可执行语句。

## D1 幂等拒绝、失败释放与回放

**夹具与输入**：reservations 为 mock：在途 Existing 的状态码 102；异体请求 hash 不同；业务失败例 reserve=null；敏感结果不可重放；最后一例回放类型化首页记录。

| 精确驱动方法 | 文件位置 |
|---|---|
| `IdempotencyServiceTest#rejects_a_matching_request_while_the_first_request_owns_the_key` | `apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:26` |
| `IdempotencyServiceTest#rejects_reusing_a_key_for_a_different_payload` | `apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:38` |
| `IdempotencyServiceTest#releases_the_reservation_when_the_business_action_fails` | `apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:47` |
| `IdempotencyServiceTest#refuses_to_replay_a_sensitive_single_delivery_result` | `apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:57` |
| `IdempotencyServiceTest#restores_homepage_replacement_as_typed_records_instead_of_raw_lists` | `apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:66` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D1-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java:41` | `if (existing != null) return restore` |
| D1-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java:111` | `private <T> T restore(` |
| D1-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java:57` | `reservations.release(` |

**证据边界**：本测试从 service 开始，没有限流拦截器或参数解析。REQUIRES_NEW 代理、锁竞争不能在此证实。

## D2 六个聚合的版本冲突

**夹具与输入**：按每个用例的 when 行读取数据库版本与请求版本。内容 9/8；项目入口 3/2；回执用 JDBC 更新返回 0 模拟竞争。不要给六类套同一个实现。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentManagementServiceTest#refuses_to_review_a_stale_item_version` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceTest.java:62` |
| `CommunityServiceTest#update_rejects_stale_versions` | `apps/api/src/test/java/cn/voidhaven/api/service/CommunityServiceTest.java:22` |
| `CatalogOrderServiceTest#rejects_stale_version_and_duplicate_content` | `apps/api/src/test/java/cn/voidhaven/api/service/CatalogOrderServiceTest.java:24` |
| `HomepageAdministrationServiceTest#rejects_stale_curation_version` | `apps/api/src/test/java/cn/voidhaven/api/service/HomepageAdministrationServiceTest.java:62` |
| `ProjectJoinServiceTest#update_rejects_stale_versions` | `apps/api/src/test/java/cn/voidhaven/api/service/ProjectJoinServiceTest.java:53` |
| `PublicCaseAdministrationServiceTest#a_stale_version_does_not_write_status_history` | `apps/api/src/test/java/cn/voidhaven/api/service/PublicCaseAdministrationServiceTest.java:100` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D2-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:347` | `public Managed decide(` |
| D2-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/CommunityService.java:150` | `COMMUNITY_ENTRY_VERSION_CONFLICT` |
| D2-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/CatalogOrderService.java:78` | `CATALOG_VERSION_CONFLICT` |
| D2-B4 | `apps/api/src/main/java/cn/voidhaven/api/service/HomepageAdministrationService.java:84` | `HOMEPAGE_VERSION_CONFLICT` |
| D2-B5 | `apps/api/src/main/java/cn/voidhaven/api/service/ProjectJoinService.java:102` | `PROJECT_JOIN_ENTRY_VERSION_CONFLICT` |
| D2-B6 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseAdministrationService.java:71` | `if (updated != 1)` |

**证据边界**：直接调用服务没有 HTTP If-Match 解析；mock 的 for update 字符串也没有真实锁。真实锁位置需逐实现核对。

## D3 三个限流委托

**夹具与输入**：公开写额度=2，第三次失败由 mock 序列注入；查询额度=60；登录测试只调用完成方法。

| 精确驱动方法 | 文件位置 |
|---|---|
| `PublicWriteRateLimiterTest#limits_a_hashed_network_subject_without_storing_the_raw_value` | `apps/api/src/test/java/cn/voidhaven/api/service/PublicWriteRateLimiterTest.java:19` |
| `PublicStatusQueryRateLimiterTest#uses_a_separate_status_query_bucket` | `apps/api/src/test/java/cn/voidhaven/api/service/PublicStatusQueryRateLimiterTest.java:17` |
| `AuthenticationRateLimiterTest#delegates_login_completion_to_the_same_subject_lock_transaction` | `apps/api/src/test/java/cn/voidhaven/api/service/AuthenticationRateLimiterTest.java:9` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D3-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicWriteRateLimiter.java:36` | `public void requireAvailable(` |
| D3-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicStatusQueryRateLimiter.java:38` | `public void requireAvailable(` |
| D3-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/AuthenticationRateLimiter.java:60` | `public void loginSucceeded(` |

**证据边界**：可证明主体转换和桶方法选择，不能证明真实计数或隔离事务；D5 再查真库。

## D4 删除任务租约与重试

**夹具与输入**：第一例提供一个孤儿与一个任务，完成更新返回 1；第二例 storage.delete 抛 synthetic 错误；第三例返回空任务列表并捕获两次 claim。

| 精确驱动方法 | 文件位置 |
|---|---|
| `MediaOrphanCleanupServiceTest#queues_before_deleting_from_private_storage` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:14` |
| `MediaOrphanCleanupServiceTest#failed_storage_deletion_stays_retryable` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:33` |
| `MediaOrphanCleanupServiceTest#each_cleanup_invocation_uses_a_distinct_claim_id` | `apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:47` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D4-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:46` | `public int processDueDeletions()` |
| D4-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:64` | `storage.delete(` |
| D4-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:67` | `if (updated == 1) completed++;` |

**证据边界**：租约校验的真实效果在 D5；单测不能直接改“数据库 locked_by 行”，模拟失去租约应改 SUCCEEDED 更新返回值。

## D5 真实 MySQL 并发（环境门槛）

**夹具与输入**：测试创建独立 mysql:8.4 容器和测试库，Flyway 迁移；8 个线程用 CyclicBarrier 同步。前提是 Docker 引擎与镜像可用。

| 精确驱动方法 | 文件位置 |
|---|---|
| `DistributedSafetyIT#serializes_rate_limits_advisory_locks_and_media_leases` | `apps/api/src/test/java/cn/voidhaven/api/integration/DistributedSafetyIT.java:39` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D5-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/RateLimitLockService.java:36` | `reservePublicWrite(` |
| D5-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:46` | `public int processDueDeletions()` |

**证据边界**：本轮不启动 Docker，不把该 IT 当作通过。这里的服务/事务管理器由测试构造，也不能自动证明幂等 Spring 代理的独立事务语义。
