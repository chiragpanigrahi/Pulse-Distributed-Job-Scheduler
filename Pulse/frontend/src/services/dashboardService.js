import api from './api'

export const dashboardService = {
  async stats() {
    const { data } = await api.get('/dashboard/stats')
    return data
  },
}
