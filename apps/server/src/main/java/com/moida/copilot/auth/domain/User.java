package com.moida.copilot.auth.domain;
import java.util.UUID;
public record User(UUID id, String email, String name, String passwordHash) {
  public Profile profile() { return new Profile(id,email,name); }
  public record Profile(UUID id,String email,String name) {}
}
