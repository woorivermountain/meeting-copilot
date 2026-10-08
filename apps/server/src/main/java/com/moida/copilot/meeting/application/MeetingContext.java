package com.moida.copilot.meeting.application;

import com.moida.copilot.transcription.domain.Transcript.Segment;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds a bounded, auditable context packet from a meeting transcript.
 *
 * <p>This remains a deterministic first-stage retriever. It deliberately keeps the original
 * segments as evidence while adding topic signals and conversational neighbours so the answer
 * model is not forced to infer context from isolated keyword matches.</p>
 */
public final class MeetingContext {
  public static final int BUDGET = 9000;
  public static final int MAX_SEGMENTS = 80;
  public static final int FOCUSED_BUDGET = 4800;
  public static final int FOCUSED_MAX_SEGMENTS = 40;

  public enum Depth { FOCUSED, EXPANDED }

  private record BudgetPolicy(
      int chars,
      int maxSegments,
      int recentBudget,
      int recentLimit,
      int topSeeds,
      int neighbourRadius) {}

  public record AdvisorFrame(String role,String objective,List<String> lenses) {
    public AdvisorFrame {
      lenses = List.copyOf(lenses);
    }
  }

  /** A deterministic whole-meeting brief. It costs no LLM tokens and never replaces evidence. */
  public record BriefItem(String signal,UUID sourceId,OffsetDateTime receivedAt,String text) {}

  public record MeetingBrief(
      List<String> topics,
      List<BriefItem> keyMoments,
      List<BriefItem> recentThread,
      int substantiveSegments,
      int totalSegments) {
    public MeetingBrief {
      topics=List.copyOf(topics);
      keyMoments=List.copyOf(keyMoments);
      recentThread=List.copyOf(recentThread);
    }
    public Map<String,Object> payload() {
      var out=new LinkedHashMap<String,Object>();
      out.put("kind","DETERMINISTIC_MEETING_BRIEF");
      out.put("instruction","Use this to understand the whole flow, then verify every factual claim against cited source text.");
      out.put("topics",topics);
      out.put("keyMoments",keyMoments);
      out.put("recentThread",recentThread);
      out.put("substantiveSegments",substantiveSegments);
      out.put("totalSegments",totalSegments);
      out.put("llmTokensUsed",0);
      return out;
    }
  }

  /** A retrieval hint only. The user's original question is never rewritten. */
  public record TermCorrection(String original,String suggested,int occurrences,List<UUID> sourceIds) {
    public TermCorrection {sourceIds=List.copyOf(sourceIds);}
  }

  private static final Set<String> STOP_WORDS = Set.of(
      "그리고", "그런데", "그러면", "그래서", "회의", "내용", "지금", "이거", "저거", "그거",
      "어떻게", "무엇", "뭐가", "뭔가", "관련", "대한", "대해서", "알려줘", "말해줘",
      "해주세요", "했나요", "할까요", "있나요", "없는지", "what", "when", "where", "which",
      "how", "please", "tell", "about", "meeting");
  private static final List<String> PARTICLE_SUFFIXES = List.of(
      "으로부터", "에게서", "한테서", "이라고", "라고", "으로", "에서", "에게", "한테", "까지",
      "부터", "처럼", "보다", "하고", "이며", "이고", "은", "는", "이", "가", "을", "를", "의",
      "에", "로", "와", "과", "도", "만");
  private static final Pattern DATE_OR_TIME = Pattern.compile(
      "(?:\\d{1,4}[./-]\\d{1,2}(?:[./-]\\d{1,2})?|\\d{1,2}월|\\d{1,2}일|월요일|화요일|수요일|목요일|금요일|토요일|일요일|오늘|내일|모레|이번\\s*주|다음\\s*주|오전|오후|\\d{1,2}시)");

  private enum Intent {
    SCHEDULE(List.of("일정", "날짜", "기한", "마감", "데드라인", "출시일", "납기", "요일", "언제", "오늘", "내일", "다음주", "다음 주", "연기", "미루", "앞당")),
    DECISION(List.of("결정", "합의", "결론", "확정", "선택", "정하", "하기로", "최종")),
    ACTION(List.of("할일", "할 일", "액션", "담당", "책임", "누가", "해야", "조치", "배정", "후속", "todo", "action")),
    ISSUE(List.of("문제", "이슈", "리스크", "위험", "우려", "차질", "막히", "장애", "blocker", "risk")),
    RATIONALE(List.of("왜", "이유", "근거", "배경", "때문", "덕분", "고려", "트레이드오프", "전제")),
    CHANGE(List.of("변경", "바꾸", "바뀌", "수정", "정정", "취소", "대신", "전환", "번복", "최종")),
    STATUS(List.of("상태", "상황", "진행", "완료", "남은", "현재", "업데이트", "진척")),
    OVERVIEW(List.of("요약", "정리", "핵심", "전체", "무슨 얘기", "논의 내용"));

