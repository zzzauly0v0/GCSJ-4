import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'ol/ol.css'

import App from './App.vue'
import router from './router'
import './assets/styles/main.scss'

const app = createApp(App)

// Element Plus 全量图标 (按需注册)
for (const [k, c] of Object.entries(ElementPlusIconsVue)) {
  app.component(k, c)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
