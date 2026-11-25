# Knowledge Repository（企业知识库问答）

前后端分离的企业级 Markdown 知识库问答系统，支持 **JWT 登录、多租户、自定义 RBAC、知识库隔离、操作审计**。

## 技术栈

- **后端**：JDK 21、Spring Boot 3、Spring Security、JWT、MySQL、MyBatis-Plus、Druid、Milvus、Ollama、MinIO、Etcd、Knife4j
- **前端**：Vue 3、Vite、Element Plus、Axios、Pinia、Vue Router、markdown-it、SSE

## 快速启动

### 1. 基础设施

```bash
docker compose up -d
```

| 服务 | 端口 |
|------|------|
| MySQL | 3306（root/root，库 `knowledge_kb`） |
| MinIO | 9000 / 9001（minioadmin/minioadmin） |
| Etcd | 2379 |
| Milvus | 19530 |
| Ollama | 11434 |

> 若库已存在旧 MVP 表，可执行 [`sql/schema_rbac.sql`](sql/schema_rbac.sql) 增量迁移；全新环境用 [`sql/schema.sql`](sql/schema.sql)。

### 2. Ollama 模型

```bash
docker exec -it kb-ollama ollama pull qllama/bge-small-zh-v1.5
docker exec -it kb-ollama ollama pull qwen:7b
```

### 3. 后端

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8088
- Knife4j: http://localhost:8088/doc.html
- Health: http://localhost:8088/api/health（匿名）

启动后自动种子：

- 组织：`default`（默认组织）
- 账号：`admin` / `admin123`
- 角色：`TENANT_ADMIN` / `KB_MANAGER` / `EDITOR` / `VIEWER`
- 知识库：默认知识库

### 4. 前端

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 ，使用 `admin/admin123` 登录。

## 权限点

| code | 含义 |
|------|------|
| `tenant:user:manage` | 组织用户管理 |
| `tenant:role:manage` | 角色权限管理 |
| `tenant:audit:view` | 查看审计 |
| `kb:space:manage` | 知识库管理 |
| `kb:member:manage` | 知识库成员 |
| `kb:doc:write` | 上传/删除文档 |
| `kb:doc:read` | 查看文档 |
| `kb:chat:ask` | 问答 |

请求头：

- `Authorization: Bearer <accessToken>`
- `X-Tenant-Id: <组织ID>`
- `X-Kb-Id: <知识库ID>`（文档/问答类接口）

## 主要能力

1. 登录 / 刷新令牌 / 退出
2. 组织与知识库切换，数据按 `tenant_id + kb_id` 隔离
3. 自定义角色与权限勾选
4. 文档上传向量化、SSE 流式问答（含耗时与引用折叠）
5. 审计日志查询

## 目录

```
knowledge-repository/
├── backend/
├── frontend/
├── sql/schema.sql
├── sql/schema_rbac.sql
└── docker-compose.yml
```

## 常见问题

1. **401**：先登录；前端会自动跳转 `/login`。
2. **403**：当前角色缺少权限点，或未加入知识库成员（非组织管理员）。
3. **Milvus 维度/schema**：企业版使用 collection `kb_chunks_rbac`（含 tenant_id/kb_id）。
4. **Etcd 异常**：不影响主流程，配置回退 `application.yml`。
