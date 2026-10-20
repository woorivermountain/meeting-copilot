# Spring and Vue public preview

This deployment uses the current `codex/spring-vue-service` application, not the legacy Nuxt demo.
The Docker build compiles the Vue app, packages its assets in the Spring Boot JAR, and runs one Java 21 service.
Browser routes and `/api` share the same HTTPS origin. API authorization and CSRF protection remain enabled.

## Render

Use `render.yaml` on `codex/public-deploy-20261020` to create a Free Docker web service and a separate Free PostgreSQL database.
The database host, port, name, user, and password are wired through Render environment variables.
`PORT` is supplied by Render and `/api/health` is the liveness endpoint.
The production profile trusts the hosting proxy's forwarding headers and sets a Secure, HttpOnly, SameSite=Lax session cookie.

Local `.env.service` files, development databases, Node dependencies, and Maven output are excluded from the Docker context.
Do not put provider keys in Git, Docker build arguments, or the frontend.
When provider access is authorized, configure `DEEPSEEK_API_KEY` and `GROQ_API_KEY` as runtime secrets in the hosting dashboard.
Without them the app can still exercise accounts, teams, meetings, manual transcripts, approved outcomes, and documents; live AI and audio transcription report unavailable rather than returning fabricated answers.

## Public preview limits

`PUBLIC_DEMO=true` enables a PostgreSQL-backed UTC daily provider-call budget.
The initial limits are 40 LLM calls and 100 speech calls per day for the whole deployment, shared across accounts and instances.
Failed provider calls also consume a slot. Each agent tool round can consume another LLM call.
This is a request cap, not a dollar-denominated billing guarantee. Provider usage and the hosting plan must still be checked in their dashboards.
Authentication endpoints are limited to 20 attempts per client IP per minute.
Request-specific consent remains required before sending content to an external model or speech provider.

The Free Render web service can sleep after inactivity. Its Free PostgreSQL database expires after 30 days.
Review the database expiry before sharing the URL for a longer hiring process. Free service limits are documented at https://render.com/docs/free.
Automatic service deployments are disabled in this Blueprint; deploy reviewed source changes manually.

## Checks

```bash
npm ci --prefix apps/web
npm --prefix apps/web test
npm --prefix apps/web run build
mvn -B -f apps/server/pom.xml verify
docker build -t meeting-copilot-deploy .
python3 scripts/verify-deployment.py https://YOUR-SERVICE.onrender.com --report verification.json
```

The verification script uses synthetic accounts and meeting content. Add `--with-ai` only when live provider calls are intended; add `--audio /path/to/synthetic.wav` for a consented synthetic-audio check.
It verifies authentication, assets, teams, approved saves, conflict handling, other-team access denial, documents, and meeting restoration.
Provider checks are separate from application tests and do not establish real meeting transcription accuracy or long-duration reliability.
