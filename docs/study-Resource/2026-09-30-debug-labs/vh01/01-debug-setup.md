# 01 调试环境与自检

## 1. 装什么、不装什么

不需要装任何东西。本机已有：

| 组件 | 位置 | 说明 |
|---|---|---|
| IDEA | `D:\IDEA\IntelliJ IDEA 2026.2` | 社区版/旗舰版都够用，本包只用 JUnit + 调试器 |
| JDK 17 | `D:\JDK\JDK17` | 与 `pom.xml` 的 `<java.version>17</java.version>` 对齐 |
| Maven 3.9.4 | `D:\Maven\apache-maven-3.9.4` | `mvn` 不在 PATH，必须写全路径或用 IDEA 内置 Maven |
| 本地仓库 | `C:\Users\雨后阳光\.m2\repository` | 已缓存 Spring Boot 4.1.1 等依赖，可离线跑 |
| 编译产物 | `apps/api/target/` | 已存在 `classes`、`test-classes`、`surefire-reports` |

**不要**为了本包去装 Docker、启动 MySQL 或 MinIO：九站全部是纯内存测试（D9 用的是 standalone MockMvc，同样不连库、不启容器）。

## 2. 在 IDEA 里打开

1. `File → Open`，选 **`D:\学习资料\voidhaven-platform\apps\api`**，不要选仓库根目录。

   理由：根目录会连着 `apps/web/node_modules` 一起索引，白白拖慢；本包只需要 API 模块。

2. 打开后确认 `Project Structure → Project`：

   - SDK = `17`（指向 `D:\JDK\JDK17`）
   - Language level = `17`

3. `Settings → Build Tools → Maven`：

   - Maven home path = `D:\Maven\apache-maven-3.9.4`
   - 勾上 `Work offline`（离线模式，等价于命令行的 `-o`）
   - Runner 的 JRE = 17

4. 等索引结束，打开 `src/test/java/cn/voidhaven/api/service/CatalogCursorTest.java`。

## 3. 断点能不能停住：三分钟自检

按顺序做，任何一步不符合预期就先修环境，别往下走。

| # | 动作 | 预期 |
|---|---|---|
| 1 | 在 `CatalogCursorTest.java` 第 14 行 `assertThat(token).startsWith("c1.");` 左侧点一下，出现红点 | 断点标记生效 |
| 2 | 右键该测试方法 → `Debug 'round_trips_opaque_tokens...'`（或 `Shift+F9`） | JVM 启动，几秒内停在第 14 行 |
| 3 | 看 `Variables` 面板，展开 `token` | 看到以 `c1.` 开头的字符串；`cursor` 里 `sort=DEADLINE`、`key="2026-10-15"` |
| 4 | 按 `Alt+F8`，输入 `token.length()` 回车 | 得到一个数字（不是报错） |
| 5 | 按 `F9` 继续 | 测试跑完，左侧出现绿色对勾 |

停不下来的常见原因：

| 现象 | 原因 | 处理 |
|---|---|---|
| 断点显示灰底红圈 | 类没被编译/加载 | 先 `Run` 一次该测试，让 IDEA 编译 |
| 停在 AssertJ / Mockito 内部代码 | 误按 `F7` 进了库代码 | `Shift+F8`（Step Out）出来，之后改用 `F8` |
| 提示找不到 JDK / 语言级别错乱 | SDK 未设置为 17 | 回第 2 步 |
| 依赖解析失败、联网超时 | 没开离线且网络受限 | 打开 `Work offline`，或命令行加 `-o` |

## 4. 把探针挂进来的两种方式

本包默认用**方式 A（临时复制）**，源码零改动。

本包有两个探针，**目标目录不同，别拷错位置**：

| 探针 | 覆盖站点 | 拷到 |
|---|---|---|
| A `ContentServiceDebugProbeTest.java` | D5 / D6 / D7 / D8 | `apps/api/src/test/java/cn/voidhaven/api/service/` |
| B `PublicContentControllerDebugProbeTest.java` | D9 | `apps/api/src/test/java/cn/voidhaven/api/web/` |

