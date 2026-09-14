import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { refresh, useSessionStore } from '@ygh/web-shared'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { useHttp } from './api/client'
import './styles/main.scss'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  const session = useSessionStore()
  if (!session.authenticated && session.renewal) {
    try {
      session.restore(await refresh(useHttp(), session.renewal))
    } catch {
      session.clear()
    }
  }
  app.use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
}

void bootstrap()
