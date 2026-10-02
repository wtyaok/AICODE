import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
});

client.interceptors.request.use(config => {
  const key = localStorage.getItem('admin-key');
  if (key) config.headers['X-Admin-Key'] = key;
  return config;
});

export const api = {
  stats: () => client.get('/stats'),
  tasks: params => client.get('/tasks', { params }),
  task: id => client.get('/tasks/' + id),
  createReview: (payload, key) =>
    client.post('/reviews', payload, {
      headers: key ? { 'Idempotency-Key': key } : {}
    }),
  feedback: (id, payload) => client.post('/findings/' + id + '/feedback', payload),
  rules: () => client.get('/rules'),
  saveRule: (id, payload) => client.put('/rules/' + id, payload)
};