    final List<String> terms;
    Intent(List<String> terms) { this.terms = terms; }
  }

  private enum Role { RECENT, DIRECT_MATCH, CONTEXT_NEIGHBOUR, MEETING_SIGNAL, RECENT_EXTENSION }

  public record Selection(
      List<Segment> segments,
      Map<UUID,List<String>> annotations,
      List<String> inferredNeeds,
      List<String> queryTerms,
      String strategy,
      Depth depth,
      int budget,
      int droppedLowInformation,
      AdvisorFrame advisor,
      MeetingBrief meetingBrief,
      List<TermCorrection> queryCorrections) {
    public Selection {
      segments = List.copyOf(segments);
      var copied = new LinkedHashMap<UUID,List<String>>();
      annotations.forEach((id, roles) -> copied.put(id, List.copyOf(roles)));
      annotations = Collections.unmodifiableMap(copied);
      inferredNeeds = List.copyOf(inferredNeeds);
      queryTerms = List.copyOf(queryTerms);
      queryCorrections = List.copyOf(queryCorrections);
    }

    /** JSON-ready context envelope. Original segment IDs remain the only transcript citations. */
    public Map<String,Object> payload(String meetingTitle,String meetingStatus,int revision) {
      var out = new LinkedHashMap<String,Object>();
      out.put("strategy", strategy);
      out.put("meeting", Map.of("title", meetingTitle, "status", meetingStatus, "revision", revision));
      out.put("inferredNeeds", inferredNeeds);
      out.put("queryTerms", queryTerms);
      out.put("queryInterpretation",Map.of(
          "originalQuestionPreserved",true,
          "corrections",queryCorrections,
          "instruction","Corrections are retrieval hints. Do not silently change the user's meaning; mention ambiguity when it matters."));
      out.put("responseDepth", depth.name());
      out.put("contextBudgetCharacters", budget);
      out.put("droppedLowInformation", droppedLowInformation);
      out.put("advisor", Map.of(
          "role", advisor.role(),
          "objective", advisor.objective(),
          "lenses", advisor.lenses()));
      out.put("meetingBrief",meetingBrief.payload());
      var annotated = new ArrayList<Map<String,Object>>();
      for (var segment : segments) {
        var item = new LinkedHashMap<String,Object>();
        item.put("id", segment.id());
        item.put("receivedAt", segment.receivedAt());
        item.put("text", segment.text());
        if (segment.speakerLabel()!=null&&!segment.speakerLabel().isBlank()) item.put("speakerLabel", segment.speakerLabel());
        item.put("contextRoles", annotations.getOrDefault(segment.id(), List.of()));
        annotated.add(item);
      }
      out.put("segments", annotated);
      var sourceIdsBySignal=new LinkedHashMap<String,List<UUID>>();
      for(var segment:segments){
        for(var signal:stateSignals(segment.text())){
          var ids=new ArrayList<>(sourceIdsBySignal.getOrDefault(signal,List.of()));
          ids.add(segment.id());sourceIdsBySignal.put(signal,List.copyOf(ids));
        }
      }
      out.put("meetingStateIndex", Map.of(
          "kind","NAVIGATION_ONLY",
          "instruction","Use this index to locate state changes, then verify the cited segment text.",
          "sourceIdsBySignal",sourceIdsBySignal));
      return out;
    }
  }

  private record Profile(String lowerQuestion,List<String> queryTerms,EnumSet<Intent> intents) {}
  private record Scored(int index,int score) {}
  private static final class Candidate {
    final int index;
    final EnumSet<Role> roles = EnumSet.noneOf(Role.class);
    int priority;
    Candidate(int index) { this.index = index; }
    void add(Role role,int candidatePriority) { roles.add(role);priority=Math.max(priority,candidatePriority); }
  }

  private MeetingContext() {}

  /** Compatibility entry point for callers that only need the selected source segments. */
  public static List<Segment> select(List<Segment> input,String question) {
    return assemble(input,question).segments();
  }

