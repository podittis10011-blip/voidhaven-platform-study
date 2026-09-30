# VH06 三个预测实验

每次只做一个。先写假设，再改指定测试；主源码保持不动。下面是有意制造的红灯/分支变化，红灯不能计入基线通过数。

**自动执行与恢复**：先安装探针，使用包根 `run-experiment.ps1`。脚本只改指定测试文件的一处文本、运行指定方法，finally 恢复原始字节；日志保存在临时目录。

**IDEA 单步**：先把该测试文件复制到临时目录备份；按下列替换编辑后 Debug 精确方法，记录断点与异常/断言；结束用备份恢复，再运行该方法回到绿色。不要用 git reset/checkout 覆盖原有学习改动。

## E1 `PublicWriteInterceptorDebugProbeTest#post_submission_uses_write_bucket`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicWriteInterceptorDebugProbeTest.java:19`。只替换**第一次出现**的以下文本：

原文本：
```java
new MockHttpServletRequest("POST", "/api/v1/public/submissions")
```

替换为：
```java
new MockHttpServletRequest("GET", "/api/v1/public/submissions")
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 原 write 调用验证未发生。只在选定测试方法中替换这一处；GET 在 preHandle 开头直接返回。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh06-E1
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E2 `PublicCaseDebugProbeTest#creates_receipt_stores_only_hash_and_queries_it`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicCaseDebugProbeTest.java:41`。只替换**第一次出现**的以下文本：

原文本：
```java
var status = service.submissionStatus(created.id(), created.queryCode());
```

替换为：
```java
var status = service.submissionStatus(created.id(), "wrong-code");
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

ERROR: CASE_QUERY_INVALID。数据库行存在但 hash 不同，在返回 RECEIVED 之前拒绝。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh06-E2
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E3 `PublicContactRevealDebugProbeTest#reveals_with_matching_aad_and_records_minimal_audit`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh06/probe/PublicContactRevealDebugProbeTest.java:25`。只替换**第一次出现**的以下文本：

原文本：
```java
row("SUBMISSION:" + ID);
```

替换为：
```java
row("SUBMISSION:another-synthetic-id");
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

ERROR: PRIVATE_VALUE_DECRYPTION_FAILED。只改生成密文使用的 AAD；读取仍按原 ID 解密，审计不会执行。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh06-E3
```

实际结果：____；断点：____；解释：____；恢复后基线：____。
