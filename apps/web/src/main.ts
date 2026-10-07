import { createApp } from 'vue'
import { createRouter,createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginPage from './features/auth/LoginPage.vue'
import TeamsPage from './features/team/TeamsPage.vue'
import MeetingsPage from './features/meeting/MeetingsPage.vue'
import MeetingPage from './features/meeting/MeetingPage.vue'
import KnowledgePage from './features/knowledge/KnowledgePage.vue'
import { auth,authService } from './features/auth/AuthService'
import './styles.css'
const router=createRouter({history:createWebHistory(),routes:[{path:'/',redirect:'/teams'},{path:'/login',component:LoginPage},{path:'/teams',component:TeamsPage},{path:'/teams/:team',component:MeetingsPage},{path:'/meetings/:meeting',component:MeetingPage},{path:'/:pathMatch(.*)*',redirect:'/teams'}]})
router.addRoute({path:'/teams/:team/knowledge',component:KnowledgePage})
router.beforeEach(async to=>{if(!auth.checked)try{await authService.restore()}catch{return to.path==='/login'?true:'/login'}if(!auth.user&&to.path!=='/login')return '/login';if(auth.user&&to.path==='/login')return '/teams'})
createApp(App).use(router).mount('#app')
