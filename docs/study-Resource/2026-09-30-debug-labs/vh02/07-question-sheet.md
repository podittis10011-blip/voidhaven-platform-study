# VH02 闭卷题

先根据观察记录回答，再展开参考。每站都要能说明哪一部分是替身。

## D1 状态机与公开指针

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

测试帧 item.getWorkflowStatus()，发布后 → PUBLISHED；测试帧 item.getVisibility()，归档后 → HIDDEN；测试帧 item.getWorkflowStatus()，候选关闭后 → DRAFT；测试帧 item.getPublicationStatus()，恢复后 → NONE。

边界：实体内存变化；不证明权限、数据库 @Version 递增、发布接口全流程。touch() 还会更新时间，不能说动作只改表中列出的业务字段。

</details>

## D2 revise 的两条分叉

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

revise 帧 createCandidate，赋值后（发布/草稿） → true / false；测试帧 candidate.getRevisionNumber()，已发布用例 → 2；测试帧 item.getPublishedRevision() == published → true；草稿测试帧 saved.getValue() == item.getCurrentRevision() → true；编号 1。

边界：新建/复用是对象与仓储调用证据，不是真实 INSERT/UPDATE 已执行；源代码中的行锁调用不等于 mock 获得真实锁。

</details>

## D3 版本与不可编辑状态

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

revise 帧 expectedVersion / item.getVersion() → 8 / 9；过期版本异常 code → CONTENT_VERSION_CONFLICT；状态用例 item.getWorkflowStatus() → PENDING_REVIEW；状态用例异常 code / 修订保存次数 → CONTENT_NOT_EDITABLE / 0。

边界：ARCHIVED 分支可读源码，但当前两个用例未驱动，不登记为断点已验证。

</details>

## D4 类型投影与原始标题校验

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

输入 draft 的 kind / language → RESOURCE / zh-CN，拒绝例 en；成功 SQL 表名 → resource_revision_details；成功写入 resourceType 参数 → GUIDE；缺原始标题异常 code → ORIGINAL_TITLE_REQUIRED。

边界：仅验证投影写入请求，数据库表结构、约束与事务需真实栈。

</details>

## D5 克隆取历史最大修订号

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

用例一 candidate.getRevisionNumber() → 8；用例二 candidate.getRevisionNumber() → 6；item.getPublishedRevision() == published → true；item.getCurrentRevision() == candidate → true；工作流 DRAFT。

边界：缺号本身不一定冲突；关键是当前指针可能指向较早修订，而更大编号已存在。唯一约束竞争未由该单测验证。

</details>

## D6 固定时刻驱动到期关闭

1. 描述输入经过的关键分支，并给出观察表中的实际值。
2. 哪些对象/依赖是真实的，哪些是替身？本测不能证明什么？

<details><summary>参考答案与判分点</summary>

测试帧 detail.get("deadline_date") → 2026-09-02；run 入参 now → 2026-09-03T00:00:00Z；测试帧 item.getPublicationStatus() → CLOSED；审计动作 / 原因 → CONTENT_CLOSED / 截止日期已到。

边界：不证明定时调度、数据库咨询锁和多实例互斥。

</details>
