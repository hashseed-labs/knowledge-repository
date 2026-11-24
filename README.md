# Knowledge Repository（企业知识库问答 MVP）

前后端分离的 Markdown 知识库问答系统：文档上传 → MinIO 存储 → 分块/向量化 → Milvus → Ollama RAG 流式问答。

## 技术栈

- **后端**：JDK 21、Spring Boot 3、MySQL、MyBatis-Plus、Druid、Milvus、Ollama、MinIO、Etcd、Knife4j
- **前端**：Vue 3、Vite、Element Plus、Axios、Pinia、Vue Router、markdown-it、SSE

## 快速启动

### 1. 启动基础设施

```bash
docker compose up -d
```

服务端口：

| 服务 | 端口 |
|------|------|
| MySQL | 3306（root/root，库 `knowledge_kb`） |
| MinIO | 9000 API / 9001 Console（minioadmin/minioadmin） |
| Etcd | 2379 |
| Milvus | 19530 |
| Ollama | 11434 |

### 2. 拉取 Ollama 模型

```bash
docker exec -it kb-ollama ollama pull qllama/bge-small-zh-v1.5
docker exec -it kb-ollama ollama pull qwen:7b
```

> Embedding 默认模型名为 `qllama/bge-small-zh-v1.5:latest`（维度 **512**）。若你拉取的是官方名 `bge-small-zh-v1.5`，请同步修改 `kb.ollama.embed-model`。维度变化时需同步 `kb.milvus.dimension` 并重建 collection。
>
> 若本机已有 MySQL(3306)/MinIO(9000)/Milvus(19530)/Ollama(11434) 在运行，可只启动缺失服务，或直接复用现有实例并改 `application.yml`。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8088
- Knife4j: http://localhost:8088/doc.html
- Health: http://localhost:8088/api/health

> 若本机已有服务占用 8080，本项目默认使用 **8088**。可通过 `server.port` 修改。
> Ollama embedding 模型名若为 `qllama/bge-small-zh-v1.5:latest`（常见社区镜像），请与 `kb.ollama.embed-model` 保持一致。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173

## 使用流程

1. 在「文档管理」上传 `.md` 文件
2. 等待状态变为 `READY`
3. 在「知识问答」提问，答案以 SSE 流式渲染为 Markdown，并展示引用来源

## 主要 API

- `POST /api/documents/upload` 上传 Markdown
- `GET /api/documents` 分页列表
- `GET /api/documents/{id}` 详情
- `DELETE /api/documents/{id}` 删除（同步清理 MySQL / MinIO / Milvus）
- `POST /api/chat/stream` SSE 流式问答（事件：`session` / `meta` / `token` / `done` / `error`）
- `GET /api/chat/sessions/{sessionId}/messages` 会话历史
- `GET /api/health` 依赖探测

## Etcd 动态配置（可选）

前缀：`/kb/config`

| Key | 含义 | 默认 |
|-----|------|------|
| `/kb/config/topK` | 检索条数 | 5 |
| `/kb/config/chunkSize` | 分块最大字符 | 600 |
| `/kb/config/chunkOverlap` | 分块重叠 | 80 |
| `/kb/config/temperature` | LLM 温度 | 0.2 |
| `/kb/config/systemPrompt` | RAG 系统提示词 | application.yml |

示例：

```bash
docker exec -it kb-etcd etcdctl put /kb/config/topK 8
```

## 目录结构

```
knowledge-repository/
├── backend/           # Spring Boot 单模块
├── frontend/          # Vue3 + Vite
├── sql/schema.sql     # MySQL 表结构
└── docker-compose.yml
```

## 常见问题

1. **文档一直 PROCESSING/FAILED**：检查 Ollama 模型是否已拉取，以及 `/api/health` 中 `ollama`/`milvus`/`minio` 是否为 true。
2. **Milvus 维度不匹配**：确认 embedding 维度与 `kb.milvus.dimension` 一致；不一致时删除 collection 或重建 Milvus 数据卷。
3. **SSE 无输出**：确认前端走 Vite 代理 `/api`，且浏览器 Network 中响应类型为 `text/event-stream`。
