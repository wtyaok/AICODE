# AI Code Review and R&D Quality Assistant

GitLab Merge Request 驱动的代码评审与研发质量助手。项目参考了 GitHub 上 mimo-x/Code-Review-GPT-Gitlab 和 Usagi-org/ai-code-review-helper 的公开设计方向，采用零第三方运行时依赖的 Node.js 实现，便于本地复现和 Docker 部署。

## 已实现功能

- GitLab Merge Request Webhook 接收与 X-Gitlab-Token 校验
- GitLab API 获取 MR 信息和 changes/diff
- 评审任务创建、状态流转、失败记录和幂等去重
- 规则检查：空值风险、SQL 拼接、空 catch、日志、TODO、硬编码凭证
- OpenAI 兼容 LLM API 调用和结构化 JSON 结果解析
- 文件路径、行号、风险类型、严重等级、修改建议
- GitLab MR 评论回写，记录回写失败和评论 ID
- 任务列表、搜索、状态筛选、统计和问题详情
- 问题反馈：采纳、忽略、误报
- JSON 持久化，Docker / docker compose 启动

## 启动

要求 Node.js 18+。

    copy .env.example .env
    npm start

打开 http://localhost:3000。不配置 GitLab 和 LLM 时，可在页面中直接粘贴 unified diff，规则评审仍可运行。

Docker：

    copy .env.example .env
    docker compose up --build

## 配置

| 变量 | 说明 |
| --- | --- |
| PORT | HTTP 端口，默认 3000 |
| GITLAB_URL | GitLab 地址，例如 https://gitlab.com |
| GITLAB_TOKEN | 具有读取 MR 和创建评论权限的 Token |
| GITLAB_WEBHOOK_SECRET | Webhook Secret Token；为空时不校验 |
| LLM_API_URL | OpenAI 兼容的 chat completions 地址 |
| LLM_API_KEY | LLM API Key |
| LLM_MODEL | 模型名称 |
| MAX_DIFF_CHARS | 发送给 LLM 的最大 diff 字符数 |

GitLab Webhook 选择 Merge request events，URL 填：

    https://your-host/api/webhooks/gitlab

## API

- GET /api/health：健康检查
- GET /api/stats：任务和风险统计
- GET /api/tasks?q=&status=：任务列表
- GET /api/tasks/:id：任务详情
- POST /api/reviews：手动创建评审，JSON 支持 title、projectId、iid、diff、postComment
- POST /api/webhooks/gitlab：GitLab MR Webhook
- POST /api/findings/:id/feedback：提交 accepted、ignored 或 false_positive

手动评审示例：

    {
      "title": "登录模块变更",
      "diff": "diff --git a/src/auth.js b/src/auth.js\n@@ -1 +1,2 @@\n+console.log(password)\n+// TODO: replace this",
      "postComment": false
    }

## 目录

    src/server.js       HTTP API、Webhook、任务编排
    src/gitlab.js       GitLab API 客户端
    src/reviewer.js     diff 解析、规则引擎、LLM 结果解析
    src/store.js        原子 JSON 持久化
    public/index.html   管理页面
    public/app.js       页面交互和 API 调用
    public/styles.css   管理页面样式
    data/store.json     初始规则和本地数据

生产环境建议将 JSON 存储替换为 MySQL，将任务执行放入队列，并在 GitLab Token、Webhook Secret、LLM Key 上使用密钥管理服务。
