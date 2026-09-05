import { createApp } from 'vue'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import router from './router'
import zh from './locales/zh.json'
import en from './locales/en.json'
import './index.css'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') || 'zh',
  fallbackLocale: 'en',
  messages: {
    zh,
    en
  }
})

const app = createApp(App)

app.use(router)
app.use(i18n)

// 全局配置
app.provide('config', null)
app.provide('reloadConfig', () => {})

app.mount('#app')
