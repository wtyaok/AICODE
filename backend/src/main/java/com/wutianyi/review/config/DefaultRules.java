package com.wutianyi.review.config;

import com.wutianyi.review.model.ReviewRule;
import com.wutianyi.review.repo.RuleRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultRules implements CommandLineRunner {
    private final RuleRepository repository;

    public DefaultRules(RuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        List<ReviewRule> defaults = List.of(
            rule(
                "hardcoded-secret", "疑似硬编码凭证", "critical",
                "(password|secret|api[_-]?key|token)\\s*[:=]\\s*['\"][^'\"]+['\"]",
                "使用密钥管理服务或环境变量，不要将凭证提交到仓库。"
            ),
            rule(
                "sql-concat", "SQL 拼接风险", "critical",
                "(select|insert|update|delete)\\b.*(\\+|\\$\\{|concat\\()",
                "使用参数化查询，避免将用户输入拼接到 SQL 中。"
            ),
            rule(
                "empty-catch", "空异常处理", "medium",
                "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}",
                "记录异常上下文并明确处理，不要静默吞掉异常。"
            ),
            rule(
                "optional-dereference", "疑似未检查空值", "medium",
                "\\b[A-Za-z_$][\\w$]*\\.get\\(\\)\\s*\\.",
                "检查 Optional 是否有值，并确认访问结果前已有明确的空值处理。"
            ),
            rule(
                "null-return", "疑似空接口返回", "medium",
                "\\breturn\\s+(null|None|undefined)\\s*;?\\s*$",
                "确认接口返回契约，使用明确的错误响应或约定的空结果结构。"
            ),
            rule(
                "open-permission", "权限放开配置待确认", "high",
                "permitAll\\s*\\(|allowAll\\s*\\(|@PermitAll\\b",
                "核对接口是否允许匿名访问，并补充权限测试。"
            ),
            rule(
                "duplicate-block", "重复新增代码片段", "low",
                "\\S",
                "抽取公共方法或组件，并确认重复逻辑是否确有必要。"
            ),
            rule(
                "console-log", "调试日志残留", "low",
                "console\\.log\\s*\\(",
                "使用结构化日志，并确认不输出敏感数据。"
            ),
            rule(
                "todo", "待处理逻辑", "low",
                "\\b(TODO|FIXME)\\b",
                "确认待办是否允许进入目标分支。"
            )
        );
        for (ReviewRule rule : defaults) {
            if (!repository.existsById(rule.id)) {
                repository.save(rule);
            }
        }
    }

    private ReviewRule rule(
        String id, String name, String severity,
        String pattern, String suggestion
    ) {
        ReviewRule rule = new ReviewRule();
        rule.id = id;
        rule.name = name;
        rule.severity = severity;
        rule.pattern = pattern;
        rule.suggestion = suggestion;
        return rule;
    }
}
