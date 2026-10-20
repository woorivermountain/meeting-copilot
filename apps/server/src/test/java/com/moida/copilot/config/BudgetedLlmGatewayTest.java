package com.moida.copilot.config;

import com.moida.copilot.llm.application.LlmGateway;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BudgetedLlmGatewayTest {
  @Test void missingConsentDoesNotSpendButAProviderFailureStillSpendsTheSlot() {
    var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:gateway-budget;DB_CLOSE_DELAY=-1","sa",""));
    jdbc.execute("create table external_request_budget(budget_date date,provider_kind varchar(20),calls integer not null,primary key(budget_date,provider_kind))");
    var budget=new ExternalRequestBudget(jdbc,true,1,1,Clock.systemUTC());
    var provider=mock(LlmGateway.class);
    when(provider.enabled()).thenReturn(true);when(provider.externalConsentRequired()).thenReturn(true);
    doCallRealMethod().when(provider).requireApproval(anyBoolean());
    when(provider.completeApproved(anyList(),anyList(),eq(true))).thenThrow(new IllegalStateException("provider unavailable"));
    var wrapped=new BudgetedLlmGateway(provider,budget);
    assertEquals(403,assertThrows(ResponseStatusException.class,()->wrapped.completeApproved(List.of(),List.of(),false)).getStatusCode().value());
    assertThrows(IllegalStateException.class,()->wrapped.completeApproved(List.of(),List.of(),true));
    assertEquals(429,assertThrows(ResponseStatusException.class,()->wrapped.completeApproved(List.of(),List.of(),true)).getStatusCode().value());
    verify(provider,times(1)).completeApproved(anyList(),anyList(),eq(true));
  }
}
