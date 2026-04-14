package xiaozhi.modules.agent.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_agent_training_sample")
public class AgentTrainingSampleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chat_history_id")
    private Long chatHistoryId;

    @TableField("user_id")
    private Long userId;

    @TableField("agent_id")
    private String agentId;

    @TableField("mac_address")
    private String macAddress;

    @TableField("session_id")
    private String sessionId;

    @TableField("chat_type")
    private Byte chatType;

    @TableField("raw_text")
    private String rawText;

    @TableField("redacted_text")
    private String redactedText;

    @TableField("has_pii")
    private Integer hasPii;

    @TableField("quality_score")
    private BigDecimal qualityScore;

    @TableField("trainable")
    private Integer trainable;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
