# AI 智能订单管理系统

## 环境要求

| 工具 | 版本 |
| --- | --- |
| Docker | Docker Desktop（含 Docker Compose） |
| Node.js | 20+ |
| pnpm | 9 |
| Qwen API Key | 在阿里云百炼控制台申请 |

## 1. 获取代码

```bash
git clone <仓库地址> java-project
cd java-project
```

## 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，填写以下变量：

```ini
QWEN_API_KEY=你的真实密钥
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_MODEL=qwen-max
MYSQL_PASSWORD=数据库密码
```

`MYSQL_PORT` 决定 MySQL 映射到宿主机的端口，需与本地实际使用的一致。

## 3. 启动 MySQL 与后端（Docker 一键部署）

一条命令完成 MySQL 与后端的构建和启动：

```bash
docker compose up -d --build
```

首次构建需要下载 Maven 依赖，耗时几分钟；之后启动会快很多。查看启动状态：

```bash
docker compose ps
docker compose logs -f backend
```

确认后端启动成功：

```bash
curl http://localhost:8080/api/health
```

后端地址为 `http://localhost:8080`，MySQL 映射到 `MYSQL_PORT`。表结构和演示数据在首次启动时由 Flyway 自动创建。

## 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

浏览器打开 `http://localhost:3000`。如后端地址不同，启动前设置：

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 pnpm dev
```
