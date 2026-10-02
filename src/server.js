const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const store = require('./store');
const reviewer = require('./reviewer');
const gitlab = require('./gitlab');

const port = Number(process.env.PORT || 3000);
const publicDir = path.join(__dirname, '..', 'public');

const config = {
  gitlabUrl: process.env.GITLAB_URL || '',
  gitlabToken: process.env.GITLAB_TOKEN || '',
  webhookSecret: process.env.GITLAB_WEBHOOK_SECRET || '',
  llmUrl: process.env.LLM_API_URL || '',
  llmKey: process.env.LLM_API_KEY || '',
  llmModel: process.env.LLM_MODEL || ''
};

function sendJson(response, statusCode, data) {
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store'
  });
  response.end(JSON.stringify(data));
}

function readRequestBody(request) {
  return new Promise((resolve, reject) => {
    let value = '';

    request.on('data', chunk => {
      value += chunk;

      if (value.length > 8 * 1024 * 1024) {
        reject(new Error('请求体过大'));
      }
    });

    request.on('end', () => {
      try {
        resolve(value ? JSON.parse(value) : {});
      } catch {
        reject(new Error('JSON 请求体无效'));
      }
    });

    request.on('error', reject);
  });
}

function sendStaticFile(response, filePath) {
  const extension = path.extname(filePath);
  const contentTypes = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8'
  };

  response.writeHead(200, {
    'content-type': contentTypes[extension] || 'application/octet-stream'
  });
  fs.createReadStream(filePath).pipe(response);
}

function taskView(task, data) {
  return {
    ...task,
    findings: data.findings.filter(finding => finding.taskId === task.id),
    feedback: data.feedback.filter(item => item.taskId === task.id)
  };
}

function isValidWebhook(request) {
  return (
    !config.webhookSecret ||
    request.headers['x-gitlab-token'] === config.webhookSecret
  );
}

function formatGitlabComment(task, findings) {
  const header =
    '## AI 代码评审\n\n任务：' + task.title + '\n\n';

  if (!findings.length) {
    return header + '未发现规则命中或可确认的问题。';
  }

  const content = findings
    .map(
      (finding, index) =>
        index +
        1 +
        '. **[' +
        finding.severity +
        '] ' +
        finding.type +
        '** - ' +
        finding.file +
        ':' +
        (finding.line || '-') +
        '\n   - ' +
        finding.message +
        '\n   - 建议：' +
        finding.suggestion
    )
    .join('\n');

  return header + content;
}

async function createReview(input) {
  const data = store.read();
  const projectId = input.projectId || input.project_id;
  const iid =
    input.iid || (input.object_attributes && input.object_attributes.iid);
  const sha =
    input.sha ||
    (input.object_attributes &&
      input.object_attributes.last_commit &&
      input.object_attributes.last_commit.id) ||
    '';
  const eventId = input.eventId || '';
  const idempotencyKey =
    (projectId || 'manual') +
    ':' +
    (iid || 'manual') +
    ':' +
    sha +
    ':' +
    eventId;
  const previousTask = data.tasks.find(
    task => task.idempotencyKey === idempotencyKey
  );

  if (previousTask) {
    return taskView(previousTask, data);
  }

  const task = {
    id: crypto.randomUUID(),
    idempotencyKey,
    projectId: projectId || null,
    iid: iid || null,
    title:
      input.title ||
      (input.object_attributes && input.object_attributes.title) ||
      '手动评审任务',
    sha,
    status: 'processing',
    trigger: input.trigger || 'manual',
    createdAt: new Date().toISOString(),
    error: null
  };

  data.tasks.unshift(task);
  store.write(data);

  try {
    let diff = input.diff || '';

    if (!diff && projectId && iid) {
      const gitlabConfig = {
        url: config.gitlabUrl,
        token: config.gitlabToken
      };
      const mergeRequest = await gitlab.getMergeRequest(
        gitlabConfig,
        projectId,
        iid
      );
      const changes = await gitlab.getChanges(gitlabConfig, projectId, iid);

      diff = (changes.changes || [])
        .map(
          change =>
            'diff --git a/' +
            change.old_path +
            ' b/' +
            change.new_path +
            '\n@@ -1 +1 @@\n+' +
            (change.diff || '')
        )
        .join('\n');
      task.title = mergeRequest.title || task.title;
    }

    if (!diff.trim()) {
      throw new Error('未获取到有效 diff');
    }

    const ruleFindings = reviewer.runRules(diff, store.read().rules);
    let llmFindings = [];

    try {
      llmFindings = await reviewer.runLlm(diff, {
        url: config.llmUrl,
        key: config.llmKey,
        model: config.llmModel
      });
    } catch (error) {
      task.llmError = error.message;
    }

    const findings = reviewer
      .mergeFindings(ruleFindings, llmFindings)
      .map(finding => ({ ...finding, taskId: task.id }));

    Object.assign(task, {
      status: 'completed',
      finishedAt: new Date().toISOString(),
      findingCount: findings.length,
      diffChars: diff.length
    });

    store.update(nextData => {
      Object.assign(
        nextData.tasks.find(item => item.id === task.id),
        task
      );
      nextData.findings.push(...findings);
      return nextData;
    });

    if (
      projectId &&
      iid &&
      config.gitlabUrl &&
      config.gitlabToken &&
      input.postComment !== false
    ) {
      try {
        const note = await gitlab.postNote(
          {
            url: config.gitlabUrl,
            token: config.gitlabToken
          },
          projectId,
          iid,
          formatGitlabComment(task, findings)
        );
        task.commentId = note.id;
      } catch (error) {
        task.commentError = error.message;
        store.update(nextData => {
          Object.assign(
            nextData.tasks.find(item => item.id === task.id),
            task
          );
          return nextData;
        });
      }
    }
  } catch (error) {
    task.status = 'failed';
    task.error = error.message;
    task.finishedAt = new Date().toISOString();

    store.update(nextData => {
      Object.assign(nextData.tasks.find(item => item.id === task.id), task);
      return nextData;
    });
  }

  return taskView(task, store.read());
}

