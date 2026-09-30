# VH05 三个预测实验

每次只做一个。先写假设，再改指定测试；主源码保持不动。下面是有意制造的红灯/分支变化，红灯不能计入基线通过数。

**自动执行与恢复**：先安装探针，使用包根 `run-experiment.ps1`。脚本只改指定测试文件的一处文本、运行指定方法，finally 恢复原始字节；日志保存在临时目录。

**IDEA 单步**：先把该测试文件复制到临时目录备份；按下列替换编辑后 Debug 精确方法，记录断点与异常/断言；结束用备份恢复，再运行该方法回到绿色。不要用 git reset/checkout 覆盖原有学习改动。

## E1 `MediaValidatorTest#decodes_real_png_pixels_and_reencodes_without_the_input_metadata`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/MediaValidatorTest.java:14`。只替换**第一次出现**的以下文本：

原文本：
```java
BufferedImage.TYPE_INT_ARGB
```

替换为：
```java
BufferedImage.TYPE_INT_RGB
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 原预期 image/png，实际 image/jpeg。无 alpha 通道走 JPEG 输出分支；PNG 输入不等于 PNG 输出。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh05-E1
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E2 `MediaRollbackDebugProbeTest#rollback_callback_deletes_uploaded_object`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh05/probe/MediaRollbackDebugProbeTest.java:44`。只替换**第一次出现**的以下文本：

原文本：
```java
complete(TransactionSynchronization.STATUS_ROLLED_BACK, true);
```

替换为：
```java
complete(TransactionSynchronization.STATUS_COMMITTED, true);
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 预期 delete 1 次，实际 0。只改回调状态，保留旧删除预期，证明提交不会补偿删除。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh05-E2
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E3 `MarkdownDocumentServiceTest#builds_a_safe_document_with_inline_media`

文件：`apps/api/src/test/java/cn/voidhaven/api/service/MarkdownDocumentServiceTest.java:13`。只替换**第一次出现**的以下文本：

原文本：
```java
media:01JCONTENT0000000000000000
```

替换为：
```java
media:01JCONTENT0000000000000001
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 抽取的新 ID 与旧断言不同。解析器照样提取格式合法的 ID，不查询该媒体是否存在；不要将提取当成归属授权。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh05-E3
```

实际结果：____；断点：____；解释：____；恢复后基线：____。
