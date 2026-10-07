<script setup lang="ts">
import { ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { auth,authService } from './features/auth/AuthService'
const route=useRoute(),router=useRouter(),error=ref('')
async function logout(){try{await authService.logout();await router.push('/login')}catch(e){error.value=(e as Error).message}}
</script>
<template><header class="app-header"><RouterLink to="/teams" class="brand"><span class="brand-mark"/>모이다</RouterLink><nav v-if="auth.user"><span>{{auth.user.name}}</span><RouterLink to="/teams">내 팀</RouterLink><button v-if="!route.path.startsWith('/meetings/')" class="text-button" @click="logout">로그아웃</button></nav></header><p v-if="error" role="alert" class="notice error">{{error}}</p><main class="app-main"><RouterView :key="route.path"/></main></template>
