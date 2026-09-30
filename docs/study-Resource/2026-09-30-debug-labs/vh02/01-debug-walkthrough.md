# VH02 断点与夹具地图

先读 [README](README.md) 完成环境和探针安装，再配合 [逐站脚本](03-step-runbook.md)。所有路径相对学习仓库根目录。表中行号已对照当前源码；声明行不可停时选下一条可执行语句。

## D1 状态机与公开指针

**夹具与输入**：四个现有用例各自 new ContentItem；关闭用例在发布后开启候选。每个方法独立 Debug，不能依赖上个用例的对象。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentItemStateTest#publishing_makes_an_approved_item_public_and_active` | `apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java:8` |
| `ContentItemStateTest#archiving_hides_the_public_projection` | `apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java:15` |
| `ContentItemStateTest#closing_a_published_item_keeps_its_candidate_workflow_intact` | `apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java:21` |
| `ContentItemStateTest#restoring_an_archived_item_requires_a_new_publish_decision` | `apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java:33` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D1-B1 | `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:178` | `public void publish()` |
| D1-B2 | `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:179` | `public void closePublic(` |
| D1-B3 | `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:182` | `public void archive()` |
| D1-B4 | `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:183` | `public void restore()` |

**证据边界**：实体内存变化；不证明权限、数据库 @Version 递增、发布接口全流程。touch() 还会更新时间，不能说动作只改表中列出的业务字段。

## D2 revise 的两条分叉

**夹具与输入**：探针 C：实体 version=1，当前修订号=1。publishedItem() 使公开/当前指针相同；draftItem() 的公开指针为 null。仓储 saveAndFlush 原样返回传入对象。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentManagementServiceReviseProbeTest#revising_a_published_item_creates_a_candidate_and_keeps_the_published_pointer` | `docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:57` |
| `ContentManagementServiceReviseProbeTest#revising_a_draft_updates_the_same_revision_row` | `docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:85` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D2-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:294` | `boolean createCandidate =` |
| D2-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:311` | `revision = previous;` |
| D2-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:214` | `item.setCurrentRevision(revision);` |
| D2-B4 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:325` | `item.beginCandidateRevision();` |

**证据边界**：新建/复用是对象与仓储调用证据，不是真实 INSERT/UPDATE 已执行；源代码中的行锁调用不等于 mock 获得真实锁。

## D3 版本与不可编辑状态

**夹具与输入**：过期版本用例实体 9L、请求 8L；状态用例实体 1L、请求 1L，但 item.submit() 将状态改为 PENDING_REVIEW。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentManagementServiceReviseProbeTest#revise_rejects_a_stale_expected_version` | `docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:104` |
| `ContentManagementServiceReviseProbeTest#revise_rejects_a_non_editable_workflow_status` | `docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:120` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D3-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:279` | `if (expectedVersion == null` |
| D3-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:282` | `require(item.getRecordState()` |
| D3-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:248` | `require(item.getWorkflowStatus() == WorkflowStatus.DRAFT` |

**证据边界**：ARCHIVED 分支可读源码，但当前两个用例未驱动，不登记为断点已验证。

## D4 类型投影与原始标题校验

**夹具与输入**：成功输入 RESOURCE、zh-CN、resourceType=GUIDE；拒绝输入 en、originalTitle=null。JsonMapper 与策略对象真实运行，JDBC/仓储模拟。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentManagementServiceTest#persists_resource_type_in_its_typed_detail_table` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceTest.java:28` |
| `ContentManagementServiceTest#rejects_non_chinese_content_without_an_original_title_before_it_can_enter_review` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceTest.java:47` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D4-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:667` | `private void writeTypedDetails` |
| D4-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:626` | `ORIGINAL_TITLE_REQUIRED` |

**证据边界**：仅验证投影写入请求，数据库表结构、约束与事务需真实栈。

## D5 克隆取历史最大修订号

**夹具与输入**：用例一当前修订 3、模拟 max=7；用例二当前修订 2、模拟 max=5。仓储保存原样返回新对象。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentRevisionCandidateServiceTest#clone_copies_revision_projections_and_keeps_the_published_revision` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentRevisionCandidateServiceTest.java:29` |
| `ContentRevisionCandidateServiceTest#clone_uses_the_highest_existing_revision_number` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentRevisionCandidateServiceTest.java:80` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D5-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentRevisionCandidateService.java:23` | `cloneCurrent(` |
| D5-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentRevisionCandidateService.java:26` | `select coalesce(max(revision_number)` |
| D5-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentRevisionCandidateService.java:51` | `item.setCurrentRevision(` |

**证据边界**：缺号本身不一定冲突；关键是当前指针可能指向较早修订，而更大编号已存在。唯一约束竞争未由该单测验证。

## D6 固定时刻驱动到期关闭

**夹具与输入**：已发布竞赛；detailsJson={}；模拟类型表 deadline_at=null、deadline_date=2026-09-02；run 的 now=2026-09-03T00:00:00Z。

| 精确驱动方法 | 文件位置 |
|---|---|
| `ContentLifecycleServiceTest#closes_a_competition_from_its_typed_deadline_not_the_json_projection` | `apps/api/src/test/java/cn/voidhaven/api/service/ContentLifecycleServiceTest.java:19` |

| 断点 | 定位 | 锚点 |
|---|---|---|
| D6-B1 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentLifecycleService.java:60` | `findTimedPublicItems()` |
| D6-B2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentLifecycleService.java:71` | `findByIdForUpdate(` |
| D6-B3 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentLifecycleService.java:74` | `item.closePublic(` |

**证据边界**：不证明定时调度、数据库咨询锁和多实例互斥。
