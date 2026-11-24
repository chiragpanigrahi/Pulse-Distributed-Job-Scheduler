import api from './api'

export const credentialService = {
  async list() {
    const { data } = await api.get('/credentials')
    return data
  },
  async create(payload) {
    const { data } = await api.post('/credentials', payload)
    return data
  },
  async remove(id) {
    await api.delete(`/credentials/${id}`)
  },
}
