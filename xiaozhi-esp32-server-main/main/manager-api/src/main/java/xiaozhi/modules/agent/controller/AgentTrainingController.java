package xiaozhi.modules.agent.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.Enums.AgentChatHistoryType;
import xiaozhi.modules.agent.dto.AgentTrainingConsentDTO;
import xiaozhi.modules.agent.entity.AgentTrainingConsentEntity;
import xiaozhi.modules.agent.entity.AgentTrainingSampleEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.AgentTrainingConsentService;
import xiaozhi.modules.agent.service.AgentTrainingSampleService;
import xiaozhi.modules.agent.vo.AgentTrainingConsentVO;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "训练素材管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/agent")
public class AgentTrainingController {
    private final AgentService agentService;
    private final AgentTrainingConsentService agentTrainingConsentService;
    private final AgentTrainingSampleService agentTrainingSampleService;

    @GetMapping("/{id}/training-consent")
    @Operation(summary = "获取训练素材授权状态")
    @RequiresPermissions("sys:role:normal")
    public Result<AgentTrainingConsentVO> getTrainingConsent(@PathVariable("id") String agentId) {
        UserDetail user = SecurityUser.getUser();
        if (!agentService.checkAgentPermission(agentId, user.getId())) {
            return new Result<AgentTrainingConsentVO>().error("没有权限查看该智能体的训练授权配置");
        }

        AgentTrainingConsentEntity entity = agentTrainingConsentService.getByUserIdAndAgentId(user.getId(), agentId);
        AgentTrainingConsentVO vo = new AgentTrainingConsentVO();
        vo.setEnabled(entity != null && Integer.valueOf(1).equals(entity.getEnabled()));
        vo.setConsentVersion(entity == null ? "v1" : entity.getConsentVersion());
        vo.setUpdatedAt(entity == null ? null : entity.getUpdatedAt());
        vo.setSampleCount(agentTrainingSampleService.countByAgentId(agentId, false));
        vo.setTrainableSampleCount(agentTrainingSampleService.countByAgentId(agentId, true));
        return new Result<AgentTrainingConsentVO>().ok(vo);
    }

    @PutMapping("/{id}/training-consent")
    @Operation(summary = "更新训练素材授权状态")
    @RequiresPermissions("sys:role:normal")
    public Result<AgentTrainingConsentVO> updateTrainingConsent(@PathVariable("id") String agentId,
            @Valid @RequestBody AgentTrainingConsentDTO dto) {
        UserDetail user = SecurityUser.getUser();
        if (!agentService.checkAgentPermission(agentId, user.getId())) {
            return new Result<AgentTrainingConsentVO>().error("没有权限修改该智能体的训练授权配置");
        }

        AgentTrainingConsentEntity entity = agentTrainingConsentService.saveOrUpdateConsent(
                user.getId(),
                agentId,
                Boolean.TRUE.equals(dto.getEnabled()),
                dto.getConsentVersion());

        AgentTrainingConsentVO vo = new AgentTrainingConsentVO();
        vo.setEnabled(Integer.valueOf(1).equals(entity.getEnabled()));
        vo.setConsentVersion(entity.getConsentVersion());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setSampleCount(agentTrainingSampleService.countByAgentId(agentId, false));
        vo.setTrainableSampleCount(agentTrainingSampleService.countByAgentId(agentId, true));
        return new Result<AgentTrainingConsentVO>().ok(vo);
    }

    @GetMapping("/{id}/training/export")
    @Operation(summary = "导出训练样本 JSONL")
    @RequiresPermissions("sys:role:normal")
    public void exportTrainingSamples(@PathVariable("id") String agentId,
            @RequestParam(value = "trainableOnly", defaultValue = "true") boolean trainableOnly,
            @RequestParam(value = "startAt", required = false) Long startAt,
            @RequestParam(value = "endAt", required = false) Long endAt,
            HttpServletResponse response) throws IOException {
        UserDetail user = SecurityUser.getUser();
        if (!agentService.checkAgentPermission(agentId, user.getId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "没有权限导出该智能体的训练样本");
            return;
        }

        List<AgentTrainingSampleEntity> samples = agentTrainingSampleService.listByAgentId(agentId, trainableOnly, startAt,
                endAt);

        Map<String, List<AgentTrainingSampleEntity>> sessionMap = new LinkedHashMap<>();
        for (AgentTrainingSampleEntity sample : samples) {
            sessionMap.computeIfAbsent(sample.getSessionId(), key -> new ArrayList<>()).add(sample);
        }

        String fileName = URLEncoder.encode(
                "training-samples-" + agentId + "-" + LocalDate.now() + ".jsonl",
                StandardCharsets.UTF_8);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/x-ndjson;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

        for (Map.Entry<String, List<AgentTrainingSampleEntity>> entry : sessionMap.entrySet()) {
            List<Map<String, String>> messages = new ArrayList<>();
            for (AgentTrainingSampleEntity sample : entry.getValue()) {
                Map<String, String> message = new LinkedHashMap<>();
                message.put("role",
                        sample.getChatType() == AgentChatHistoryType.USER.getValue() ? "user" : "assistant");
                message.put("content", sample.getRedactedText());
                messages.add(message);
            }

            if (messages.size() < 2) {
                continue;
            }

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("session_id", entry.getKey());
            record.put("agent_id", agentId);
            record.put("messages", messages);

            response.getWriter().write(JsonUtils.toJsonString(record));
            response.getWriter().write('\n');
        }
        response.getWriter().flush();
    }
}
