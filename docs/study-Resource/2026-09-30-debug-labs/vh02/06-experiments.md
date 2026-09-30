# VH02 三个预测实验

每次只做一个。先写假设，再改指定测试；主源码保持不动。下面是有意制造的红灯/分支变化，红灯不能计入基线通过数。

**自动执行与恢复**：先安装探针，使用包根 `run-experiment.ps1`。脚本只改指定测试文件的一处文本、运行指定方法，finally 恢复原始字节；日志保存在临时目录。

**IDEA 单步**：先把该测试文件复制到临时目录备份；按下列替换编辑后 Debug 精确方法，记录断点与异常/断言；结束用备份恢复，再运行该方法回到绿色。不要用 git reset/checkout 覆盖原有学习改动。

## E1 `ContentManagementServiceReviseProbeTest#revising_a_published_item_creates_a_candidate_and_keeps_the_published_pointer`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:57`。只替换**第一次出现**的以下文本：

原文本：
```java
item.publish();
```

替换为：
```java
item.submit();
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

ERROR: CONTENT_NOT_EDITABLE；createCandidate 未执行。审核中不在可编辑白名单；并非自动转入草稿分支。修改位于 publishedItem helper。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh02-E1
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E2 `ContentManagementServiceReviseProbeTest#revise_rejects_a_stale_expected_version`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh02/probe/ContentManagementServiceReviseProbeTest.java:104`。只替换**第一次出现**的以下文本：

原文本：
```java
ITEM, draft(), null, 8L, ACTOR
```

替换为：
```java
ITEM, draft(), null, null, ACTOR
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

PASS: CONTENT_VERSION_CONFLICT 仍被正确捕获。缺失 expectedVersion 与过期版本同样拒绝；仓储写入仍为 0。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh02-E2
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E3 `ContentRevisionCandidateServiceTest#clone_uses_the_highest_existing_revision_number`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/ContentRevisionCandidateServiceTest.java:80`。只替换**第一次出现**的以下文本：

原文本：
```java
.thenReturn(5);
```

替换为：
```java
.thenReturn(9);
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 预期 6，实际 10。只改模拟历史最大号，保留旧断言，证明编号依赖 max+1。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh02-E3
```

实际结果：____；断点：____；解释：____；恢复后基线：____。
