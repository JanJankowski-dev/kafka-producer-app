import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/market-data': 'http://localhost:8080',
      '/market-screenings': 'http://localhost:8080',
      '/strategy-comparisons': 'http://localhost:8080',
      '/daily-research': 'http://localhost:8080',
      '/etf-benchmarks': 'http://localhost:8080',
      '/private': 'http://localhost:8080',
    },
  },
})
