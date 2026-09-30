# 真实环境门槛与证据边界

## VH04 D5：MySQL 并发

前置：JDK 17、Maven 缓存、可用 Docker 引擎与 mysql:8.4 镜像。Docker CLI 缺失不能单独证明引擎不可用。测试创建独立容器和合成库，不指向业务数据库。

```powershell
Set-Location 'D:\学习资料\voidhaven-platform\apps\api'
$env:JAVA_HOME = 'D:\JDK\JDK17'
$mvn = 'D:\Maven\apache-maven-3.9.4\bin\mvn.cmd'
& $mvn -o '-Dtest=IdempotencyServiceTest' '-Dit.test=DistributedSafetyIT' verify
```

IDEA 精确方法：`DistributedSafetyIT.serializes_rate_limits_advisory_locks_and_media_leases`。先看 Assumptions 是否跳过；并发 barrier/锁内部不要 Suspend All，优先在主线程等待结束后的断言行暂停。观察公开写 2/6、登录 5/3、邀请码 2/6、咨询锁、租约与唯一约束竞争。

读取 `target/failsafe-reports/TEST-cn.voidhaven.api.integration.DistributedSafetyIT.xml`，只有 failures=0、errors=0、skipped=0 才计通过。无环境则写 BLOCKED_ENV，不改测试绕过。

## VH05：MySQL / MinIO

```powershell
& $mvn -o '-Dtest=MySqlAndMinioIntegrationTest' test
```

检查 Surefire 的 skipped。只证明此测试实际断言的迁移和存储行为，不证明部署端口、所有授权路径或补偿一定成功。本轮没有运行这些环境测试。

## HTTP 流程

过滤器装配顺序、CSRF、Session 持久化、Retry-After、grant 签发/撤销需真实栈另验。各主题旧学习包的 `03-manual-repro-runbook.md` 是补充入口，不属于本包单元测试已通过的部分。重放前核对运行源码版本，不能把旧镜像结果套用到当前代码。

数据库模拟只证明发起何种查询，审计 mock 只证明调用；媒体同步回调手工触发不证明真实事务回滚。查询码只在创建时交付一次，但不是消费即失效的激活令牌。
