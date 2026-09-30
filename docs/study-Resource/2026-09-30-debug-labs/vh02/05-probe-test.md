# VH02 探针安装与清理

| 文件 | Java 目标位置 |
|---|---|
| [ContentManagementServiceReviseProbeTest.java](probe/ContentManagementServiceReviseProbeTest.java) | `apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceReviseProbeTest.java` |

探针用合成数据、mock 或内存实现，不连接真实数据库/对象存储。先读源码中的边界注释。

```powershell
$pack = 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs'
& "$pack\manage-probes.ps1" -Action Install -Layer vh02
# 学习完成，先确保探针没有未备份的实验修改，再清理
& "$pack\manage-probes.ps1" -Action Clean -Layer vh02
```

安装不覆盖已有文件；清理只删除本脚本登记且哈希未变化的副本。若有同名文件或实验修改，先自行保存/恢复，脚本会拒绝覆盖或删除。

## 探针的证明范围

PasswordEncoder 使用真实编码器但未启动容器；PublicCase 的 Supplier 真执行但 JDBC 仅记录；PublicContactReveal 真加解密但审计为 mock；拦截器手工调用不证明 MVC 顺序；媒体同步回调手工触发不代表真实事务。

不要在源码主干加入调试修改；探针原件留在 docs，测试目录只放副本。
