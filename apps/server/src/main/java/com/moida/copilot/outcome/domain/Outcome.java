package com.moida.copilot.outcome.domain;
import java.util.UUID;
import java.time.LocalDate;
public record Outcome(UUID id,String kind,String text,UUID ownerId,LocalDate dueDate,String status,int version) {}
