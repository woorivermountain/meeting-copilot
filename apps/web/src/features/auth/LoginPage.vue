<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authService } from './AuthService'
const router=useRouter(),signup=ref(false),email=ref(''),name=ref(''),password=ref(''),busy=ref(false),error=ref('')
async function submit(){busy.value=true;error.value='';try{if(signup.value)await authService.signup(email.value,name.value,password.value);else await authService.login(email.value,password.value);password.value='';await router.push('/teams')}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template>
<section class="auth-page">
<h1>{{signup?'계정 만들기':'로그인'}}</h1><p class="auth-subtitle">{{signup?'팀과 함께 사용할 계정을 만들어 주세요.':'팀의 회의와 기록을 이어서 확인하세요.'}}</p>
<form @submit.prevent="submit">
<label v-if="signup">이름<input v-model="name" autocomplete="name" required maxlength="80" placeholder="이름"></label>
<label>이메일<input v-model="email" type="email" autocomplete="username" required maxlength="254" placeholder="name@company.com"></label>
<label>비밀번호<input v-model="password" type="password" :autocomplete="signup?'new-password':'current-password'" required :minlength="signup?10:1" maxlength="72" :placeholder="signup?'10자 이상 입력':'비밀번호 입력'"></label>
<small v-if="signup">10자 이상으로 입력해 주세요. 한글 등 다중 바이트 문자는 최대 72바이트까지 가능합니다.</small>
<p v-if="error" role="alert" class="notice error">{{error}}</p>
<button class="full-width" :disabled="busy">{{busy?'잠시만요…':signup?'계정 만들기':'로그인'}}</button>
</form>
<div class="auth-switch"><span>{{signup?'이미 가입했나요?':'처음 이용하나요?'}}</span><button class="quiet-button" :disabled="busy" @click="signup=!signup;error=''">{{signup?'로그인':'계정 만들기'}}</button></div>
</section>
</template>
