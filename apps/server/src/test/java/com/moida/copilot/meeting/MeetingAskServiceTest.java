package com.moida.copilot.meeting;
import com.fasterxml.jackson.databind.*;
import com.moida.copilot.meeting.application.*;
import com.moida.copilot.meeting.domain.Meeting;
import com.moida.copilot.transcription.application.TranscriptService;
import com.moida.copilot.transcription.domain.Transcript.Segment;
import com.moida.copilot.knowledge.application.KnowledgeService;
import com.moida.copilot.knowledge.domain.Knowledge.Document;
import com.moida.copilot.llm.application.LlmGateway;
import com.moida.copilot.usage.AiUsageService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingAskServiceTest {
  final ObjectMapper json=new ObjectMapper().findAndRegisterModules();final MeetingService access=mock(MeetingService.class);final LlmGateway llm=mock(LlmGateway.class);final KnowledgeService knowledge=mock(KnowledgeService.class);final AiUsageService usage=mock(AiUsageService.class);
  final UUID meeting=UUID.randomUUID(),user=UUID.randomUUID(),team=UUID.randomUUID();
  MeetingAskService service;
  @BeforeEach void setup(){when(access.require(meeting,user,false)).thenReturn(new Meeting(meeting,team,"test","OPEN",0,OffsetDateTime.now()));service=new MeetingAskService(access,mock(TranscriptService.class),knowledge,llm,json,usage);}
  @AfterEach void cleanup(){service.close();}
  Segment segment(String text){return new Segment(UUID.randomUUID(),OffsetDateTime.now(),text);}
  JsonNode create(List<Segment> segments){return json.valueToTree(service.create(meeting,user,"출시 일정은?",true,null,segments,null));}
  JsonNode waitFor(UUID id) throws Exception {for(int i=0;i<200;i++){var result=json.valueToTree(service.read(meeting,user,id));if(!Set.of("QUEUED","RUNNING").contains(result.path("status").asText()))return result;Thread.sleep(10);}throw new AssertionError("job timeout");}
  @Test void boundedContextPreservesRelevantHistoryAndRecent(){var all=new ArrayList<Segment>();all.add(segment("출시 일정 금요일 "+"가".repeat(1000)));for(int i=0;i<30;i++)all.add(segment("다른 논의 "+"나".repeat(1000)));var selected=MeetingContext.select(all,"출시 일정");assertTrue(selected.contains(all.getFirst()));assertTrue(selected.contains(all.getLast()));assertTrue(selected.stream().mapToInt(s->s.text().length()).sum()<=MeetingContext.FOCUSED_BUDGET);assertTrue(selected.size()<all.size());assertEquals(1900,MeetingContext.excerptStart("가".repeat(2000)+"출시 금요일","출시"));var tiny=new ArrayList<Segment>();for(int i=0;i<2000;i++)tiny.add(segment("네"));assertTrue(MeetingContext.select(tiny,"질문").isEmpty());}
  @Test void layeredContextKeepsCauseNeighbourAndLatestChange(){var cause=segment("고객사 인증 심사가 이틀 늦어졌습니다.");var changed=segment("그 영향 때문에 출시일을 금요일에서 다음 주 월요일로 변경하죠.");var all=new ArrayList<Segment>(List.of(cause,changed));for(int i=0;i<20;i++)all.add(segment("별도 디자인 논의 "+"나".repeat(500)));var context=MeetingContext.assemble(all,"왜 출시 일정이 바뀌었어?");assertTrue(context.segments().contains(cause));assertTrue(context.segments().contains(changed));assertTrue(context.inferredNeeds().containsAll(List.of("SCHEDULE","RATIONALE","CHANGE")));assertTrue(context.annotations().get(cause.id()).contains("CONTEXT_NEIGHBOUR"));assertTrue(context.annotations().get(changed.id()).contains("DIRECT_MATCH"));assertEquals("layered-context-v3",context.strategy());assertEquals("실행 계획 조율자",context.advisor().role());var index=json.valueToTree(context.payload("회의","OPEN",0)).path("meetingStateIndex");assertEquals("NAVIGATION_ONLY",index.path("kind").asText());assertTrue(index.path("sourceIdsBySignal").path("CHANGE").toString().contains(changed.id().toString()));}
  @Test void answerUsesLocalOnlyAndImmutableSnapshot() throws Exception {var s=segment("출시는 금요일입니다.");var input=new ArrayList<>(List.of(s));when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenReturn(json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일입니다.","sourceIds",List.of(s.id().toString())))).put("_model","test-local"));UUID id=UUID.fromString(create(input).path("id").asText());input.clear();var result=waitFor(id);assertEquals("COMPLETED",result.path("status").asText());assertEquals("test-local",result.path("result").path("model").asText());assertEquals(1,result.path("result").path("sources").size());assertEquals("layered-context-v3",result.path("result").path("contextStrategy").asText());verify(llm,never()).complete(anyList(),anyList());assertThrows(ResponseStatusException.class,()->service.read(meeting,UUID.randomUUID(),id));}
  @Test void uncitedGeneralKnowledgeStaysSeparateFromMeetingFacts() throws Exception {var s=segment("사용자 온보딩 이탈을 줄여야 합니다.");when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenReturn(json.createObjectNode().put("content",json.writeValueAsString(Map.of("groundedAnswer","","additionalInsights",List.of("첫 성공 행동까지 걸리는 시간을 측정해 보세요."),"assumptions",List.of("현재 이탈 구간이 측정되고 있다는 가정"),"sourceIds",List.of()))));UUID id=UUID.fromString(create(List.of(s)).path("id").asText());var result=waitFor(id).path("result");assertEquals("COMPLETED",waitFor(id).path("status").asText());assertTrue(result.path("groundedAnswer").asText().isBlank());assertEquals(1,result.path("additionalInsights").size());assertEquals("MODEL_GENERAL_KNOWLEDGE_NO_WEB",result.path("knowledgeScope").asText());}
  @Test void malformedAndFabricatedSourcesFail() throws Exception {for(String content:List.of("null","not json","{\"answer\":\"fabricated\",\"sourceIds\":[\"missing\"]}")){when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenReturn(json.createObjectNode().put("content",content));UUID id=UUID.fromString(create(List.of(segment("원문"))).path("id").asText());assertEquals("FAILED",waitFor(id).path("status").asText());}}
  @Test void consentAndBudgetEnforcedBeforeCall(){assertThrows(ResponseStatusException.class,()->service.create(meeting,user,"질문",false,null,List.of(segment("원문")),null));assertThrows(ResponseStatusException.class,()->create(List.of(segment("가".repeat(4001)))));var duplicate=segment("원문");assertThrows(ResponseStatusException.class,()->create(List.of(duplicate,duplicate)));verify(llm,never()).completeApproved(anyList(),anyList(),anyBoolean());verify(llm,never()).complete(anyList(),anyList());}
  @Test void cancelIsTerminalAndPerUserActiveCapacityBounded() throws Exception {var entered=new CountDownLatch(2);var release=new CountDownLatch(1);when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{entered.countDown();release.await();return json.createObjectNode().put("content","{}");});UUID one=UUID.fromString(create(List.of(segment("첫 번째 요청"))).path("id").asText());UUID two=UUID.fromString(create(List.of(segment("두 번째 요청"))).path("id").asText());assertTrue(entered.await(2,TimeUnit.SECONDS));var failure=assertThrows(ResponseStatusException.class,()->create(List.of(segment("세 번째 요청"))));assertEquals(429,failure.getStatusCode().value());assertEquals("CANCELLED",json.valueToTree(service.cancel(meeting,user,one)).path("status").asText());release.countDown();assertEquals("CANCELLED",waitFor(one).path("status").asText());waitFor(two);}
  @Test void revokedAccessAfterModelBlocksResult() throws Exception {var s=segment("원문");when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{when(access.require(meeting,user,false)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","응답","sourceIds",List.of(s.id().toString()))));});UUID id=UUID.fromString(create(List.of(s)).path("id").asText());Thread.sleep(100);assertThrows(ResponseStatusException.class,()->service.read(meeting,user,id));verify(access,atLeast(3)).require(meeting,user,false);}
  @Test void teamDocumentsAreOptInBoundedCitedAndRecheckedWithoutAgent() throws Exception {
    var docs=new ArrayList<Document>();for(int n=0;n<6;n++)docs.add(new Document(UUID.randomUUID(),UUID.randomUUID(),"운영 출시 일정 "+n,"출시 금요일 "+"가".repeat(1000)));
    when(knowledge.teamMeetingDocuments(team,user)).thenReturn(docs);
    assertThrows(ResponseStatusException.class,()->create(List.of()));verify(knowledge,never()).teamMeetingDocuments(any(),any());
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{List<Map<String,Object>> messages=i.getArgument(0);var input=json.readTree((String)messages.getLast().get("content"));assertEquals(2,input.path("documents").size());for(var doc:input.path("documents"))assertTrue(doc.path("quote").asText().length()<=450);return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일","sourceIds",List.of(input.path("documents").get(0).path("sourceId").asText()))));});
    UUID id=UUID.fromString(json.valueToTree(service.create(meeting,user,"출시 일정",true,null,List.of(),null,false,true)).path("id").asText());
    var result=waitFor(id);assertEquals("COMPLETED",result.path("status").asText());assertEquals("운영 출시 일정 0",result.path("result").path("citations").get(0).path("title").asText());
    verify(knowledge,atLeast(3)).recheckMeetingSources(eq(team),eq(user),isNull(),anyCollection());
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(knowledge).recheckMeetingSources(eq(team),eq(user),isNull(),anyCollection());
    assertThrows(ResponseStatusException.class,()->service.read(meeting,user,id));
  }
  @Test void agentStillNarrowsAnOptedInTeamSearch() throws Exception {
    UUID agent=UUID.randomUUID();var s=segment("출시 금요일");when(knowledge.agentMeetingDocuments(team,user,agent)).thenReturn(List.of());
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenReturn(json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일","sourceIds",List.of(s.id().toString())))));
    UUID id=UUID.fromString(json.valueToTree(service.create(meeting,user,"출시",true,null,List.of(s),agent,false,true)).path("id").asText());assertEquals("COMPLETED",waitFor(id).path("status").asText());verify(knowledge).agentMeetingDocuments(team,user,agent);verify(knowledge,never()).teamMeetingDocuments(any(),any());
  }
  @Test void teamSourceRevocationBeforeCallPreventsExport() throws Exception {
    when(knowledge.teamMeetingDocuments(team,user)).thenReturn(List.of(new Document(UUID.randomUUID(),UUID.randomUUID(),"출시 일정","출시 금요일")));
    var checked=new CountDownLatch(1);doAnswer(i->{checked.countDown();throw new ResponseStatusException(HttpStatus.FORBIDDEN);}).when(knowledge).recheckMeetingSources(eq(team),eq(user),isNull(),anyCollection());
    service.create(meeting,user,"출시",true,null,List.of(),null,false,true);assertTrue(checked.await(2,TimeUnit.SECONDS));verify(llm,never()).completeApproved(anyList(),anyList(),anyBoolean());
  }
  @Test void teamSourceRevocationAfterCallCannotExposeResult() throws Exception {
    when(knowledge.teamMeetingDocuments(team,user)).thenReturn(List.of(new Document(UUID.randomUUID(),UUID.randomUUID(),"출시 일정","출시 금요일")));
    var returned=new CountDownLatch(1);when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{List<Map<String,Object>> messages=i.getArgument(0);var input=json.readTree((String)messages.getLast().get("content"));doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(knowledge).recheckMeetingSources(eq(team),eq(user),isNull(),anyCollection());returned.countDown();return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일","sourceIds",List.of(input.path("documents").get(0).path("sourceId").asText()))));});
    UUID id=UUID.fromString(json.valueToTree(service.create(meeting,user,"출시",true,null,List.of(),null,false,true)).path("id").asText());assertTrue(returned.await(2,TimeUnit.SECONDS));assertThrows(ResponseStatusException.class,()->service.read(meeting,user,id));
  }
  @Test void manualSpeakerLabelsAreBoundedAndSentWithSnapshot() throws Exception {
    assertThrows(ResponseStatusException.class,()->create(List.of(new Segment(UUID.randomUUID(),OffsetDateTime.now(),"원문","가".repeat(81)))));
    var s=new Segment(UUID.randomUUID(),OffsetDateTime.now(),"출시 금요일","민수");
    when(llm.completeApproved(anyList(),anyList(),anyBoolean())).thenAnswer(i->{List<Map<String,Object>> messages=i.getArgument(0);var input=json.readTree((String)messages.getLast().get("content"));assertEquals("민수",input.path("context").path("segments").get(0).path("speakerLabel").asText());return json.createObjectNode().put("content",json.writeValueAsString(Map.of("answer","금요일","sourceIds",List.of(s.id().toString()))));});
    UUID id=UUID.fromString(create(List.of(s)).path("id").asText());assertEquals("민수",waitFor(id).path("result").path("sources").get(0).path("speakerLabel").asText());
  }
}
