import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './index.css'

const app = createApp(App)

app.use(router)

// 全局配置
app.provide('config', null)
app.provide('reloadConfig', () => {})

app.mount('#app')
