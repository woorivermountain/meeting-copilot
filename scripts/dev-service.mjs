import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const root=fileURLToPath(new URL('../',import.meta.url))
process.chdir(root)
if(existsSync('.env.service')) process.loadEnvFile('.env.service')
const webEnv={...process.env}
for(const name of Object.keys(webEnv)) if(/^(LLM_|DATABASE_)/.test(name)) delete webEnv[name]
const children=[spawn('mvn',['-q','-f','apps/server/pom.xml','spring-boot:run'],{stdio:'inherit',env:process.env}),spawn('npm',['--prefix','apps/web','run','dev'],{stdio:'inherit',env:webEnv})]
let stopping=false
function stop(code=0){if(stopping)return;stopping=true;for(const child of children)child.kill('SIGTERM');process.exitCode=code}
for(const child of children){child.on('error',error=>{console.error(error.message);stop(1)});child.on('exit',code=>stop(code??0))}
process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop())
