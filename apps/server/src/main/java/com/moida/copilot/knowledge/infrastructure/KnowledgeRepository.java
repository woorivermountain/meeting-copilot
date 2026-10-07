package com.moida.copilot.knowledge.infrastructure;
import com.moida.copilot.knowledge.domain.Knowledge.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class KnowledgeRepository {
  private final JdbcClient db;public KnowledgeRepository(JdbcClient db){this.db=db;}
  public List<Box> boxes(UUID team,UUID user){return db.sql("select b.id,b.name,b.department,b.created_by from app_knowledge_boxes b where b.team_id=:team and (b.created_by=:user or exists(select 1 from knowledge_grants g where g.box_id=b.id and g.user_id=:user)) order by b.name").param("team",team).param("user",user).query(Box.class).list();}
  public boolean allowed(UUID team,UUID box,UUID user){return boxes(team,user).stream().anyMatch(b->b.id().equals(box));}
  public boolean owns(UUID team,UUID box,UUID user){return boxes(team,user).stream().anyMatch(b->b.id().equals(box)&&b.createdBy().equals(user));}
  public UUID createBox(UUID team,UUID user,String name,String department){UUID id=UUID.randomUUID();db.sql("insert into app_knowledge_boxes(id,team_id,name,department,created_by) values(:id,:team,:name,:department,:user)").param("id",id).param("team",team).param("name",name).param("department",department).param("user",user).update();return id;}
  public void grant(UUID box,UUID user){if(db.sql("select count(*) from knowledge_grants where box_id=:box and user_id=:user").param("box",box).param("user",user).query(Long.class).single()==0)db.sql("insert into knowledge_grants(box_id,user_id) values(:box,:user)").param("box",box).param("user",user).update();}
  public void revoke(UUID box,UUID user){db.sql("delete from knowledge_grants where box_id=:box and user_id=:user").param("box",box).param("user",user).update();}
  public List<UUID> grants(UUID box){return db.sql("select user_id from knowledge_grants where box_id=:box").param("box",box).query(UUID.class).list();}
  public List<Document> documents(UUID team,UUID user){return db.sql("select d.id,d.box_id,d.title,d.content from app_knowledge_documents d join app_knowledge_boxes b on b.id=d.box_id where b.team_id=:team and (b.created_by=:user or exists(select 1 from knowledge_grants g where g.box_id=b.id and g.user_id=:user)) order by d.created_at desc").param("team",team).param("user",user).query(Document.class).list();}
  public UUID document(UUID box,String title,String content,UUID user){UUID id=UUID.randomUUID();db.sql("insert into app_knowledge_documents(id,box_id,title,content,approved_by,created_at) values(:id,:box,:title,:content,:user,current_timestamp)").param("id",id).param("box",box).param("title",title).param("content",content).param("user",user).update();return id;}
  public List<Agent> agents(UUID team){return db.sql("select id,name,department from app_knowledge_agents where team_id=:team order by name").param("team",team).query(Agent.class).list();}
  public List<UUID> agentBoxes(UUID agent){return db.sql("select box_id from app_agent_boxes where agent_id=:agent").param("agent",agent).query(UUID.class).list();}
  public UUID agent(UUID team,String name,String department,List<UUID> boxes){UUID id=UUID.randomUUID();db.sql("insert into app_knowledge_agents(id,team_id,name,department) values(:id,:team,:name,:department)").param("id",id).param("team",team).param("name",name).param("department",department).update();for(UUID box:new HashSet<>(boxes))db.sql("insert into app_agent_boxes(team_id,agent_id,box_id) values(:team,:agent,:box)").param("team",team).param("agent",id).param("box",box).update();return id;}
  public void audit(UUID team,UUID user,String operation,UUID resource){db.sql("insert into app_audit(id,team_id,actor_id,operation,resource_id,created_at) values(:id,:team,:user,:operation,:resource,current_timestamp)").param("id",UUID.randomUUID()).param("team",team).param("user",user).param("operation",operation).param("resource",resource).update();}
}
