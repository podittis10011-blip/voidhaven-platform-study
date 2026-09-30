# VOIDHAVEN 学习与开源教学仓库

本仓库是 **VOIDHAVEN 项目团队的开源教学仓库**，也是维护者本人的学习仓库。它包含 `voidhaven-platform` 的源码副本，以及围绕公开内容、生命周期、认证、幂等并发、媒体存储和公开投稿六条核心链路整理的调试学习材料。

本仓库**不是正式产品的权威来源，也不是生产发行版**。正式产品在其组织仓库中演进；本仓库的规范、证据与发布记录是同步当时的历史快照，仅供学习与教学参考。同步内容与产品仓库冲突时，以产品仓库为准。

## 学习内容

- React 19 + TypeScript 前端：页面、浅色/深色主题、表单与编辑草稿。
- Spring Boot API：会话认证、角色权限、CSRF、幂等写入与修订并发控制。
- MySQL 与 Flyway：追加式迁移、双指针修订、版本冲突处理。
- 内容生命周期：草稿、候选修订、发布、归档、恢复与到期关闭。
- 媒体链路：真实图片解码与重编码、私有存储、访问控制与失败补偿。
- 公开投稿：限流分桶、查询码回执、联系方式加密与最小化审计。
- 工程方法：OpenAPI 契约、单元测试、浏览器测试、隔离数据库集成测试与发布演练。

## 目录

| 目录 | 内容 |
| --- | --- |
| `apps/web/` | React 19、TypeScript、Vite 前端 |
| `apps/api/` | Java 17、Spring Boot API、Flyway 迁移 |
| `contracts/openapi/` | 前后端唯一接口契约 |
| `infra/` | Docker Compose、Nginx、验证与发布脚本 |
| `infra/compose/` | 本地、测试服务器与候选环境编排 |
| `docs/study-Resource/` | 按日期整理的核心链路学习材料与调试实验包 |
| `docs/learning-Resource/` | 学习卡片、注释记录与验证说明 |
| `docs/standards/`、`docs/decisions/` | 同步时的规范与决策记录 |
| `docs/runbooks/` | 本地开发、备份恢复与发布回滚手册 |
| `docs/evidence/` | 历史验证记录与已知限制 |

## 学习路线

1. **[从零重建索引](docs/study-Resource/2026-09-21-vh00-from-zero-rebuild/README.md)**：先建立整体地图，再进入各条链路。
2. **六条核心链路**：VH01 公开内容读链路、VH02 生命周期与双修订、VH03 认证与账号资格、VH04 幂等与并发、VH05 媒体与存储、VH06 公开投稿与门禁，入口见 `docs/study-Resource/` 下对应日期目录。
3. **[2026-09-30 调试实验包](docs/study-Resource/2026-09-30-debug-labs/README.md)**：29 个调试站点，每站给出精确测试方法、夹具、断点锚点、单步顺序和观察表，并配可复现实验与探针。建议从该包的 `00-debug-method.md` 读起。
4. **[学习卡片与注释规范](docs/learning-Resource/README.md)**：记录自己的观察，再决定是否落盘注释。

调试实验包中的基线、实验与断点审计结果记录在 `docs/study-Resource/2026-09-30-debug-labs/verification/`，其中未执行与未通过的条目都显式保留，不计为验收通过。

## 本地开始

需要 Git、Node.js 24.15+、npm 10+。后端验证需要 JDK 17；完整运行与数据库集成测试需要 Docker Desktop 的 Linux 引擎。Maven 使用仓库自带 Wrapper。

```powershell
git clone https://github.com/podittis10011-blip/voidhaven-platform-study.git
cd voidhaven-platform
npm ci
```

仅首次创建 `.env`，不要覆盖已有配置：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# 编辑 .env，将占位值替换为仅用于本地的测试值。
docker compose --env-file .env -f infra/compose/compose.local.yml up --build
```

访问 <http://localhost:8080>，健康检查为 `/healthz`。管理员与合作方账号来自 `.env` 中的 `VOIDHAVEN_BOOTSTRAP_ADMIN_*`、`VOIDHAVEN_BOOTSTRAP_PARTNER_*`。只使用合成内容与虚构联系人；默认凭据、HTTP 与端口配置仅适合可信的本地环境，不要暴露到公网。

开发前端时另开终端运行 `npm run web:dev`，访问 <http://127.0.0.1:4173>，它把 `/api` 转发到 `http://localhost:8080`。仅启动前端不代表完整业务可用。

停止环境并保留数据卷：

```powershell
docker compose --env-file .env -f infra/compose/compose.local.yml down
```

细节见 [本地开发手册](docs/runbooks/local-development.md)。历史发布、备份恢复与生产切换脚本仅作教学示例，不要在真实环境直接执行。

## 检查与测试

```powershell
npm run verify:web
./infra/scripts/check-repository.ps1
Push-Location apps/api
./mvnw.cmd -B test "-Dtest=!*IT,!MySqlAndMinioIntegrationTest"
Pop-Location
```

macOS/Linux 后端使用 `cd apps/api && ./mvnw -B test "-Dtest=!*IT,!MySqlAndMinioIntegrationTest"`；PowerShell 脚本需要 PowerShell 7。内存较少时可单独执行 `npm --workspace apps/web run test -- --maxWorkers=1`。

`mvn test` 不执行以 `IT` 结尾的集成测试；`DistributedSafetyIT` 与 `MySqlAndMinioIntegrationTest` 需要可用 Docker，未运行时不得记为完整验收通过。浏览器测试默认包含接口拦截数据，不能替代真实后端验收。

## 贡献

欢迎补充解释、修复问题、添加可复现的小实验。流程与完成条件见 [贡献指南](CONTRIBUTING.md)，协作约束见 [仓库规则](AGENTS.md)。

向产品仓库迁移时，只复制复核后的最小变更，并走产品仓库的 Issue、PR 与 CI 流程。本仓库不自动部署、不绑定域名、不创建外部资源。

## 安全与许可

不要提交 `.env`、凭据、真实用户数据、真实联系人、线上备份、私有证书或正式域名配置。缓存、构建结果、测试报告与本地生成物保持忽略。漏洞处理方式见 [安全说明](SECURITY.md)。

本项目原创代码采用 [MIT License](LICENSE)。第三方依赖、字体与媒体资产遵循各自许可证；图片等资产的来源与授权仍需在对外使用前独立确认。历史同步内容如与产品仓库的授权说明冲突，以产品仓库的授权说明为准。
