package com.moida.copilot.config;

import java.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** An atomic, persisted call cap shared by all instances. Failed calls also consume a slot. */
@Component
public class ExternalRequestBudget {
  private final JdbcTemplate jdbc;
  private final boolean active;
  private final int llmLimit, speechLimit;
  private final Clock clock;

  @Autowired
  public ExternalRequestBudget(JdbcTemplate jdbc, Environment env) {
    this(jdbc, env.getProperty("PUBLIC_DEMO", Boolean.class, false),
      env.getProperty("PUBLIC_LLM_CALLS_PER_DAY", Integer.class, 40),
      env.getProperty("PUBLIC_SPEECH_CALLS_PER_DAY", Integer.class, 100), Clock.systemUTC());
  }

  ExternalRequestBudget(JdbcTemplate jdbc, boolean active, int llmLimit, int speechLimit, Clock clock) {
    this.jdbc=jdbc; this.active=active; this.llmLimit=llmLimit; this.speechLimit=speechLimit; this.clock=clock;
  }

  public void take(String kind) {
    if(!active) return;
    int limit=switch(kind) { case "llm" -> llmLimit; case "speech" -> speechLimit; default -> throw new IllegalArgumentException("Unknown provider kind"); };
    var day=LocalDate.now(clock);
    if(reserve(day,kind,limit)) return;
    try { jdbc.update("insert into external_request_budget(budget_date,provider_kind,calls) values(?,?,0)",day,kind); }
    catch(DuplicateKeyException ignored) { /* A concurrent request created the same day. */ }
    if(!reserve(day,kind,limit)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"공개 체험판의 오늘 AI 이용 한도에 도달했습니다. 다음 날 다시 이용해 주세요.");
  }

  private boolean reserve(LocalDate day,String kind,int limit) {
    return jdbc.update("update external_request_budget set calls=calls+1 where budget_date=? and provider_kind=? and calls<?",day,kind,Math.max(0,limit))==1;
  }
}