async function route(request, response) {
  const url = new URL(
    request.url,
    'http://' + (request.headers.host || 'localhost')
  );

  if (request.method === 'GET' && url.pathname === '/') {
    return sendStaticFile(response, path.join(publicDir, 'index.html'));
  }

  if (request.method === 'GET' && url.pathname === '/app.js') {
    return sendStaticFile(response, path.join(publicDir, 'app.js'));
  }

  if (request.method === 'GET' && url.pathname === '/styles.css') {
    return sendStaticFile(response, path.join(publicDir, 'styles.css'));
  }

  if (request.method === 'GET' && url.pathname === '/api/health') {
    return sendJson(response, 200, {
      ok: true,
      service: 'ai-code-review',
      time: new Date().toISOString()
    });
  }

  const data = store.read();

  if (request.method === 'GET' && url.pathname === '/api/stats') {
    const severities = {};

    for (const level of ['critical', 'high', 'medium', 'low']) {
      severities[level] = data.findings.filter(
        finding => finding.severity === level
      ).length;
    }

    return sendJson(response, 200, {
      tasks: data.tasks.length,
      findings: data.findings.length,
      open: data.findings.filter(finding => finding.status === 'open').length,
      severities
    });
  }

  if (request.method === 'GET' && url.pathname === '/api/rules') {
    return sendJson(response, 200, data.rules);
  }

  if (request.method === 'GET' && url.pathname === '/api/tasks') {
    const query = (url.searchParams.get('q') || '').toLowerCase();
    const status = url.searchParams.get('status');
    const tasks = data.tasks
      .filter(task => {
        const searchable = (task.title + ' ' + task.projectId).toLowerCase();
        return (!status || task.status === status) && searchable.includes(query);
      })
      .map(task => taskView(task, data));

    return sendJson(response, 200, tasks);
  }

  const taskMatch = url.pathname.match(/^\/api\/tasks\/([^/]+)$/);

  if (request.method === 'GET' && taskMatch) {
    const task = data.tasks.find(item => item.id === taskMatch[1]);
    return task
      ? sendJson(response, 200, taskView(task, data))
      : sendJson(response, 404, { error: '任务不存在' });
  }

  if (request.method === 'POST' && url.pathname === '/api/reviews') {
    try {
      const input = await readRequestBody(request);
      return sendJson(
        response,
        201,
        await createReview({ ...input, trigger: 'manual' })
      );
    } catch (error) {
      return sendJson(response, 400, { error: error.message });
    }
  }

  if (request.method === 'POST' && url.pathname === '/api/webhooks/gitlab') {
    if (!isValidWebhook(request)) {
      return sendJson(response, 401, { error: 'Webhook token 无效' });
    }

    try {
      const input = await readRequestBody(request);

      if (input.object_kind && input.object_kind !== 'merge_request') {
        return sendJson(response, 202, {
          accepted: false,
          reason: '非 Merge Request 事件'
        });
      }

      return sendJson(
        response,
        202,
        await createReview({
          ...input,
          trigger: 'webhook',
          eventId: request.headers['x-gitlab-event'] || ''
        })
      );
    } catch (error) {
      return sendJson(response, 400, { error: error.message });
    }
  }

  const feedbackMatch = url.pathname.match(
    /^\/api\/findings\/([^/]+)\/feedback$/
  );

  if (request.method === 'POST' && feedbackMatch) {
    try {
      const input = await readRequestBody(request);
      const allowedStatuses = ['accepted', 'ignored', 'false_positive'];

      if (!allowedStatuses.includes(input.status)) {
        return sendJson(response, 400, {
          error: '反馈状态必须是 accepted、ignored 或 false_positive'
        });
      }

      const finding = data.findings.find(
        item => item.id === feedbackMatch[1]
      );

      if (!finding) {
        return sendJson(response, 404, { error: '问题不存在' });
      }

      store.update(nextData => {
        nextData.findings.find(item => item.id === finding.id).status =
          input.status;
        nextData.feedback.unshift({
          id: crypto.randomUUID(),
          taskId: finding.taskId,
          findingId: finding.id,
          status: input.status,
          comment: input.comment || '',
          createdAt: new Date().toISOString()
        });
        return nextData;
      });

      return sendJson(response, 200, { ok: true });
    } catch (error) {
      return sendJson(response, 400, { error: error.message });
    }
  }

  return sendJson(response, 404, { error: 'Not Found' });
}

const server = http.createServer((request, response) => {
  route(request, response).catch(error =>
    sendJson(response, 500, { error: error.message })
  );
});

server.listen(port, () => {
  console.log(
    'AI Code Review Assistant listening on http://localhost:' + port
  );
});
