# 正式版：Spring Boot + Python + Vue3

正式启动入口是 docker-compose.yml，服务边界与简历技术栈一致：

| 服务 | 技术 | 责任 |
| --- | --- | --- |
| backend | Spring Boot 3、JPA、Spring Data Redis | Webhook、幂等、任务、MySQL、GitLab API、评论回写 |
| analyzer | Python、FastAPI、unidiff、OpenAI SDK | diff 行解析、规则扫描、LLM 结构化分析 |
| frontend | Vue3、Vite、Axios | 任务列表、统计、问题详情、反馈 |
| mysql | MySQL 8.4 | 任务、问题、规则、反馈持久化 |
| redis | Redis 7.4 | 任务状态缓存和短期可观测状态 |

## 启动

1. 复制 .env.formal.example 为 .env，填写 GitLab Token、Webhook Secret、Admin Key 和 LLM 配置。本机已生成 .env，密钥请从该文件读取。
2. 执行 docker compose up --build，Compose 会自动读取 .env。
3. 打开 http://localhost:3000，输入相同的 ADMIN_API_KEY。
4. GitLab Webhook URL 配置为 http://你的地址/api/webhooks/gitlab，选择 Merge request events 和 Push events，Secret Token 填 GITLAB_WEBHOOK_SECRET。

手动评审可粘贴 unified diff；配置 projectId + mrIid 后，后端通过 GitLab API 获取最新 MR 版本 diff。Push 事件使用前后提交 SHA 调用 GitLab Compare API，自动创建幂等任务；新建或删除分支的零 SHA 事件会跳过。Push 任务没有 MR 评论区，结果在任务列表查看。LLM 未配置时规则分析仍可运行。

## 规则覆盖与边界

默认规则覆盖硬编码凭证、SQL 拼接、空异常处理、疑似未检查的 Optional 访问、空返回、权限放开配置、调试日志和重复新增代码片段。重复代码规则查找同一次变更内相同的连续三行新增代码；其他规则主要基于单行正则。它们只提示可疑位置，不能凭单行命中断言一定存在缺陷。接口兼容性和需要完整上下文的风险交给配置后的 LLM 辅助判断，评审结果仍需人工反馈确认。

## 已验证范围

- Spring Boot 3.3 / Java 21 镜像编译与 Java 测试 3 项通过；Python `pytest` 3 项通过；Vue3 / Vite 镜像构建通过。
- Docker Compose 五个服务启动，MySQL 和 Redis 持久化与状态缓存可用。
- 手动 diff 评审、默认规则命中、文件行号、任务幂等、鉴权、统计、反馈记录和前端代理接口已通过本地联调。
- Push Webhook 的入队与幂等、零 SHA 事件跳过已通过本地联调；未配置 GitLab Token 的测试任务在远端拉取阶段按预期失败。
- 未配置真实 GitLab Token 和 LLM Key，因此真实 MR / Push diff 拉取、MR 评论回写和 LLM 结果尚未在外部服务验证。

## 关键 API

- POST /api/webhooks/gitlab：GitLab MR Webhook，使用 X-Gitlab-Token
- POST /api/reviews：手动任务，使用 X-Admin-Key，支持 Idempotency-Key
- GET /api/tasks、GET /api/tasks/{id}：任务查询
- GET /api/stats：统计
- POST /api/findings/{id}/feedback：采纳、忽略、误报
- GET/PUT /api/rules：规则查看和维护

## GitHub 参考

- mimo-x/Code-Review-GPT-Gitlab（MIT）：参考 GitLab MR 版本 diff、规则/LLM 评审、评论回写和 Vue3 管理台整体设计。
- Usagi-org/ai-code-review-helper（Apache-2.0）：参考 Python Webhook、VCS API 和 LLM 服务拆分。
- vishunit/gitlab-mr-reviewer：参考 Spring Boot GitLab MR 与 LLM 调用接口；该仓库未声明许可证，未复制源码。

正式版没有复制第三方代码，使用的是公开架构和 API 使用方式。

## 与旧 Node 原型的关系

根目录旧 src/、public/ 文件是此前的单进程 JavaScript 原型，保留用于对照。正式版运行时不读取它们；正式代码位于 backend/、analyzer/、frontend/。
