const crypto = require('crypto');

const severityRank = {
  critical: 4,
  high: 3,
  medium: 2,
  low: 1
};

function parseDiff(diff) {
  const files = [];
  let currentFile;
  let lineNumber = 0;

  for (const rawLine of String(diff || '').split(/\r?\n/)) {
    const fileHeader = rawLine.match(/^diff --git a\/(.+?) b\/(.+)$/);
    const hunkHeader = rawLine.match(
      /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/
    );

    if (fileHeader) {
      currentFile = {
        path: fileHeader[2],
        lines: []
      };
      files.push(currentFile);
      continue;
    }

    if (hunkHeader) {
      lineNumber = Number(hunkHeader[1]);
      continue;
    }

    if (!currentFile || rawLine.startsWith('\\ No newline')) {
      continue;
    }

    if (rawLine[0] === '+') {
      currentFile.lines.push({
        text: rawLine.slice(1),
        line: lineNumber,
        added: true
      });
      lineNumber += 1;
    } else if (rawLine[0] === '-') {
      currentFile.lines.push({
        text: rawLine.slice(1),
        line: null,
        added: false
      });
    } else {
      currentFile.lines.push({
        text: rawLine.slice(1),
        line: lineNumber,
        added: false
      });
      lineNumber += 1;
    }
  }

  return files;
}

function getSuggestion(ruleId) {
  const suggestions = {
    'null-safety': '在使用前增加空值校验，或使用安全访问/默认值。',
    'sql-injection': '使用参数化查询或 ORM 参数绑定，避免拼接用户输入。',
    'empty-catch': '记录异常上下文并进行明确处理，避免静默吞掉错误。',
    'console-log': '生产代码使用结构化日志，并避免输出敏感数据。',
    todo: '确认该逻辑是否已经完成，避免遗留待办进入生产分支。',
    'hardcoded-secret':
      '将凭证放入安全配置或密钥管理服务，不要提交到代码库。'
  };

  return suggestions[ruleId] || '请结合代码上下文进行修复并补充回归测试。';
}

function runRules(diff, rules) {
  const findings = [];
  const enabledRules = rules.filter(rule => rule.enabled);

  for (const file of parseDiff(diff)) {
    const addedLines = file.lines.filter(line => line.added);

    for (const line of addedLines) {
      for (const rule of enabledRules) {
        let matched = false;

        try {
          matched = new RegExp(rule.pattern, 'i').test(line.text);
        } catch {
          continue;
        }

        if (!matched) {
          continue;
        }

        const id = crypto
          .createHash('sha1')
          .update(file.path + ':' + line.line + ':' + rule.id + ':' + line.text)
          .digest('hex')
          .slice(0, 16);

        findings.push({
          id,
          ruleId: rule.id,
          file: file.path,
          line: line.line,
          type: rule.name,
          severity: rule.severity,
          message: rule.name + '：该新增代码命中规则，请结合上下文确认。',
          suggestion: getSuggestion(rule.id),
          source: 'rule',
          status: 'open'
        });
      }
    }
  }

  return findings;
}

function extractJson(text) {
  const match = String(text || '').match(/\[[\s\S]*\]/);

  if (!match) {
    return [];
  }

  try {
    return JSON.parse(match[0]);
  } catch {
    return [];
  }
}

async function runLlm(diff, config) {
  if (!config.url || !config.key) {
    return [];
  }

  const requestBody = {
    model: config.model || 'gpt-4o-mini',
    temperature: 0.1,
    messages: [
      {
        role: 'system',
        content:
          '你是严格的代码审查器。只返回 JSON 数组，每项字段为 file,line,type,severity,message,suggestion。severity 只能是 critical/high/medium/low。'
      },
      {
        role: 'user',
        content:
          '审查以下 Git diff，输出 JSON 数组：\n' +
          String(diff).slice(0, Number(process.env.MAX_DIFF_CHARS || 60000))
      }
    ]
  };

  const response = await fetch(config.url, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: 'Bearer ' + config.key
    },
    body: JSON.stringify(requestBody),
    signal: AbortSignal.timeout(45_000)
  });

  if (!response.ok) {
    throw new Error('LLM HTTP ' + response.status);
  }

  const data = await response.json();
  const content =
    data.choices?.[0]?.message?.content || data.output_text || '';

  return extractJson(content).map((item, index) => ({
    id: crypto
      .createHash('sha1')
      .update('llm:' + item.file + ':' + item.line + ':' + item.type + ':' + index)
      .digest('hex')
      .slice(0, 16),
    ruleId: 'llm',
    source: 'llm',
    status: 'open',
    file: item.file || 'unknown',
    line: Number(item.line) || null,
    type: item.type || '智能分析问题',
    severity: severityRank[item.severity] ? item.severity : 'medium',
    message: item.message || '模型识别到潜在问题。',
    suggestion: item.suggestion || '请结合上下文确认并修复。'
  }));
}

function mergeFindings(ruleFindings, llmFindings) {
  const uniqueFindings = new Map();

  for (const finding of ruleFindings.concat(llmFindings)) {
    const key = finding.file + ':' + finding.line + ':' + finding.type;

    if (!uniqueFindings.has(key)) {
      uniqueFindings.set(key, finding);
    }
  }

  return Array.from(uniqueFindings.values()).sort(
    (left, right) => severityRank[right.severity] - severityRank[left.severity]
  );
}

module.exports = {
  parseDiff,
  runRules,
  runLlm,
  mergeFindings
};
