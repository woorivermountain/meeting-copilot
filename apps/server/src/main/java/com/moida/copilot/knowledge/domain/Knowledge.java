package com.moida.copilot.knowledge.domain;
import java.util.*;
public final class Knowledge {
  private Knowledge(){}
  public record Box(UUID id,String name,String department,UUID createdBy){}
  public record Document(UUID id,UUID boxId,String title,String content){}
  public record Agent(UUID id,String name,String department){}
  public record Source(String sourceId,UUID documentId,String title,int start,int end,String quote){}
  public record ToolTrace(String name,String status,int sources){}
  public record Answer(String status,String answer,List<Source> citations,List<ToolTrace> tools,String model){}
}
