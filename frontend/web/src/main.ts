import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import { permission } from './directives/permission'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('permission', permission)
app.mount('#app')
