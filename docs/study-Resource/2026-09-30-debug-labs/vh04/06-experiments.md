# VH04 三个预测实验

每次只做一个。先写假设，再改指定测试；主源码保持不动。下面是有意制造的红灯/分支变化，红灯不能计入基线通过数。

**自动执行与恢复**：先安装探针，使用包根 `run-experiment.ps1`。脚本只改指定测试文件的一处文本、运行指定方法，finally 恢复原始字节；日志保存在临时目录。

**IDEA 单步**：先把该测试文件复制到临时目录备份；按下列替换编辑后 Debug 精确方法，记录断点与异常/断言；结束用备份恢复，再运行该方法回到绿色。不要用 git reset/checkout 覆盖原有学习改动。

## E1 `IdempotencyServiceTest#rejects_a_matching_request_while_the_first_request_owns_the_key`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:26`。只替换**第一次出现**的以下文本：

原文本：
```java
assertThat(actions).hasValue(0);
```

替换为：
```java
assertThat(actions).hasValue(1);
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 实际 actions=0。反证断言：在途请求根本未执行业务回调。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh04-E1
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E2 `IdempotencyServiceTest#releases_the_reservation_when_the_business_action_fails`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java:47`。只替换**第一次出现**的以下文本：

原文本：
```java
throw new IllegalStateException("synthetic failure")
```

替换为：
```java
throw new IllegalArgumentException("synthetic failure")
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 原断言期待 IllegalStateException，实际 IllegalArgumentException。业务异常被原样传播；在 catch/release 断点仍能观察释放，不是转换成统一幂等错误。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh04-E2
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E3 `MediaOrphanCleanupServiceTest#queues_before_deleting_from_private_storage`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java:14`。只替换**第一次出现**的以下文本：

原文本：
```java
startsWith("instance-a:"))).thenReturn(1);

        int removed
```

替换为：
```java
startsWith("instance-a:"))).thenReturn(0);

        int removed
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: removed 预期 1，实际 0。只改 SUCCEEDED 完成更新的模拟返回值，模拟未命中持有者条件；不会使已经调用过的 delete 撤销。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh04-E3
```

实际结果：____；断点：____；解释：____；恢复后基线：____。
