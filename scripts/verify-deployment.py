"""Exercise a deployed service with synthetic accounts and meeting content only."""
import argparse, http.cookiejar, json, re, secrets, time, uuid
import urllib.request, urllib.error
from pathlib import Path
from datetime import datetime, timezone

parser=argparse.ArgumentParser()
parser.add_argument('origin')
parser.add_argument('--report', required=True)
parser.add_argument('--with-ai', action='store_true')
parser.add_argument('--audio')
parser.add_argument('--login-out')
args=parser.parse_args()
origin=args.origin.rstrip('/')
checks=[]

class Client:
    def __init__(self):
        self.jar=http.cookiejar.CookieJar()
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
    def call(self,path,method='GET',body=None,headers=None):
        headers=dict(headers or {})
        if body is not None and not isinstance(body,bytes):
            body=json.dumps(body,ensure_ascii=False).encode();headers['Content-Type']='application/json'
        if method not in ['GET','HEAD','OPTIONS']:
            _,token,_=self.call('/api/auth/csrf');headers[token['headerName']]=token['token']
        req=urllib.request.Request(origin+path,data=body,headers=headers,method=method)
        try:
            response=self.http.open(req,timeout=100);status=response.status;data=response.read();response_headers=dict(response.headers)
        except urllib.error.HTTPError as error:
            status=error.code;data=error.read();response_headers=dict(error.headers)
        try:content=json.loads(data)
        except (ValueError,UnicodeDecodeError):content=data.decode(errors='replace')
        return status,content,response_headers
    def expect(self,label,path,method='GET',body=None,status=200,headers=None):
        actual,content,response_headers=self.call(path,method,body,headers)
        ok=actual==status
        checks.append({'check':label,'passed':ok,'status':actual,'expected_status':status})
        if not ok: raise AssertionError(f'{label}: HTTP {actual}, expected {status}: {str(content)[:240]}')
        return content,response_headers

owner=Client();login={};error=None
try:
    owner.expect('health','/api/health')
    html,_=owner.expect('public login shell','/login')
    assert '<div id="app"></div>' in html
    for asset in re.findall(r'(?:src|href)="(/assets/[^\"]+)"',html): owner.expect('public bundle '+asset,asset)
    owner.expect('API requires login','/api/teams',status=401)
    password=secrets.token_urlsafe(24)
    email='verify-'+uuid.uuid4().hex[:12]+'@example.test'
    profile,_=owner.expect('signup','/api/auth/signup','POST',{'email':email,'name':'배포 검증 사용자','password':password})
    login={'email':email,'password':password}
    owner.expect('session restore','/api/auth/me')
    team,_=owner.expect('create team','/api/teams','POST',{'name':'배포 검증 팀'})
    meeting,_=owner.expect('create meeting',f'/api/teams/{team["id"]}/meetings','POST',{'title':'금요일 초안 검토'})
    mid=meeting['id'];segment={'id':str(uuid.uuid4()),'receivedAt':datetime.now(timezone.utc).isoformat(),'text':'김민수는 금요일까지 초안을 작성합니다. 월요일에 팀에서 초안을 검토합니다.'}
    transcript={'version':0,'approved':True,'segments':[segment]}
    saved,_=owner.expect('approved transcript save',f'/api/meetings/{mid}/transcript','PUT',transcript)
    assert saved['version']==1
    owner.expect('stale transcript rejected',f'/api/meetings/{mid}/transcript','PUT',transcript,status=409)
    owner.expect('transcript history',f'/api/meetings/{mid}/transcript/history')
    outcome={'kind':'ACTION','text':'금요일까지 초안 작성','approved':False}
    owner.expect('unapproved outcome rejected',f'/api/meetings/{mid}/outcomes','POST',outcome,status=400)
    outcome['approved']=True
    created,_=owner.expect('approved outcome saved',f'/api/meetings/{mid}/outcomes','POST',outcome)
    owner.expect('outcome status change',f'/api/meetings/{mid}/outcomes/{created["id"]}','PATCH',{'status':'DONE','version':0})
    owner.expect('stale outcome rejected',f'/api/meetings/{mid}/outcomes/{created["id"]}','PATCH',{'status':'TODO','version':0},status=409)
    outsider=Client();outsider.expect('second account','/api/auth/signup','POST',{'email':'outside-'+uuid.uuid4().hex[:12]+'@example.test','name':'다른 팀 사용자','password':secrets.token_urlsafe(24)})
    outsider.expect('other team meeting blocked',f'/api/meetings/{mid}',status=403)
    outsider.expect('other team knowledge blocked',f'/api/teams/{team["id"]}/knowledge',status=403)
    knowledge=f'/api/teams/{team["id"]}/knowledge'
    box,_=owner.expect('knowledge box',knowledge+'/boxes','POST',{'name':'제품 일정','department':'개발'})
    owner.expect('approved document upload',knowledge+'/documents','POST',{'boxId':box['id'],'title':'검토 일정','content':'금요일까지 초안을 작성합니다. 월요일에 팀에서 검토합니다.','approved':True})
    agent,_=owner.expect('agent assignment',knowledge+'/agents','POST',{'name':'일정 담당','department':'개발','boxIds':[box['id']]})
    owner.expect('knowledge workspace',knowledge)
    if args.with_ai:
        owner.expect('external consent required',f'/api/meetings/{mid}/summary','POST',{'version':1,'approved':True},status=403)
        summary,_=owner.expect('live provider summary',f'/api/meetings/{mid}/summary','POST',{'version':1,'approved':True,'externalApproved':True})
        assert summary.get('items'), 'No summary items'
        checks.append({'check':'summary returns cited source','passed':any(item.get('sources') for item in summary['items'])})
        answer,_=owner.expect('live knowledge agent',knowledge+f'/agents/{agent["id"]}/ask','POST',{'question':'초안은 언제까지 작성하나요?','approved':True,'externalApproved':True})
        checks.append({'check':'agent returns source citations','passed':bool(answer.get('citations'))})
    if args.audio:
        transcript,_=owner.expect('synthetic Korean audio transcription',f'/api/teams/{team["id"]}/speech/transcribe','POST',Path(args.audio).read_bytes(),headers={'Content-Type':'audio/wav','X-Speech-Session':str(uuid.uuid4()),'X-Speech-Sequence':'0','X-Speech-External-Approved':'true','X-Speech-Provider':'groq'})
        checks.append({'check':'transcription contains synthetic reference words','passed':'금요일' in transcript.get('text','') and '초안' in transcript.get('text',''),'transcribed_text':transcript.get('text')})
    owner.expect('meeting end',f'/api/meetings/{mid}/end','POST',{})
    owner.expect('meeting trash',f'/api/meetings/{mid}','DELETE')
    owner.expect('meeting restore',f'/api/meetings/{mid}/restore','POST',{})
except Exception as exc:
    error=str(exc)
finally:
    if args.login_out and login:Path(args.login_out).write_text(json.dumps(login))
    report={'origin':origin,'checked_at':datetime.now(timezone.utc).isoformat(),'scope':'synthetic accounts and meeting only; not a real team meeting quality study','with_live_ai':args.with_ai,'with_synthetic_audio':bool(args.audio),'checks':checks,'error':error}
    Path(args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False,indent=2))
if error or not all(c['passed'] for c in checks):raise SystemExit(1)
