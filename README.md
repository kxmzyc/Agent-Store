# Agent Store

本仓库当前交付项目是 [Smart Mall 智能商城](smart-mall/README.md)：一个面向企业实训答辩的“淘宝 + AI 助手”微型电商系统。

项目源码、Docker 编排、验收脚本和答辩材料都在 `smart-mall/` 目录内。完整启动步骤、测试账号、接口说明和验收清单见 [smart-mall/README.md](smart-mall/README.md)。

## 快速启动

Windows 最快方式（首次启动先生成 `.env`）：

```powershell
cd smart-mall
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
.\open-project.cmd
```

命令行方式：

```powershell
cd smart-mall
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --build
docker compose ps
```

启动后访问：

| 服务 | 地址 |
|---|---|
| 前端页面 | http://127.0.0.1/ |
| 后端 Swagger | http://127.0.0.1:8081/swagger-ui/index.html |
| Agent API | http://127.0.0.1:8000 |
| MySQL | 127.0.0.1:3307 |

## 项目入口

- [完整 README](smart-mall/README.md)
- [接口文档](smart-mall/docs/api-spec.md)
- [架构说明](smart-mall/docs/architecture.md)
- [ER 图](smart-mall/docs/er-diagram.md)
- [缺陷记录](smart-mall/docs/bug-list.md)
- [答辩准备记录](smart-mall/CODEX_DEFENSE_PREP.md)
