package com.moida.copilot.auth.infrastructure;
import com.moida.copilot.auth.domain.User;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class UserRepository {
  private final JdbcClient db;
  public UserRepository(JdbcClient db) { this.db=db; }
  public Optional<User> findByEmail(String email) { return db.sql("select id,email,name,password_hash from app_users where email=:email").param("email",email).query((r,n)-> new User(r.getObject("id",UUID.class),r.getString("email"),r.getString("name"),r.getString("password_hash"))).optional(); }
  public User get(UUID id) { return db.sql("select id,email,name,password_hash from app_users where id=:id").param("id",id).query((r,n)->new User(r.getObject("id",UUID.class),r.getString("email"),r.getString("name"),r.getString("password_hash"))).single(); }
  public void insert(User u) { db.sql("insert into app_users(id,email,name,password_hash,created_at) values(:id,:email,:name,:password,current_timestamp)").param("id",u.id()).param("email",u.email()).param("name",u.name()).param("password",u.passwordHash()).update(); }
}
