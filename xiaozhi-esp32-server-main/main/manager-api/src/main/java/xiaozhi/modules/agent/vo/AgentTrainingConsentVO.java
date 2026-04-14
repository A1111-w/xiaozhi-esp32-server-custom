package xiaozhi.modules.agent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "训练素材授权信息")
public class AgentTrainingConsentVO {

    @Schema(description = "是否允许将匿名化对话用于模型优化")
    private Boolean enabled;

    @Schema(description = "授权版本")
    private String consentVersion;

    @Schema(description = "最后更新时间")
    private Date updatedAt;

    @Schema(description = "已入库样本数")
    private Long sampleCount;

    @Schema(description = "可导出训练样本数")
    private Long trainableSampleCount;
}