### 方式 A：临时复制（推荐）

```powershell
$lab      = 'D:\学习资料\voidhaven-platform'
$probeDir = "$lab\docs\study-Resource\2026-09-30-debug-labs\vh01\probe"
$testRoot = "$lab\apps\api\src\test\java\cn\voidhaven\api"

# 挂进来（两个探针各去自己的包目录）
Copy-Item "$probeDir\ContentServiceDebugProbeTest.java"            "$testRoot\service" -Force
Copy-Item "$probeDir\PublicContentControllerDebugProbeTest.java"   "$testRoot\web"     -Force

# 跑
Set-Location "$lab\apps\api"
$env:JAVA_HOME = 'D:\JDK\JDK17'
& 'D:\Maven\apache-maven-3.9.4\bin\mvn.cmd' -o "-Dtest=ContentServiceDebugProbeTest,PublicContentControllerDebugProbeTest" test

# 用完删掉（IDEA 里右键文件 → Delete 也可以）
Remove-Item "$testRoot\service\ContentServiceDebugProbeTest.java" -Force
Remove-Item "$testRoot\web\PublicContentControllerDebugProbeTest.java" -Force

# 自检：应只剩进场前就存在的 BootstrapAdminCommandTest.java 一条改动
git -C $lab status --short
```

### 方式 B：标记为测试源根（不想反复复制时）

`File → Project Structure → Modules → api → Sources`，把 `docs/study-Resource/2026-09-30-debug-labs/vh01/probe` 标为 `Tests`。

代价：会写 `.idea/` 配置（已被 `.gitignore` 忽略），且这个目录不在 Maven 的默认测试源码集里，命令行 `mvn test` 不会自动包含它。另外两个探针的 `package` 声明不同（`service` 与 `web`），同目录下 IDEA 会报"包路径与目录不一致"的警告，能编译但不干净。**稳妥起见方式 B 只推荐挂探针 A。**

## 5. 环境坑（都踩过，先记住）

1. **`mvn` / `npm` / `docker` 都不在当前 PATH 上。** Maven 用 `D:\Maven\apache-maven-3.9.4\bin\mvn.cmd`；npm 在 `C:\Program Files\nodejs\npm.cmd`。`docker` 确实没有 CLI。
2. **一个文件里有三个测试类。** `CatalogCursorTest.java` 里同时定义了 `CatalogCursorTest`、`CatalogSortTest`、`CatalogQueryTest`（包级可见）。按文件搜索只能找到第一个类名，按类名跑 `-Dtest=CatalogSortTest` 是有效的。
3. **PowerShell 里逗号会拆参数。** 多类一起跑必须整体加引号：`"-Dtest=A,B,C"`，否则报 `Missing argument in parameter list`。
4. **全量 `mvn test` 不等于纯单元测试。** 这个 pom 没有给 surefire 配 `excludedGroups`，所以带 `@Tag("integration")` 的两个 Testcontainers 用例会被拉起来；Docker 不可用时它们是**静默 Skipped**，不是通过。跑全量前先看报告里的 `Skipped`。
5. **Mockito 的默认返回值会骗你。** 未打桩时 `jdbc.query(...)` 返回空 List、`queryForObject(...)` 返回 `null`。后者会让 `configuredSort` 悄悄回落 `DEFAULT`——这不是 bug，是 `catch (Exception)` 兜底加 Mockito 默认值的组合效果。D5 第一站就是这个。

## 6. 调试时的三条纪律

1. **先预测再运行。** 每停一次，先在 `04` 的表里写下你预期的值，再按 `F8` 看实际值；不一致的地方才是真正学到东西的地方。
2. **不要改主源码来"方便调试"。** 需要造场景时改探针或写新的实验用例，`apps/api/src/main` 保持原样。
3. **调试不影响产品。** 本包全程只用替身协作者，不连库、不写数据、不碰 `.env`。
