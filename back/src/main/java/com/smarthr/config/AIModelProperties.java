/**
 * AI 模型配置属性类
 * 简化版本 - 仅支持阿里云百炼模型
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart-hr.ai")//读取yml文件
public class AIModelProperties {

    private String defaultModel = "aliyun";

    private AliyunConfig aliyun = new AliyunConfig();
    private OpenAIConfig openai = new OpenAIConfig();

    @Data
    public static class AliyunConfig {
        private boolean enabled = true;
    }

    @Data
    public static class OpenAIConfig {
        private boolean enabled = true;
    }
}
