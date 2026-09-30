# VH02 观察表

先填预测，Debug 后填实际与原因。断言通过不能替代断点证据；无法到达时写明测试方法、输入和最后停点。

## D1 状态机与公开指针

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| 测试帧 item.getWorkflowStatus()，发布后 | | | |
| 测试帧 item.getVisibility()，归档后 | | | |
| 测试帧 item.getWorkflowStatus()，候选关闭后 | | | |
| 测试帧 item.getPublicationStatus()，恢复后 | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。

## D2 revise 的两条分叉

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| revise 帧 createCandidate，赋值后（发布/草稿） | | | |
| 测试帧 candidate.getRevisionNumber()，已发布用例 | | | |
| 测试帧 item.getPublishedRevision() == published | | | |
| 草稿测试帧 saved.getValue() == item.getCurrentRevision() | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。

## D3 版本与不可编辑状态

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| revise 帧 expectedVersion / item.getVersion() | | | |
| 过期版本异常 code | | | |
| 状态用例 item.getWorkflowStatus() | | | |
| 状态用例异常 code / 修订保存次数 | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。

## D4 类型投影与原始标题校验

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| 输入 draft 的 kind / language | | | |
| 成功 SQL 表名 | | | |
| 成功写入 resourceType 参数 | | | |
| 缺原始标题异常 code | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。

## D5 克隆取历史最大修订号

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| 用例一 candidate.getRevisionNumber() | | | |
| 用例二 candidate.getRevisionNumber() | | | |
| item.getPublishedRevision() == published | | | |
| item.getCurrentRevision() == candidate | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。

## D6 固定时刻驱动到期关闭

使用 [地图](01-debug-walkthrough.md) 的全部驱动方法。

| 观察点（相应栈帧） | 我的预测 | 实际值 | 结论 / 差异原因 |
|---|---|---|---|
| 测试帧 detail.get("deadline_date") | | | |
| run 入参 now | | | |
| 测试帧 item.getPublicationStatus() | | | |
| 审计动作 / 原因 | | | |

测试方法：____；断点文件/锚点：____；日期：____；最后断言结果：____；未覆盖：____。
