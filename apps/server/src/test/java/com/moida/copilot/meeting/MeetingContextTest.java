package com.moida.copilot.meeting;

import com.moida.copilot.meeting.application.MeetingContext;
import com.moida.copilot.transcription.domain.Transcript.Segment;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeetingContextTest {
  private Segment segment(String text){return new Segment(UUID.randomUUID(),OffsetDateTime.now(),text);}

  @Test void briefMapsTheWholeFlowWithoutReplacingSourceEvidence(){
    var decision=segment("프롬프트 템플릿은 제품팀이 관리하기로 결정했습니다.");
    var change=segment("출시 일정은 금요일에서 다음 주 월요일로 변경합니다.");
    var recent=segment("오늘은 사용자 안내와 배포 점검을 논의합니다.");
    var context=MeetingContext.assemble(List.of(decision,change,recent),"결정과 변경을 정리해 줘");
    assertEquals("layered-context-v4",context.strategy());
    assertEquals(3,context.meetingBrief().substantiveSegments());
    assertTrue(context.meetingBrief().keyMoments().stream().anyMatch(item->item.sourceId().equals(decision.id())));
    assertTrue(context.meetingBrief().keyMoments().stream().anyMatch(item->item.sourceId().equals(change.id())));
    assertEquals(0,context.meetingBrief().payload().get("llmTokensUsed"));
  }

  @Test void typoCandidatesExpandRetrievalButPreserveTheOriginalQuestion(){
    var source=segment("프롬프트 담당자는 제품팀입니다.");
    var context=MeetingContext.assemble(List.of(source),"프롬포트 담당은 누구야?");
    assertEquals("프롬포트",context.queryCorrections().getFirst().original());
    assertEquals("프롬프트",context.queryCorrections().getFirst().suggested());
    assertTrue(context.queryTerms().containsAll(List.of("프롬포트","프롬프트")));
    var interpretation=(Map<?,?>)context.payload("회의","OPEN",0).get("queryInterpretation");
    assertEquals(true,interpretation.get("originalQuestionPreserved"));
  }
}
