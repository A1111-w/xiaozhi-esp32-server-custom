package xiaozhi.modules.agent.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentTrainingSampleEntity;

public interface AgentTrainingSampleService extends IService<AgentTrainingSampleEntity> {

    void ingest(AgentChatHistoryEntity history, Long userId);

    long countByAgentId(String agentId, boolean trainableOnly);

    List<AgentTrainingSampleEntity> listByAgentId(String agentId, boolean trainableOnly, Long startAt, Long endAt);
}
