package xiaozhi.modules.agent.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dao.AgentTrainingSampleDao;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentTrainingSampleEntity;
import xiaozhi.modules.agent.service.AgentTrainingSampleService;

@Service
public class AgentTrainingSampleServiceImpl
        extends ServiceImpl<AgentTrainingSampleDao, AgentTrainingSampleEntity>
        implements AgentTrainingSampleService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern
            .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-./?%&=+#:~]+");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<![0-9Xx])[1-9]\\d{16}[0-9Xx](?![0-9Xx])");
    private static final Pattern WECHAT_PATTERN = Pattern.compile("(微信号|vx|wx)[:：\\s]*[A-Za-z][-_A-Za-z0-9]{5,19}");
    private static final Pattern PURE_PLACEHOLDER_PATTERN = Pattern.compile("^(\\[[A-Z_]+\\]\\s*)+$");
    private static final Set<String> IGNORED_TEXTS = Set.of(
            "你好",
            "小智",
            "小智小智",
            "停一下",
            "别说了",
            "小智闭嘴");

    @Override
    public void ingest(AgentChatHistoryEntity history, Long userId) {
        if (history == null || history.getId() == null || userId == null) {
            return;
        }

        String rawText = extractContent(history.getContent());
        if (StringUtils.isBlank(rawText)) {
            return;
        }

        String redactedText = redact(rawText);
        boolean hasPii = !rawText.equals(redactedText);
        boolean trainable = isTrainable(redactedText);

        AgentTrainingSampleEntity sample = AgentTrainingSampleEntity.builder()
                .chatHistoryId(history.getId())
                .userId(userId)
                .agentId(history.getAgentId())
                .macAddress(history.getMacAddress())
                .sessionId(history.getSessionId())
                .chatType(history.getChatType())
                .rawText(rawText)
                .redactedText(redactedText)
                .hasPii(hasPii ? 1 : 0)
                .qualityScore(calculateQualityScore(redactedText, trainable))
                .trainable(trainable ? 1 : 0)
                .createdAt(history.getCreatedAt() == null ? new Date() : history.getCreatedAt())
                .updatedAt(new Date())
                .build();

        save(sample);
    }

    @Override
    public long countByAgentId(String agentId, boolean trainableOnly) {
        LambdaQueryWrapper<AgentTrainingSampleEntity> wrapper = new LambdaQueryWrapper<AgentTrainingSampleEntity>()
                .eq(AgentTrainingSampleEntity::getAgentId, agentId);
        if (trainableOnly) {
            wrapper.eq(AgentTrainingSampleEntity::getTrainable, 1);
        }
        return count(wrapper);
    }

    @Override
    public List<AgentTrainingSampleEntity> listByAgentId(String agentId, boolean trainableOnly, Long startAt, Long endAt) {
        LambdaQueryWrapper<AgentTrainingSampleEntity> wrapper = new LambdaQueryWrapper<AgentTrainingSampleEntity>()
                .eq(AgentTrainingSampleEntity::getAgentId, agentId)
                .orderByAsc(AgentTrainingSampleEntity::getCreatedAt)
                .orderByAsc(AgentTrainingSampleEntity::getId);

        if (trainableOnly) {
            wrapper.eq(AgentTrainingSampleEntity::getTrainable, 1);
        }
        if (startAt != null) {
            wrapper.ge(AgentTrainingSampleEntity::getCreatedAt, new Date(startAt));
        }
        if (endAt != null) {
            wrapper.le(AgentTrainingSampleEntity::getCreatedAt, new Date(endAt));
        }
        return list(wrapper);
    }

    private String extractContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> contentMap = JsonUtils.parseObject(trimmed, Map.class);
                Object nestedContent = contentMap.get("content");
                if (nestedContent != null) {
                    return nestedContent.toString().trim();
                }
            } catch (Exception ignored) {
                // fall back to raw text
            }
        }
        return trimmed;
    }

    private String redact(String rawText) {
        String redacted = rawText;
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[PHONE]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[EMAIL]");
        redacted = URL_PATTERN.matcher(redacted).replaceAll("[URL]");
        redacted = ID_CARD_PATTERN.matcher(redacted).replaceAll("[ID_CARD]");
        redacted = WECHAT_PATTERN.matcher(redacted).replaceAll("[WECHAT]");
        return redacted;
    }

    private boolean isTrainable(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.length() < 2) {
            return false;
        }
        if (IGNORED_TEXTS.contains(normalized)) {
            return false;
        }
        return !PURE_PLACEHOLDER_PATTERN.matcher(normalized).matches();
    }

    private BigDecimal calculateQualityScore(String text, boolean trainable) {
        if (!trainable) {
            return BigDecimal.ZERO;
        }

        int length = text.trim().length();
        if (length >= 40) {
            return BigDecimal.valueOf(1.00);
        }
        if (length >= 12) {
            return BigDecimal.valueOf(0.70);
        }
        return BigDecimal.valueOf(0.30);
    }
}
