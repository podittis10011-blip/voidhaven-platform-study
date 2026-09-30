# VH02 单步脚本

每站先在 [观察表](04-observation-sheets.md) 写预测。以下预期来自源码/断言，交付方执行状态见 [台账](08-evidence-ledger.md)；不等于你已观察。

## D1 状态机与公开指针

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentItemStateTest#publishing_makes_an_approved_item_public_and_active`
- `ContentItemStateTest#archiving_hides_the_public_projection`
- `ContentItemStateTest#closing_a_published_item_keeps_its_candidate_workflow_intact`
- `ContentItemStateTest#restoring_an_archived_item_requires_a_new_publish_decision`

1. Debug 第一个方法，在 publish 行执行前记录四维状态；方法压在一行，F8 会一次执行整行，返回测试帧再比较。
2. 依次 Debug 归档、关闭、恢复方法。关闭用例先执行 beginCandidateRevision，再进入 closePublic；关注工作流与发布状态互不替代。
3. 在恢复用例最后一个断言处暂停，观察 HIDDEN、APPROVED、NONE；F9 跑完所有断言。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 测试帧 item.getWorkflowStatus()，发布后 | PUBLISHED |
| 测试帧 item.getVisibility()，归档后 | HIDDEN |
| 测试帧 item.getWorkflowStatus()，候选关闭后 | DRAFT |
| 测试帧 item.getPublicationStatus()，恢复后 | NONE |

## D2 revise 的两条分叉

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentManagementServiceReviseProbeTest#revising_a_published_item_creates_a_candidate_and_keeps_the_published_pointer`
- `ContentManagementServiceReviseProbeTest#revising_a_draft_updates_the_same_revision_row`

1. 先安装探针。Debug 已发布方法，停在 createCandidate 赋值前；看 item.getWorkflowStatus()，F8 后看 createCandidate。
2. F8 跟到 saveAndFlush；不要 F7 进入 Mockito。跨过保存后看 revision.getRevisionNumber() 与 previous.getId()。
3. 停在 setCurrentRevision 前后比较两个指针，回测试帧看 candidate 与 published。F9 完成审计验证。
4. 另起 Debug 草稿方法，确认命中 revision = previous；比较对象身份和编号，最后公开指针仍为 null。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| revise 帧 createCandidate，赋值后（发布/草稿） | true / false |
| 测试帧 candidate.getRevisionNumber()，已发布用例 | 2 |
| 测试帧 item.getPublishedRevision() == published | true |
| 草稿测试帧 saved.getValue() == item.getCurrentRevision() | true；编号 1 |

## D3 版本与不可编辑状态

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentManagementServiceReviseProbeTest#revise_rejects_a_stale_expected_version`
- `ContentManagementServiceReviseProbeTest#revise_rejects_a_non_editable_workflow_status`

1. 打开 ApiException 的 caught 异常断点；Debug 过期版本用例，比较 expectedVersion 与 item.getVersion()。
2. F8 执行版本判断，在异常处观察错误码，再 F9 让 assertThatThrownBy 接住它；红色异常断点并不表示测试失败。
3. 换状态用例；版本检查通过后，到工作流白名单停住，确认拒绝发生在 createCandidate 之前。
4. 回测试方法看 never().saveAndFlush 验证；不要把 mock 的 version 当作数据库自动递增结果。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| revise 帧 expectedVersion / item.getVersion() | 8 / 9 |
| 过期版本异常 code | CONTENT_VERSION_CONFLICT |
| 状态用例 item.getWorkflowStatus() | PENDING_REVIEW |
| 状态用例异常 code / 修订保存次数 | CONTENT_NOT_EDITABLE / 0 |

## D4 类型投影与原始标题校验

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentManagementServiceTest#persists_resource_type_in_its_typed_detail_table`
- `ContentManagementServiceTest#rejects_non_chinese_content_without_an_original_title_before_it_can_enter_review`

1. Debug 成功用例，进入 writeTypedDetails，观察 RESOURCE 分支构造的 SQL 与 GUIDE 参数。
2. 返回测试帧，在 verify(jdbc).update 前暂停；F8 验证写入目标是 resource_revision_details。
3. Debug 非中文缺标题用例，在 ORIGINAL_TITLE_REQUIRED 抛出位置停住，往上看调用栈属于 create 的校验链。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 输入 draft 的 kind / language | RESOURCE / zh-CN，拒绝例 en |
| 成功 SQL 表名 | resource_revision_details |
| 成功写入 resourceType 参数 | GUIDE |
| 缺原始标题异常 code | ORIGINAL_TITLE_REQUIRED |

## D5 克隆取历史最大修订号

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentRevisionCandidateServiceTest#clone_copies_revision_projections_and_keeps_the_published_revision`
- `ContentRevisionCandidateServiceTest#clone_uses_the_highest_existing_revision_number`

1. Debug 用例一，在 max 查询语句前记录旧修订号，F8 越过 mock 查询后观察下一编号。
2. 逐个 F8 越过 insert-select 调用；在测试断言区核对明细、筛选、关系、媒体及行动入口的 verify。
3. 到 setCurrentRevision 后返回测试帧，看 candidate 与 published 指针。
4. 换用例二验证 max=5 得到 6，不能用旧公开修订号 2+1 替代。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 用例一 candidate.getRevisionNumber() | 8 |
| 用例二 candidate.getRevisionNumber() | 6 |
| item.getPublishedRevision() == published | true |
| item.getCurrentRevision() == candidate | true；工作流 DRAFT |

## D6 固定时刻驱动到期关闭

**选择入口**：按下面顺序每次只 Debug 一个方法；源文件位置见 [地图](01-debug-walkthrough.md)。

- `ContentLifecycleServiceTest#closes_a_competition_from_its_typed_deadline_not_the_json_projection`

1. Debug 指定方法，在 run 入口观察传入 now；不要使用电脑当天日期解释测试。
2. F8 越过列表与逐条锁定查询，查看类型详情返回的日期；JSON 为空但仍有 deadline_date。
3. 停在 closePublic 前记 ACTIVE，执行后记 CLOSED；返回测试帧核对审计理由“截止日期已到”。

**停不住时**：核对本节夹具是否执行了目标分支；不要临时换成另一个同名 Service 测试。尚未安装探针时先安装。

**通过判据**：观察表各项与下列预期一致，最后 F9 使当前用例正常结束。

| 观察表达式 / 位置 | 预期 |
|---|---|
| 测试帧 detail.get("deadline_date") | 2026-09-02 |
| run 入参 now | 2026-09-03T00:00:00Z |
| 测试帧 item.getPublicationStatus() | CLOSED |
| 审计动作 / 原因 | CONTENT_CLOSED / 截止日期已到 |
