import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { fichiersSeo } from './vite-plugins/seo-files.js'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), fichiersSeo()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  },
  // `npm run preview` sert le bundle compile. Sans ce relais, il ne peut joindre aucune API
  // et la boutique s'affiche vide : c'est pourtant la SEULE facon de verifier localement ce
  // que recoit reellement un visiteur. En developpement, Vite injecte la feuille de style
  // apres le premier rendu, et une page mesuree dans cet intervalle parait deborder de
  // 400 px alors qu'elle est saine une fois compilee.
  preview: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
