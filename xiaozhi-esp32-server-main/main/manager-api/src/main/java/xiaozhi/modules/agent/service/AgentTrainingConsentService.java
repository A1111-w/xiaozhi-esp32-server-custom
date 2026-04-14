package xiaozhi.modules.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.modules.agent.entity.AgentTrainingConsentEntity;

public interface AgentTrainingConsentService extends IService<AgentTrainingConsentEntity> {

    AgentTrainingConsentEntity getByUserIdAndAgentId(Long userId, String agentId);

    boolean isEnabled(Long userId, String agentId);

    AgentTrainingConsentEntity saveOrUpdateConsent(Long userId, String agentId, boolean enabled, String consentVersion);
}
