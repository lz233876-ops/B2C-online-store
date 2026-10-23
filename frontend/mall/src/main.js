import { createApp } from 'vue'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
// 全量引入 Vant：本项目页面少，省掉按需引入的构建配置
app.use(Vant)
app.use(router)
app.mount('#app')
