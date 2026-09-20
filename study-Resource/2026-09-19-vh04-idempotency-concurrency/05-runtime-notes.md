# VH-04 一次性运行栈：本轮 AI 是怎么起的

用途：记录 2026-09-19 那次实测的环境构造方式，方便复现或延长实验。
**这套栈是一次性的**：MySQL 容器由 Testcontainers 持有，JVM 退出后由 Ryuk 回收；实验结束已全部停止。

## 1. 为什么不用 compose

| 事实 | 影响 |
|---|---|
| 本机 `PATH` 里没有 `docker` / `docker-compose` | 不能执行 `docker compose up` |
| Docker 引擎在运行（Server 29.6.2，`npipe:////./pipe/docker_engine`） | Testcontainers 能正常起容器 |
| 仓库 `target/` 里已有 `api-0.1.0-SNAPSHOT.jar` | 不必重新构建即可启动 API |

所以本轮采用「Testcontainers 起 MySQL + 本地启动 fat jar」的组合。

## 2. 步骤

### 2.1 拿到仓库的测试依赖类路径

```powershell
cd D:\Projects\voidhaven\voidhaven-platform\apps\api
$out = Join-Path $env:TEMP "vh04-cp.txt"
.\mvnw.cmd -B -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath "-Dmdep.outputFile=$out" "-Dmdep.includeScope=test"
```

### 2.2 起一个一次性 MySQL

把下面这段存成 `MysqlKeeper.java`（**放在临时目录，不要放进仓库**）：

```java
import org.testcontainers.containers.MySQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MysqlKeeper {
    public static void main(String[] args) throws Exception {
        MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("voidhaven")
                .withUsername("voidhaven_app")
                .withPassword("change-me-local-only");
        mysql.start();
        String url = mysql.getJdbcUrl() + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        Files.writeString(Path.of(args[0]), url);   // 把 URL 写到文件里给下一步用
        System.out.println("JDBC_URL=" + url);
        Thread.sleep(Long.MAX_VALUE);               // 保持存活；Ctrl+C 或结束进程即回收容器
    }
}
```

```powershell
$dir = Join-Path $env:TEMP "vh04"; New-Item -ItemType Directory -Force -Path $dir | Out-Null
$cp  = Get-Content (Join-Path $env:TEMP "vh04-cp.txt") -Raw

javac -cp $cp -d $dir "$dir\MysqlKeeper.java"
java  -cp "$dir;$cp" MysqlKeeper "$dir\jdbc-url.txt"
```

### 2.3 启动 API

```powershell
$env:SPRING_DATASOURCE_URL      = (Get-Content "$dir\jdbc-url.txt" -Raw).Trim()
$env:SPRING_DATASOURCE_USERNAME = "voidhaven_app"
$env:SPRING_DATASOURCE_PASSWORD = "change-me-local-only"
$env:SPRING_PROFILES_ACTIVE     = "local"      # 必须有：否则不会建 local-admin / 合成内容
$env:SERVER_PORT                = "18084"
$env:VOIDHAVEN_PRIVATE_CONTACT_ENABLED    = "true"
$env:VOIDHAVEN_PRIVATE_VALUE_CURRENT_KEY_ID = "test-local-v1"
$env:VOIDHAVEN_PRIVATE_VALUE_KEYS         = "test-local-v1:dGVzdC1vbmx5LXByaXZhdGUtdmFsdWUta2V5LTMyISE="
$env:VOIDHAVEN_BOOTSTRAP_ADMIN_LOGIN      = "local-admin"
$env:VOIDHAVEN_BOOTSTRAP_ADMIN_PASSWORD   = "<本地口令，自定>"
$env:VOIDHAVEN_BOOTSTRAP_PARTNER_LOGIN    = "local-partner"
$env:VOIDHAVEN_BOOTSTRAP_PARTNER_PASSWORD = "<本地口令，自定>"
$env:VOIDHAVEN_RATE_LIMIT_SECRET          = "vh04-lab-rate-limit-secret-0123456789"

java -jar D:\Projects\voidhaven\voidhaven-platform\apps\api\target\api-0.1.0-SNAPSHOT.jar
```

启动成功的标志：

```text
Flyway: Successfully applied 24 migrations to schema `voidhaven`, now at version v24
Tomcat started on port 18084 (http)
Started VoidhavenApiApplication
```

### 2.4 直连实验库看数据

容器把 3306 映射到一个随机主机端口（本轮是 59830）。用本机 `D:\MySQL Server 8.0\bin\mysql.exe` 直连：

```powershell
& "D:\MySQL Server 8.0\bin\mysql.exe" -u voidhaven_app -pchange-me-local-only -h 127.0.0.1 -P <端口> voidhaven --default-character-set=utf8mb4 -e "select ..."
```

## 3. 回收

```powershell
# 停 API：找到监听 18084 的进程
$c = Get-NetTCPConnection -State Listen -LocalPort 18084
Stop-Process -Id $c.OwningProcess -Force

# 停 keeper：结束后 Ryuk 会连带删除 MySQL 容器
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like "*MysqlKeeper*" } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

## 4. 两个踩坑记录

1. **第一次起的 API 用了默认 profile，登录一直 401。** 原因是 `LocalDataInitializer` 带 `@Profile("local")`，没有账号可登。加 `SPRING_PROFILES_ACTIVE=local` 后正常。
2. **`/auth/csrf` 的响应会 `Set-Cookie: XSRF-TOKEN`。** 用 `curl -b jar` 只读不写，取到的令牌和 cookie 对不上，写请求统一 403。必须 `-b jar -c jar`。

## 5. 与你的栈的差异

| 项 | 本轮一次性栈 | 你的 compose 栈 |
|---|---|---|
| 入口 | API 直连 `:18084` | nginx `:${VOIDHAVEN_HTTP_PORT}`，再代理到 api |
| MinIO | 没有 | 有；媒体相关实验要在这边做 |
| 数据 | 每次全新空库 | 持久卷，注意先清理上一次的实验数据 |
| 限流 | 默认 8/3600s | 可用 `.env` 调小以便快速观察 429 |
