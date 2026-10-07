package com.moida.copilot.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.moida.copilot.team.application.TeamService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Stores usage counters only. Prompts, transcript text, and model responses are never persisted. */
@Service
public class AiUsageService {
  private final JdbcClient db;
  private final TeamService teams;

  public AiUsageService(JdbcClient db,TeamService teams){this.db=db;this.teams=teams;}

  public void record(
      UUID team,
      UUID user,
      UUID meeting,
      String feature,
      String provider,
      String model,
      JsonNode response,
      long estimatedInputTokens,
      int outputCharacters){
    var usage=response.path("_usage");
    long input=number(usage,"prompt_tokens","input_tokens");
    long output=number(usage,"completion_tokens","output_tokens");
    long total=number(usage,"total_tokens");
    boolean estimated=input==0&&output==0&&total==0;
    if(estimated){
      input=Math.max(0,estimatedInputTokens);
      output=Math.max(0,(outputCharacters+2L)/3L);
      total=input+output;
    }else if(total==0){
      total=input+output;
    }
    db.sql("insert into ai_usage_events(id,team_id,user_id,meeting_id,feature,provider,model,input_tokens,output_tokens,total_tokens,estimated,created_at) values(:id,:team,:user,:meeting,:feature,:provider,:model,:input,:output,:total,:estimated,current_timestamp)")
        .param("id",UUID.randomUUID()).param("team",team).param("user",user).param("meeting",meeting)
        .param("feature",feature).param("provider",safe(provider,40,"unknown")).param("model",safe(model,160,"unknown"))
        .param("input",input).param("output",output).param("total",total).param("estimated",estimated).update();
  }

  public Map<String,Object> view(UUID team,UUID user,int days){
    teams.requireMember(team,user,false);
    Instant since=Instant.now().minus(Duration.ofDays(days));
    var events=db.sql("select id,meeting_id,feature,provider,model,input_tokens,output_tokens,total_tokens,estimated,created_at from ai_usage_events where team_id=:team and user_id=:user and created_at>=:since order by created_at desc limit 500")
        .param("team",team).param("user",user).param("since",Timestamp.from(since))
        .query((rs,row)->new UsageEvent(
            rs.getObject("id",UUID.class),rs.getObject("meeting_id",UUID.class),rs.getString("feature"),
            rs.getString("provider"),rs.getString("model"),rs.getLong("input_tokens"),
            rs.getLong("output_tokens"),rs.getLong("total_tokens"),rs.getBoolean("estimated"),
            rs.getObject("created_at",OffsetDateTime.class))).list();

    long input=0,output=0,total=0,estimatedRequests=0;
    var features=new LinkedHashMap<String,long[]>();
    for(var event:events){
      input+=event.inputTokens();output+=event.outputTokens();total+=event.totalTokens();
      if(event.estimated())estimatedRequests++;
      var values=features.computeIfAbsent(event.feature(),ignored->new long[2]);values[0]++;values[1]+=event.totalTokens();
    }
    var breakdown=new ArrayList<Map<String,Object>>();
    features.forEach((feature,values)->breakdown.add(Map.of("feature",feature,"requests",values[0],"totalTokens",values[1])));
    var result=new LinkedHashMap<String,Object>();result.put("periodDays",days);result.put("requests",events.size());
    result.put("inputTokens",input);result.put("outputTokens",output);result.put("totalTokens",total);
    result.put("actualRequests",events.size()-estimatedRequests);result.put("estimatedRequests",estimatedRequests);
    result.put("features",breakdown);result.put("recent",events.stream().limit(50).toList());return result;
  }

  private static long number(JsonNode usage,String...names){for(var name:names){var value=usage.path(name);if(value.canConvertToLong()&&value.asLong()>=0)return value.asLong();}return 0;}
  private static String safe(String value,int max,String fallback){String resolved=Objects.toString(value,"").trim();if(resolved.isBlank())return fallback;return resolved.substring(0,Math.min(max,resolved.length()));}

  public record UsageEvent(
      UUID id,
      UUID meetingId,
      String feature,
      String provider,
      String model,
      long inputTokens,
      long outputTokens,
      long totalTokens,
      boolean estimated,
      OffsetDateTime createdAt) {}
}
