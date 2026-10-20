package com.moida.copilot.config;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ExternalRequestBudgetTest {
  private JdbcTemplate db() {
    var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:budget-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
    jdbc.execute("create table external_request_budget(budget_date date,provider_kind varchar(20),calls integer not null,primary key(budget_date,provider_kind))");return jdbc;
  }
  private ExternalRequestBudget budget(JdbcTemplate jdbc,Clock clock,int limit) { return new ExternalRequestBudget(jdbc,true,limit,2,clock); }
  private final Clock day=Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"),ZoneOffset.UTC);

  @Test void separateProvidersAndRestartsRespectThePersistedLimit() {
    var jdbc=db();budget(jdbc,day,1).take("llm");
    var restarted=budget(jdbc,day,1);
    assertEquals(429,assertThrows(ResponseStatusException.class,()->restarted.take("llm")).getStatusCode().value());
    restarted.take("speech");restarted.take("speech");
    assertThrows(ResponseStatusException.class,()->restarted.take("speech"));
    budget(jdbc,Clock.offset(day,Duration.ofDays(1)),1).take("llm");
  }

  @Test void concurrentInstancesCannotSpendMoreThanTheLimit() throws Exception {
    var jdbc=db();var accepted=new AtomicInteger();var pool=Executors.newFixedThreadPool(8);
    try {
      var work=new ArrayList<Callable<Void>>();
      for(int i=0;i<40;i++) work.add(()->{try{budget(jdbc,day,5).take("llm");accepted.incrementAndGet();}catch(ResponseStatusException ignored){}return null;});
      for(var result:pool.invokeAll(work)) result.get();
      assertEquals(5,accepted.get());
    } finally { pool.shutdownNow(); }
  }

  @Test void aZeroLimitBlocksCalls() {
    assertThrows(ResponseStatusException.class,()->budget(db(),day,0).take("llm"));
  }
}
