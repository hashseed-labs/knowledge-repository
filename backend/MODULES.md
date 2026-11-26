# Knowledge Repository 后端模块说明

本文描述 `backend` Maven 多模块工程中各子模块的职责、主要包内容与依赖关系。  
运行时仍为**单进程 Spring Boot**，由 `kb-bootstrap` 统一启动并扫描 `com.knowledge.kb`。

## 模块一览

| 模块 | Artifact | 职责摘要 |
|------|----------|----------|
| [kb-common](#kb-common) | `kb-common` | 通用响应、业务异常、分页等无业务域基础类型 |
| [kb-platform](#kb-platform) | `kb-platform` | 安全认证、租户上下文、审计、基础设施配置、RBAC 持久化 |
| [kb-ai](#kb-ai) | `kb-ai` | 向量、大模型、对象存储、配置中心等外部 AI/基础设施客户端 |
| [kb-identity](#kb-identity) | `kb-identity` | 登录鉴权、用户 / 角色 / 组织（租户）管理 |
| [kb-knowledge](#kb-knowledge) | `kb-knowledge` | 知识库空间与文档管理（上传、切分、入库） |
| [kb-rag](#kb-rag) | `kb-rag` | 知识库问答（检索增强生成） |
| [kb-exam](#kb-exam) | `kb-exam` | 在线考试（出卷、作答、判分） |
| [kb-bootstrap](#kb-bootstrap) | `kb-bootstrap` | 启动入口、应用配置、种子数据、健康检查 |

## 依赖关系

依赖方向为单向，禁止形成环：

```text
kb-common
    ↑
kb-platform
    ↑
  kb-ai
    ↑
kb-knowledge ←──── kb-identity（组织创建时初始化默认知库）
    ↑                 ↑
 kb-rag / kb-exam      │
    ↑                 │
    └──── kb-bootstrap ┘
```

父 POM `<modules>` 声明顺序（与依赖层级一致）：

`kb-common` → `kb-platform` → `kb-ai` → `kb-knowledge` → `kb-identity` → `kb-rag` → `kb-exam` → `kb-bootstrap`

- 业务模块只依赖下层模块，不反向依赖。
- `kb-bootstrap` 聚合全部业务模块，产出可执行 fat jar。

## kb-common

**定位**：跨域共享的基础 API 契约，不包含业务逻辑。

**主要能力**：

- 统一 API 包装：`ApiResponse`
- 业务异常与全局处理：`BizException`、`GlobalExceptionHandler`
- 分页结果：`PageResult`

**典型包路径**：`com.knowledge.kb.common`、`com.knowledge.kb.dto.PageResult`

---

## kb-platform

**定位**：平台层——安全、多租户、审计与通用配置；承载 RBAC 相关持久化，供上层业务复用。

**主要能力**：

- 配置：`KbProperties`、CORS、异步、MyBatis-Plus、OpenAPI/Knife4j、Spring Security 过滤链
- 安全：JWT 签发与校验、`JwtAuthFilter`、`AuthUser`、`PermissionService`、`SecurityUtils`、`TenantContext`（`X-Tenant-Id` / `X-Kb-Id`）
- 审计：`@Audited` 切面、`AuditService`、审计查询 API
- 持久化：`sys_user` / `sys_tenant` / `sys_role` / `sys_permission` 等实体与 Mapper；知识库成员 `KbMember`（用于鉴权）

**依赖**：`kb-common`

---

## kb-ai

**定位**：AI 与外部中间件集成，对业务模块屏蔽厂商细节。

**主要能力**：

- Embedding：`EmbeddingClient`（Ollama / DashScope）
- LLM：`LlmClient`（Ollama / DashScope，含流式）
- 向量库：`MilvusService`
- 对象存储：`MinioService`（文档原文）
- 动态配置：`EtcdConfigService`

可通过 Spring Profile（`ollama` / `qwen`）切换实现与模型维度。

**依赖**：`kb-platform`（读取 `KbProperties` 等）

---

## kb-identity

**定位**：身份与组织域。

**主要能力**：

- 认证：登录 / 刷新令牌 / 退出；登录失败次数按自然日锁定（满 N 次锁至次日）；重置密码后清零失败计数
- 用户管理：组织内用户 CRUD、角色绑定
- 角色权限：角色与权限点维护
- 组织（租户）管理：组织 CRUD、软删除、成员加人 / 移人 / 改角色；新建组织时创建默认知库（依赖 knowledge）

**主要 API 前缀**：`/api/auth/**`、`/api/admin/users`、`/api/admin/roles`、`/api/admin/tenants`

**依赖**：`kb-platform`、`kb-knowledge`

---

## kb-knowledge

**定位**：知识资产域——知识库与文档生命周期。

**主要能力**：

- 知识库空间：列表（按权限）、创建/更新、成员管理
- 文档：上传 Markdown、列表、删除；异步切分（`MarkdownChunker`）→ Embedding → 写入 Milvus / MySQL chunk
- 与租户、知识库 ID 隔离（`tenant_id` + `kb_id`）

**主要 API 前缀**：`/api/kb/**`、`/api/documents/**`（以实际 Controller 为准）

**依赖**：`kb-platform`、`kb-ai`

---

## kb-rag

**定位**：检索增强问答。

**主要能力**：

- 会话管理与消息落库
- 基于 Embedding + Milvus 检索相关片段
- 调用 LLM 生成回答（SSE 流式），并返回引用（citations）

**主要 API 前缀**：`/api/chat/**`

**依赖**：`kb-knowledge`、`kb-ai`（及传递依赖 `kb-platform`）

---

## kb-exam

**定位**：在线考试域（已按 api / application / domain / infra 分层）。

**主要能力**：

- 基于知识库内容 LLM 出卷、试卷编辑与发布
- 开考、作答保存、交卷
- 客观题自动判分与成绩查看

**主要包路径**：`com.knowledge.kb.exam.api` / `application` / `domain` / `infra` / `llm`

**依赖**：`kb-knowledge`、`kb-ai`

---

## kb-bootstrap

**定位**：可执行应用壳，不承载核心业务逻辑。

**主要能力**：

- Spring Boot 入口：`KnowledgeRepositoryApplication`（组件扫描 + 精确 `MapperScan`）
- 配置文件：`application.yml`、`application-ollama.yml`、`application-qwen.yml`
- 启动种子：`RbacDataInitializer`（默认组织、admin、内置角色权限、默认知库、考试表增量等）
- 健康检查：`/api/health`（需 `system:health:view`；探测 MySQL / MinIO / Milvus / Embedding / LLM / Etcd）

**依赖**：`kb-identity`、`kb-knowledge`、`kb-rag`、`kb-exam`（及传递依赖）

**启动**：

```bash
cd backend
mvn -pl kb-bootstrap -am spring-boot:run
# 或
java -jar kb-bootstrap/target/kb-bootstrap-1.0.0-SNAPSHOT.jar
```

---

## 包名与模块的关系

为降低迁移成本，各模块内 **Java 包名仍以 `com.knowledge.kb.*` 为主**（例如 `service`、`controller`、`mapper`），物理目录落在对应 Maven 模块下。  
`kb-exam` 已使用独立包前缀 `com.knowledge.kb.exam.*`，可作后续其它域包对齐的参考。

## 相关文档

- 项目总览与启动：[../README.md](../README.md)
- SQL 脚本：[`../sql/`](../sql/)
