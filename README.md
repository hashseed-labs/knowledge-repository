# Knowledge Repository（企业知识库问答）

前后端分离的企业级 Markdown 知识库问答系统，支持 **JWT 登录、多租户、自定义 RBAC、知识库隔离、操作审计**。

## 技术栈

- **后端**：JDK 21、Spring Boot 3（Maven 多模块）、Spring Security、JWT、MySQL、MyBatis-Plus、Druid、Milvus、Ollama / 阿里云百炼（DashScope）、MinIO、Etcd、Knife4j
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

### 2. 模型 Profile（`ollama` / `qwen`）

通过 `spring.profiles.active` 切换，默认 `ollama`。

| Profile | 配置文件 | Embedding | LLM | Milvus |
|---------|----------|-----------|-----|--------|
| `ollama` | `application-ollama.yml` | bge-small-zh-v1.5 | qwen:7b | `kb_chunks_rbac` / 512 |
| `qwen` | `application-qwen.yml` | qwen3.7-text-embedding | qwen3.7-plus | `kb_chunks_rbac_qwen` / 1024 |

本地 Ollama 模型：

```bash
docker exec -it kb-ollama ollama pull qllama/bge-small-zh-v1.5
docker exec -it kb-ollama ollama pull qwen:7b
```

启动示例：

```bash
# 默认 ollama
mvn spring-boot:run

# 百炼 Qwen（需 API Key）
# PowerShell:
$env:DASHSCOPE_API_KEY="sk-xxx"
$env:SPRING_PROFILES_ACTIVE="qwen"
mvn spring-boot:run

# 或
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=qwen
```

> 切换 profile 会换 embedding 维度与 collection，需重建文档向量，勿混用旧向量库。
> `qwen3.7-plus` 在 `application-qwen.yml` 中已设 `enable-thinking: false`。

### 3. 后端（Maven 多模块）

`backend` 为父工程，业务按域拆分：

| 模块 | 职责 |
|------|------|
| `kb-common` | 统一响应、异常、分页 |
| `kb-platform` | 安全/JWT、租户上下文、审计、配置、RBAC 实体 |
| `kb-ai` | Embedding / LLM / Milvus / MinIO / Etcd |
| `kb-knowledge` | 知识库空间与文档 |
| `kb-identity` | 登录、用户/角色/组织管理 |
| `kb-rag` | 知识问答 |
| `kb-exam` | 在线考试 |
| `kb-bootstrap` | 启动入口、配置与种子数据（可执行 jar） |

各模块职责详见 [`backend/MODULES.md`](backend/MODULES.md)。

```bash
cd backend
# 编译打包
mvn -DskipTests clean package
# 启动（推荐）
mvn -pl kb-bootstrap -am spring-boot:run
# 或运行打包产物
# java -jar kb-bootstrap/target/kb-bootstrap-1.0.0-SNAPSHOT.jar
```

- API: http://localhost:8088
- Knife4j: http://localhost:8088/doc.html
- Health: http://localhost:8088/api/health（需登录，权限 `system:health:view`）

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

登录安全：同一自然日密码错误满 **3** 次后锁定该账号至次日（可配置 `kb.auth.max-failed-logins-per-day`）；管理员在用户管理中重置密码后，当天重新获得尝试次数。已有库需执行 `sql/schema_login_lock.sql`。

## 权限点

| code | 含义 |
|------|------|
| `system:health:view` | 依赖状态（仅组织管理员） |
| `tenant:org:manage` | 组织管理（新建/编辑/删除租户） |
| `tenant:user:manage` | 组织用户管理 |
| `tenant:role:manage` | 角色权限管理 |
| `tenant:audit:view` | 查看审计 |
| `kb:space:manage` | 知识库管理 |
| `kb:member:manage` | 知识库成员 |
| `kb:doc:write` | 上传/删除文档 |
| `kb:doc:read` | 查看文档 |
| `kb:chat:ask` | 问答 |
| `exam:paper:generate` | 根据文档生成试卷 |
| `exam:paper:read` | 查看试卷 |
| `exam:paper:manage` | 编辑/发布/删除试卷 |
| `exam:attempt:take` | 参加考试 |
| `exam:attempt:read` | 查看本人成绩 |

请求头：

- `Authorization: Bearer <accessToken>`
- `X-Tenant-Id: <组织ID>`
- `X-Kb-Id: <知识库ID>`（文档/问答/考试类接口）

## 在线考试（文档生成试卷）

1. 在「文档管理」上传 Markdown，等待状态为 `READY`
2. 打开「在线考试」→「生成试卷」，选择文档并配置题型数量与考试时长
3. 后端按文档分块调用 LLM 生成 JSON 题目并落库（含 `sourceChunkIds` 溯源）
4. 详情中可微调题目，再「发布」为 `READY`
5. 对已发布试卷点「开始考试」进入答题页（倒计时、自动保存）；「交卷」后客观题自动判分，简答标记待评阅
6. 「我的成绩」可查看历史作答记录

表结构见 [`sql/schema_exam.sql`](sql/schema_exam.sql)；启动时也会自动建表/补列。

> 简答自动评分、防作弊等为后续增强。

## 主要能力

1. 登录 / 刷新令牌 / 退出
2. 组织管理（新建/编辑/删除组织、组织成员加人/移人/改角色）、组织与知识库切换，数据按 `tenant_id + kb_id` 隔离；删除为软删除（`DISABLED`），`default` 不可删
3. 自定义角色与权限勾选
4. 文档上传向量化、SSE 流式问答（含耗时与引用折叠）
5. 依据文档生成试卷（单选/多选/判断/简答）
6. 在线作答、倒计时交卷、客观题自动批改
7. 审计日志查询

## 目录

```
knowledge-repository/
├── backend/
├── frontend/
├── sql/schema.sql
├── sql/schema_rbac.sql
├── sql/schema_exam.sql
└── docker-compose.yml
```

## 常见问题

1. **401**：先登录；前端会自动跳转 `/login`。
2. **403**：当前角色缺少权限点，或未加入知识库成员（非组织管理员）。
3. **Milvus 维度/schema**：企业版使用 collection `kb_chunks_rbac`（含 tenant_id/kb_id）。
4. **Etcd 异常**：不影响主流程，配置回退 `application.yml`。
