# VH03 三个预测实验

每次只做一个。先写假设，再改指定测试；主源码保持不动。下面是有意制造的红灯/分支变化，红灯不能计入基线通过数。

**自动执行与恢复**：先安装探针，使用包根 `run-experiment.ps1`。脚本只改指定测试文件的一处文本、运行指定方法，finally 恢复原始字节；日志保存在临时目录。

**IDEA 单步**：先把该测试文件复制到临时目录备份；按下列替换编辑后 Debug 精确方法，记录断点与异常/断言；结束用备份恢复，再运行该方法回到绿色。不要用 git reset/checkout 覆盖原有学习改动。

## E1 `AccountEligibilityFilterTest#invalidates_an_existing_session_when_the_account_is_no_longer_active`

文件：`apps/api/src/test/java/cn/voidhaven/api/web/AccountEligibilityFilterTest.java:25`。只替换**第一次出现**的以下文本：

原文本：
```java
account.suspend();
```

替换为：
```java
account.changeQualificationStatus("REVOKED");
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

PASS: 401、会话失效、上下文清空仍成立。SUSPENDED 与 REVOKED 都不是活跃账号；Account 没有 revoke() 方法。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh03-E1
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E2 `PasswordEncoderDebugProbeTest#real_encoder_uses_random_salt_and_checks_passwords`

文件：`docs/study-Resource/2026-09-30-debug-labs/vh03/probe/PasswordEncoderDebugProbeTest.java:8`。只替换**第一次出现**的以下文本：

原文本：
```java
encoder.matches("WrongPassword123!", first)
```

替换为：
```java
encoder.matches(raw, first)
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 预期 false，实际 true。保留 isFalse 断言，用正确密码替换错误输入；区分 matches 行为与测试预期。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh03-E2
```

实际结果：____；断点：____；解释：____；恢复后基线：____。

## E3 `ProductionConfigurationValidatorTest#acceptsExplicitSecureProductionConfiguration`

文件：`apps/api/src/test/java/cn/voidhaven/api/config/ProductionConfigurationValidatorTest.java:11`。只替换**第一次出现**的以下文本：

原文本：
```java
"172.30.0.0/24", true,
```

替换为：
```java
"172.30.0.0/24", false,
```

我的预测：____。

<details><summary>预期与解释（预测后展开）</summary>

FAILURE: 原无异常断言失败，原因 Production session cookies must be Secure。改该文件公共 helper 中 cookieSecure 参数，其他配置保持合法；只运行选定方法。

</details>

```powershell
& 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs\run-experiment.ps1' -Id vh03-E3
```

实际结果：____；断点：____；解释：____；恢复后基线：____。
