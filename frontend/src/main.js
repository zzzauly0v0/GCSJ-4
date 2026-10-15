import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'ol/ol.css'

// ================================================================
// STORMWATCH Design System — import order is load order.
// theme.css MUST come first (defines CSS variables).
// All subsequent files depend on its tokens.
// ================================================================
import './styles/theme.css'          // 1. CSS variable tokens (colors, fonts, spacing, timing)
import './styles/typography.css'     // 2. Font imports + typographic classes
import './styles/animations.css'     // 3. @keyframes + stagger utility classes
import './styles/globals.css'        // 4. Reset + Element Plus overrides + OL overrides
import './styles/theme-aurora.css'   // 5. Aurora 学术云蓝主题（作用域 .aurora-theme）

// Legacy main.scss — kept for backward-compat; its tokens are overridden by globals.css
import './assets/styles/main.scss'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// Element Plus icon registration
for (const [k, c] of Object.entries(ElementPlusIconsVue)) {
  app.component(k, c)
}

app.use(createPinia())
app.use(router)
// Pass 'dark' class mode — our CSS variables handle the actual palette
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