  public static Selection assemble(List<Segment> input,String question) {
    return assemble(input,question,Depth.FOCUSED);
  }

  public static Selection assemble(List<Segment> input,String question,Depth requestedDepth) {
    var baseProfile=profile(question);
    var corrections=queryCorrections(input,baseProfile);
    var retrievalTerms=new LinkedHashSet<>(baseProfile.queryTerms);
    corrections.forEach(value->retrievalTerms.add(value.suggested()));
    var profile=new Profile(baseProfile.lowerQuestion,List.copyOf(retrievalTerms),baseProfile.intents);
    var brief=brief(input);
    var depth=requestedDepth==null?Depth.FOCUSED:requestedDepth;
    var policy=depth==Depth.EXPANDED
        ?new BudgetPolicy(BUDGET,MAX_SEGMENTS,3200,28,18,2)
        :new BudgetPolicy(FOCUSED_BUDGET,FOCUSED_MAX_SEGMENTS,1800,16,10,1);
    int droppedLowInformation=(int)input.stream().filter(MeetingContext::isLowInformation).count();
    if (input.isEmpty()) return new Selection(
        List.of(),Map.of(),intentNames(profile.intents),profile.queryTerms,"layered-context-v4",
        depth,policy.chars(),0,advisor(profile,List.of()),brief,corrections);

    var candidates = new HashMap<Integer,Candidate>();
    int recentChars = 0,recentCount = 0,recentScanned=0;
    for (int i=input.size()-1;i>=0&&recentScanned<policy.recentLimit()*2;i--,recentScanned++) {
      if (isLowInformation(input.get(i))) continue;
      int length=input.get(i).text().length();
      if (recentCount>=policy.recentLimit()||(recentChars>0&&recentChars+length>policy.recentBudget())) break;
      add(candidates,i,Role.RECENT,2200+i);
      recentChars+=length;recentCount++;
    }

    var ranked = new ArrayList<Scored>();
    for (int i=0;i<input.size();i++) {
      int relevance=relevance(input.get(i),profile,i,input.size());
      if (relevance>0) ranked.add(new Scored(i,relevance));
    }
    ranked.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(Comparator.comparingInt(Scored::index).reversed()));

    int seeds=0;
    for (var hit:ranked) {
      if (seeds++>=policy.topSeeds()) break;
      add(candidates,hit.index(),Role.DIRECT_MATCH,1600+hit.score()*20+hit.index());
      for (int distance=1;distance<=policy.neighbourRadius();distance++) {
        int before=hit.index()-distance,after=hit.index()+distance;
        int priority=1200+hit.score()*10-distance*30+hit.index();
        if (before>=0&&!isLowInformation(input.get(before))) add(candidates,before,Role.CONTEXT_NEIGHBOUR,priority);
        if (after<input.size()&&!isLowInformation(input.get(after))) add(candidates,after,Role.CONTEXT_NEIGHBOUR,priority);
      }
    }

    // Overview and intent questions need important moments even when the exact wording differs.
    for (int i=0;i<input.size();i++) {
      if (isLowInformation(input.get(i))) continue;
      int signal=signalScore(input.get(i).text(),profile.intents);
      if (signal>0) add(candidates,i,Role.MEETING_SIGNAL,900+signal*20+i);
    }

    // Preserve conversational continuity for terse meetings without filling the prompt with old noise.
    for (int i=input.size()-1;i>=0&&i>=input.size()-policy.maxSegments()*2;i--) {
      if (isLowInformation(input.get(i))) continue;
      add(candidates,i,Role.RECENT_EXTENSION,100+i);
    }

    var ordered = new ArrayList<>(candidates.values());
    ordered.sort(Comparator.comparingInt((Candidate c)->c.priority).reversed().thenComparing(Comparator.comparingInt((Candidate c)->c.index).reversed()));
    var chosen = new LinkedHashSet<Integer>();
    int chars=0;
    for (var candidate:ordered) {
      if (chosen.size()>=policy.maxSegments()) break;
      int length=input.get(candidate.index).text().length();
      if (chars+length<=policy.chars()) {chosen.add(candidate.index);chars+=length;}
    }

