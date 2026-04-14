package xiaozhi.modules.agent.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "训练素材授权更新参数")
public class AgentTrainingConsentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "是否允许将匿名化对话用于模型优化", example = "true")
    private Boolean enabled;

    @Schema(description = "授权版本", example = "v1")
    private String consentVersion;
}
