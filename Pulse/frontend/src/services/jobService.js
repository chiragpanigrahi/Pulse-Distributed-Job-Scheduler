import api from './api'

export const jobService = {
  async list({ search, status, page = 0, size = 20 } = {}) {
    const { data } = await api.get('/jobs', { params: { search, status, page, size } })
    return data
  },

  async get(id) {
    const { data } = await api.get(`/jobs/${id}`)
    return data
  },

  async create(payload) {
    const { data } = await api.post('/jobs', payload)
    return data
  },

  async update(id, payload) {
    const { data } = await api.put(`/jobs/${id}`, payload)
    return data
  },

  async remove(id) {
    await api.delete(`/jobs/${id}`)
  },

  async pause(id) {
    const { data } = await api.post(`/jobs/${id}/pause`)
    return data
  },

  async resume(id) {
    const { data } = await api.post(`/jobs/${id}/resume`)
    return data
  },

  async trigger(id, params = {}) {
    const { data } = await api.post(`/jobs/${id}/trigger`, params)
    return data
  },

  async executions(id, { page = 0, size = 20 } = {}) {
    const { data } = await api.get(`/jobs/${id}/executions`, { params: { page, size } })
    return data
  },
}
