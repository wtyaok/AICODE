async function request(config, endpoint, options = {}) {
  if (!config.url || !config.token) {
    throw new Error('GitLab 未配置 URL 或 Token');
  }

  const baseUrl = config.url.replace(/\/$/, '');
  const response = await fetch(baseUrl + '/api/v4' + endpoint, {
    ...options,
    headers: {
      'PRIVATE-TOKEN': config.token,
      'content-type': 'application/json',
      ...(options.headers || {})
    },
    signal: AbortSignal.timeout(30_000)
  });

  const text = await response.text();
  let data;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const detail = typeof data === 'string' ? data : JSON.stringify(data);
    throw new Error('GitLab HTTP ' + response.status + ': ' + detail);
  }

  return data;
}

function getMergeRequest(config, projectId, iid) {
  const project = encodeURIComponent(projectId);
  return request(config, '/projects/' + project + '/merge_requests/' + iid);
}

function getChanges(config, projectId, iid) {
  const project = encodeURIComponent(projectId);
  return request(config, '/projects/' + project + '/merge_requests/' + iid + '/changes');
}

function postNote(config, projectId, iid, body) {
  const project = encodeURIComponent(projectId);

  return request(config, '/projects/' + project + '/merge_requests/' + iid + '/notes', {
    method: 'POST',
    body: JSON.stringify({ body })
  });
}

module.exports = {
  getMergeRequest,
  getChanges,
  postNote
};
