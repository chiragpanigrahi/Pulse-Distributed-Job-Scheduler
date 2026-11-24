import api from './api'

export const executionService = {
  async get(id) {
    const { data } = await api.get(`/executions/${id}`)
    return data
  },
  async list({ status, page = 0, size = 25 } = {}) {
    const { data } = await api.get('/executions', { params: { status: status || undefined, page, size } })
    return data
  },
  /** What Pulse sent and what came back, with secrets masked by the server. */
  async exchange(id) {
    const { data } = await api.get(`/executions/${id}/exchange`)
    return data
  },
  async pipelineRun(runId) {
    const { data } = await api.get(`/executions/pipeline-runs/${runId}`)
    return data
  },
  async pendingApprovals() {
    const { data } = await api.get('/executions/pending-approvals')
    return data
  },
  async approve(id) {
    await api.post(`/executions/${id}/approve`)
  },
  async reject(id) {
    await api.post(`/executions/${id}/reject`)
  },
}