    var chronological=chosen.stream().sorted().toList();
    var selected=chronological.stream().map(input::get).toList();
    var annotations=new LinkedHashMap<UUID,List<String>>();
    for (int index:chronological) annotations.put(input.get(index).id(),candidates.get(index).roles.stream().map(Role::name).toList());
    return new Selection(
        selected,annotations,intentNames(profile.intents),profile.queryTerms,"layered-context-v4",
        depth,policy.chars(),droppedLowInformation,advisor(profile,selected),brief,corrections);
  }

  /** Builds a small whole-meeting map without an LLM call. */
  public static MeetingBrief brief(List<Segment> input) {
    var substantive=new ArrayList<Segment>();
    var topicStats=new HashMap<String,int[]>();
    var signalTerms=new HashSet<String>();
    for(var intent:Intent.values()) for(var term:intent.terms) signalTerms.add(stripParticle(term));
    for(int index=0;index<input.size();index++) {
      var segment=input.get(index);
      if(isLowInformation(segment)) continue;
      substantive.add(segment);
      var unique=new LinkedHashSet<>(words(segment.text()));
      for(var term:unique) {
        if(term.length()<2||STOP_WORDS.contains(term)||signalTerms.contains(term)||term.chars().allMatch(Character::isDigit)) continue;
        var stat=topicStats.computeIfAbsent(term,ignored->new int[]{0,0});
        stat[0]++;stat[1]=index;
      }
    }
    var topics=topicStats.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String,int[]>>comparingInt(e->e.getValue()[0]).reversed()
            .thenComparing(Comparator.<Map.Entry<String,int[]>>comparingInt(e->e.getValue()[1]).reversed()))
        .limit(8).map(Map.Entry::getKey).toList();

    var moments=new ArrayList<BriefItem>();var signalCounts=new HashMap<String,Integer>();
    for(int index=input.size()-1;index>=0;index--) {
      var segment=input.get(index);if(isLowInformation(segment))continue;
      for(var signal:stateSignals(segment.text())) {
        if(signalCounts.getOrDefault(signal,0)>=2)continue;
        moments.add(new BriefItem(signal,segment.id(),segment.receivedAt(),clip(segment.text(),240)));
        signalCounts.merge(signal,1,Integer::sum);
      }
    }
    Collections.reverse(moments);
    var recent=substantive.stream().skip(Math.max(0,substantive.size()-5L))
        .map(segment->new BriefItem("RECENT",segment.id(),segment.receivedAt(),clip(segment.text(),240))).toList();
    return new MeetingBrief(topics,moments,recent,substantive.size(),input.size());
  }

  /** Query-aware score used for both transcript candidates and document candidates. */
  public static int score(String text,String question) {
    return relevance(text,null,profile(question));
  }

  public static int excerptStart(String text,String question) {
    String lower=text.toLowerCase(Locale.ROOT);var p=profile(question);var terms=new LinkedHashSet<>(p.queryTerms);
    for (var intent:p.intents) terms.addAll(intent.terms);
    int first=terms.stream().mapToInt(lower::indexOf).filter(i->i>=0).min().orElse(0);
    return Math.max(0,first-100);
  }

  private static int relevance(Segment segment,Profile profile,int index,int total) {
    int base=relevance(segment.text(),segment.speakerLabel(),profile);
    if (base==0) return 0;
    return base+Math.min(4,(index+1)*4/Math.max(1,total));
  }

  private static int relevance(String text,String speaker,Profile profile) {
    String lower=text.toLowerCase(Locale.ROOT);int score=0;
    for (var term:profile.queryTerms) if (lower.contains(term)) score+=8;
    if (speaker!=null&&!speaker.isBlank()&&profile.lowerQuestion.contains(speaker.toLowerCase(Locale.ROOT))) score+=12;
    for (var intent:profile.intents) {
      if (intent==Intent.OVERVIEW) continue;
      int matches=(int)intent.terms.stream().filter(lower::contains).limit(3).count();
      score+=matches*3;
    }
    if (profile.intents.contains(Intent.OVERVIEW)) {
      for (var intent:Intent.values()) if (intent!=Intent.OVERVIEW&&containsAny(lower,intent.terms)) score+=2;
    }
    if (profile.intents.contains(Intent.SCHEDULE)&&DATE_OR_TIME.matcher(lower).find()) score+=4;
    if ((profile.intents.contains(Intent.CHANGE)||profile.intents.contains(Intent.SCHEDULE)||profile.intents.contains(Intent.STATUS))&&containsAny(lower,Intent.CHANGE.terms)) score+=5;
    if (profile.intents.contains(Intent.RATIONALE)&&containsAny(lower,Intent.RATIONALE.terms)) score+=6;
    return score;
  }

  private static int signalScore(String text,EnumSet<Intent> requested) {
    String lower=text.toLowerCase(Locale.ROOT);int score=0;
    boolean overview=requested.contains(Intent.OVERVIEW);
    for (var intent:Intent.values()) {
      if (intent==Intent.OVERVIEW) continue;
      if ((overview||requested.contains(intent))&&containsAny(lower,intent.terms)) score+=intent==Intent.CHANGE?4:2;
    }
    if ((overview||requested.contains(Intent.SCHEDULE))&&DATE_OR_TIME.matcher(lower).find()) score+=2;
    return score;
  }

  private static Profile profile(String question) {
    String lower=Objects.toString(question,"").toLowerCase(Locale.ROOT).trim();
    var intents=EnumSet.noneOf(Intent.class);
    for (var intent:Intent.values()) if (containsAny(lower,intent.terms)) intents.add(intent);
    var terms=new LinkedHashSet<String>();
    for (var raw:lower.split("[^\\p{L}\\p{N}]+")) {
      String term=stripParticle(raw);
      if (term.length()>1&&!STOP_WORDS.contains(raw)&&!STOP_WORDS.contains(term)) terms.add(term);
      if(terms.size()>=64)break;
    }
    return new Profile(lower,List.copyOf(terms),intents);
  }

  private static List<TermCorrection> queryCorrections(List<Segment> input,Profile profile) {
    if(input.isEmpty()||profile.queryTerms.isEmpty())return List.of();
    var vocabulary=new LinkedHashMap<String,List<UUID>>();
    for(int index=input.size()-1;index>=0;index--)for(var term:new LinkedHashSet<>(words(input.get(index).text()))) {
      if(term.length()<3||STOP_WORDS.contains(term)||term.chars().allMatch(Character::isDigit))continue;
      if(!vocabulary.containsKey(term)&&vocabulary.size()>=4000)continue;
      vocabulary.computeIfAbsent(term,ignored->new ArrayList<>()).add(input.get(index).id());
    }
    var corrections=new ArrayList<TermCorrection>();
    for(var original:profile.queryTerms) {
      if(original.length()<3||vocabulary.containsKey(original))continue;
      String best=null;int bestDistance=Integer.MAX_VALUE,bestCount=-1;
      for(var candidate:vocabulary.entrySet()) {
        String term=candidate.getKey();int lengthGap=Math.abs(original.length()-term.length());
        int allowed=original.length()<=4?1:2;
        if(lengthGap>allowed)continue;
        int distance=levenshtein(original,term,allowed);
        if(distance>allowed||distance==0)continue;
        int count=candidate.getValue().size();
        if(distance<bestDistance||(distance==bestDistance&&count>bestCount)) {best=term;bestDistance=distance;bestCount=count;}
      }
      if(best!=null) {
        var ids=vocabulary.get(best).stream().distinct().limit(4).toList();
        corrections.add(new TermCorrection(original,best,vocabulary.get(best).size(),ids));
        if(corrections.size()>=8)break;
      }
    }
    return List.copyOf(corrections);
  }

  private static List<String> words(String value) {
    var words=new ArrayList<String>();
    for(var raw:Objects.toString(value,"").toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      var term=stripParticle(raw);
      if(!term.isBlank())words.add(term);
    }
    return words;
  }

  /** Bounded Levenshtein: stops work once no cell can remain within maxDistance. */
  private static int levenshtein(String left,String right,int maxDistance) {
    if(Math.abs(left.length()-right.length())>maxDistance)return maxDistance+1;
    var previous=new int[right.length()+1];for(int j=0;j<=right.length();j++)previous[j]=j;
    for(int i=1;i<=left.length();i++) {
      var current=new int[right.length()+1];current[0]=i;int rowMin=current[0];
      for(int j=1;j<=right.length();j++) {
        int cost=left.charAt(i-1)==right.charAt(j-1)?0:1;
        current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+cost);
        rowMin=Math.min(rowMin,current[j]);
      }
      if(rowMin>maxDistance)return maxDistance+1;
      previous=current;
    }
    return previous[right.length()];
  }

  private static String clip(String value,int max) {
    String normalized=Objects.toString(value,"").replaceAll("\\s+"," ").trim();
    return normalized.length()<=max?normalized:normalized.substring(0,max-1)+"…";
  }

  private static String stripParticle(String value) {
    if (value==null) return "";
    for (var suffix:PARTICLE_SUFFIXES) if (value.length()>suffix.length()+1&&value.endsWith(suffix)) return value.substring(0,value.length()-suffix.length());
    return value;
  }

  private static boolean isLowInformation(Segment segment) {
    String value=segment.text().toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{P}\\p{S}\\s]+", "")
        .replaceAll("(.)\\1{3,}", "$1");
    if (value.isBlank()||value.length()==1) return true;
    return Set.of(
        "네", "예", "응", "어", "음", "아", "어어", "음음", "아아", "그", "저",
        "잠시만", "잠깐만", "잠깐", "그러게", "맞아요", "그렇죠", "알겠습니다",
        "okay", "ok", "uh", "um", "hmm").contains(value);
  }

  private static AdvisorFrame advisor(Profile profile,List<Segment> selected) {
    String context=(profile.lowerQuestion+" "+selected.stream().limit(20).map(Segment::text).reduce("",(a,b)->a+" "+b)).toLowerCase(Locale.ROOT);
    if (containsAny(context,List.of("api","서버","코드","개발","배포","데이터","모델","llm","토큰","버그","보안","아키텍처"))) {
      return new AdvisorFrame(
          "기술 검토자",
          "회의에서 드러난 기술 목표와 제약을 바탕으로 구현 선택지와 놓친 실패 조건을 제안한다.",
          List.of("구현 가능성","경계 조건","보안과 운영","검증 방법"));
    }
    if (profile.intents.contains(Intent.ISSUE)) {
      return new AdvisorFrame(
          "리스크 검토자",
          "확인된 이슈와 일반적인 실패 패턴을 구분해 누락된 위험과 완화책을 제안한다.",
          List.of("영향","발생 가능성","조기 신호","완화책"));
    }
    if (profile.intents.stream().anyMatch(Set.of(Intent.SCHEDULE,Intent.ACTION,Intent.STATUS)::contains)) {
      return new AdvisorFrame(
          "실행 계획 조율자",
          "일정과 담당, 의존성을 연결해 실행 가능한 다음 단계와 확인할 공백을 제안한다.",
          List.of("담당","기한","의존성","완료 기준"));
    }
    if (profile.intents.stream().anyMatch(Set.of(Intent.DECISION,Intent.RATIONALE,Intent.CHANGE)::contains)) {
      return new AdvisorFrame(
          "의사결정 검토자",
          "결정의 근거와 변경 이력을 비교하고 대안과 트레이드오프를 제안한다.",
          List.of("결정 근거","대안","트레이드오프","되돌림 조건"));
    }
    if (profile.intents.contains(Intent.OVERVIEW)) {
      return new AdvisorFrame(
          "회의 편집자",
          "흩어진 논의를 핵심 주제와 미확정 사항으로 정리한다.",
          List.of("핵심 주제","결정","미결 사항","다음 단계"));
    }
    if (containsAny(context,List.of("사용자","고객","제품","기능","서비스","시장","전략","온보딩","전환"))) {
      return new AdvisorFrame(
          "제품 전략 파트너",
          "회의의 사용자 문제와 제품 목표를 연결하고 다음 검증 질문과 선택지를 제안한다.",
          List.of("사용자 가치","가정","우선순위","검증 지표"));
    }
    return new AdvisorFrame(
        "회의 맥락 파트너",
        "질문의 의도를 회의 맥락에 맞게 해석하고 관련된 추가 관점과 확인 질문을 제안한다.",
        List.of("직접 근거","숨은 가정","추가 관점","확인 질문"));
  }

  private static boolean containsAny(String text,List<String> terms) {return terms.stream().anyMatch(text::contains);}
  private static List<String> stateSignals(String text) {
    String lower=text.toLowerCase(Locale.ROOT);var signals=new ArrayList<String>();
    for(var intent:List.of(Intent.DECISION,Intent.ACTION,Intent.ISSUE,Intent.SCHEDULE,Intent.CHANGE,Intent.RATIONALE,Intent.STATUS))
      if(containsAny(lower,intent.terms)||(intent==Intent.SCHEDULE&&DATE_OR_TIME.matcher(lower).find()))signals.add(intent.name());
    return signals;
  }
  private static List<String> intentNames(EnumSet<Intent> intents) {return intents.stream().map(Intent::name).toList();}
  private static void add(Map<Integer,Candidate> candidates,int index,Role role,int priority) {candidates.computeIfAbsent(index,Candidate::new).add(role,priority);}
}
