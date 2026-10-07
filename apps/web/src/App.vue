<script setup lang="ts">
import { ref,computed } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { auth,authService } from './features/auth/AuthService'
import AppIcon from './shared/ui/AppIcon.vue'
const route=useRoute(),router=useRouter(),error=ref('')
const inMeeting=computed(()=>route.path.startsWith('/meetings/'))
async function logout(){try{await authService.logout();await router.push('/login')}catch(e){error.value=(e as Error).message}}
</script>
<template>
<div :class="['app-shell',{'is-meeting':inMeeting,'is-auth':!auth.user}]">
<header class="app-header"><RouterLink to="/teams" class="brand"><span class="brand-mark"><AppIcon name="note" :size="18"/></span>모이다</RouterLink><nav v-if="auth.user"><span class="user-chip">{{auth.user.name.slice(0,1)}}</span><span>{{auth.user.name}}</span><RouterLink v-if="inMeeting" to="/teams" class="quiet-link">내 팀</RouterLink><button v-else class="quiet-button mobile-logout" @click="logout">로그아웃</button></nav></header>
<aside v-if="auth.user&&!inMeeting" class="app-sidebar"><nav aria-label="메인 메뉴"><RouterLink to="/teams"><AppIcon name="team"/>내 팀</RouterLink></nav><div class="sidebar-bottom"><span>{{auth.user.email}}</span><button class="quiet-button" @click="logout"><AppIcon name="logout" :size="18"/>로그아웃</button></div></aside>
<main :class="['app-main',{'meeting-main':inMeeting}]"><p v-if="error" role="alert" class="notice error">{{error}}</p><RouterView :key="route.path"/></main>
</div>
</template>
