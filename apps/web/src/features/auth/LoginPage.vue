<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authService } from './AuthService'
const router=useRouter(), signup=ref(false), email=ref(''),name=ref(''),password=ref(''),busy=ref(false),error=ref('')
async function submit(){busy.value=true;error.value='';try{if(signup.value)await authService.signup(email.value,name.value,password.value);else await authService.login(email.value,password.value);password.value='';await router.push('/teams')}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><section class="auth-page"><h1>{{ signup?'팀과 함께 시작하세요':'회의 작업 공간에 로그인' }}</h1><p>회의를 기록하고, 검토한 결정과 할 일을 팀에 남깁니다.</p><form @submit.prevent="submit"><label v-if="signup">이름<input v-model="name" autocomplete="name" required maxlength="80"></label><label>이메일<input v-model="email" type="email" autocomplete="username" required maxlength="254"></label><label>비밀번호<input v-model="password" type="password" :autocomplete="signup?'new-password':'current-password'" required :minlength="signup?10:1" maxlength="72"></label><small v-if="signup">10자 이상, UTF-8 기준 72바이트 이하로 설정해 주세요.</small><p v-if="error" role="alert" class="notice error">{{ error }}</p><button :disabled="busy">{{busy?'처리 중…':signup?'계정 만들고 시작':'로그인'}}</button></form><button class="text-button" :disabled="busy" @click="signup=!signup;error=''">{{ signup?'이미 계정이 있어요 · 로그인':'처음 사용해요 · 계정 만들기' }}</button><p class="record-note">회의 기록은 소속 팀원에게만 공유됩니다.<br>마이크는 회의 화면에서 직접 켤 때만 사용합니다.</p></section></template>
