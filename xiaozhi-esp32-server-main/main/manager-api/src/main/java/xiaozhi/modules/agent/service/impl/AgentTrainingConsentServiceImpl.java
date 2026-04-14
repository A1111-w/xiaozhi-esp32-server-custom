package xiaozhi.modules.agent.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.modules.agent.dao.AgentTrainingConsentDao;
import xiaozhi.modules.agent.entity.AgentTrainingConsentEntity;
import xiaozhi.modules.agent.service.AgentTrainingConsentService;

@Service
public class AgentTrainingConsentServiceImpl
        extends ServiceImpl<AgentTrainingConsentDao, AgentTrainingConsentEntity>
        implements AgentTrainingConsentService {

    @Override
    public AgentTrainingConsentEntity getByUserIdAndAgentId(Long userId, String agentId) {
        if (userId == null || agentId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<AgentTrainingConsentEntity>()
                .eq(AgentTrainingConsentEntity::getUserId, userId)
                .eq(AgentTrainingConsentEntity::getAgentId, agentId)
                .last("limit 1"));
    }

    @Override
    public boolean isEnabled(Long userId, String agentId) {
        AgentTrainingConsentEntity entity = getByUserIdAndAgentId(userId, agentId);
        return entity != null && Integer.valueOf(1).equals(entity.getEnabled());
    }

    @Override
    public AgentTrainingConsentEntity saveOrUpdateConsent(Long userId, String agentId, boolean enabled,
            String consentVersion) {
        AgentTrainingConsentEntity entity = getByUserIdAndAgentId(userId, agentId);
        Date now = new Date();
        if (entity == null) {
            entity = new AgentTrainingConsentEntity();
            entity.setUserId(userId);
            entity.setAgentId(agentId);
            entity.setCreatedAt(now);
        }
        entity.setEnabled(enabled ? 1 : 0);
        entity.setConsentVersion((consentVersion == null || consentVersion.isBlank()) ? "v1" : consentVersion);
        entity.setUpdatedAt(now);

        if (entity.getId() == null) {
            save(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }
}
