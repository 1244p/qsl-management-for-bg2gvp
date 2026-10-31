package com.bi1kbu.qslmanagement.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class QslRoleTemplateContractTest {

    private static final Path ROLE_TEMPLATE_PATH = Path.of(
        "src/main/resources/extensions/qsl-menu-role-templates.yaml"
    );

    // 前端菜单权限节点清单就是控制台菜单的真实来源，据此反查权限模板是否齐备
    private static final Path MENU_MODULES_PATH = Path.of("ui/src/constants/menu-modules.ts");

    // 控制台自定义端点路由的唯一来源，据此反查每条路由是否都有授权规则
    private static final Path CONSOLE_ENDPOINT_PATH = Path.of(
        "src/main/java/com/bi1kbu/qslmanagement/api/console/QslConsoleApiEndpoint.java"
    );

    private static final String CONSOLE_API_GROUP = "console.api.qsl-management.bi1kbu.com";
    private static final String EXTENSION_API_GROUP = "qsl-management.bi1kbu.com";
    private static final String MENU_PERMISSION_PREFIX = "plugin:qsl-management:";

    @Test
    void shouldProvideRoleTemplateForEveryMenuPermission() throws IOException {
        var menuSource = Files.readString(MENU_MODULES_PATH);
        var nodeKeys = new TreeSet<String>();
        nodeKeys.addAll(matches(menuSource, "toViewPermission\\('([^']+)'\\)"));
        nodeKeys.addAll(matches(menuSource, "toEditPermission\\('([^']+)'\\)"));

        assertTrue(!nodeKeys.isEmpty(), "未从 ui/src/constants/menu-modules.ts 解析到任何权限节点");

        var uiPermissions = loadUiPermissions();
        var missing = new ArrayList<String>();
        for (var key : nodeKeys) {
            var view = MENU_PERMISSION_PREFIX + key + ":view";
            var edit = MENU_PERMISSION_PREFIX + key + ":edit";
            if (!uiPermissions.contains(view)) {
                missing.add(view);
            }
            if (!uiPermissions.contains(edit)) {
                missing.add(edit);
            }
        }

        assertTrue(
            missing.isEmpty(),
            "菜单引用了没有权限模板的节点，非超管角色会看不到对应菜单：" + missing
        );
    }

    @Test
    void shouldProvideRuleForEveryConsoleEndpointRoute() throws IOException {
        var source = Files.readString(CONSOLE_ENDPOINT_PATH);
        var matcher = Pattern.compile("\\.andRoute\\(\\s*(GET|POST|PUT|PATCH|DELETE)\\(\"(/[^\"]*)\"")
            .matcher(source);
        var rules = loadRules();
        var checked = new ArrayList<String>();

        while (matcher.find()) {
            var method = matcher.group(1);
            var path = matcher.group(2);
            var verb = switch (method) {
                case "GET" -> "get";
                case "POST" -> "create";
                case "PUT" -> "update";
                case "PATCH" -> "patch";
                default -> "delete";
            };
            var resource = path.replaceFirst("^/", "").split("/")[0];
            assertHasRule(rules, CONSOLE_API_GROUP, resource, verb);
            checked.add(method + " " + path);
        }

        assertTrue(checked.size() >= 30, "console 路由解析数量异常，疑似路由写法变更：" + checked.size());
    }

    @Test
    void shouldCoverConsoleCustomEndpointRules() throws IOException {
        var rules = loadRules();

        // QslOverviewConsoleEndpoint
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "overview", "get");

        // QslConsoleApiEndpoint
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "reports", "get");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "mail-send-confirms", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "mail-receive-confirms", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "receive-records", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "exchange-requests", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "qsl-card-requests", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "online-card-imports", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "ai-config-tests", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "ai-address-normalizations", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "ai-online-import-parses", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "qrz-credential-tests", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "qrz-address-lookups", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "notification-mails", "create");

        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "imports", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "imports", "get");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "exports", "create");
        assertHasRule(rules, "console.api.qsl-management.bi1kbu.com", "exports", "get");
    }

    @Test
    void shouldCoverPublicCustomEndpointRules() throws IOException {
        var rules = loadRules();

        // QslPublicApiEndpoint + QslOverviewPublicEndpoint
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "qso-public", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "ONLINE_EYEBALL", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "EYEBALL", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "exchange-online/bureaus", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "exchange-online/station-cards", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "exchange-offline/activities", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "exchange-online/requests", "create");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "qsl-card/qsos", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "qsl-card/station-contact", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "qsl-card/requests", "create");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "exchange-offline/confirm", "create");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "receipt-public/confirm", "create");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "receipt-public", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "overview-public", "get");
        assertHasRule(rules, "api.qsl-management.bi1kbu.com", "cards", "get");
    }

    @Test
    void shouldAggregatePublicRolesToAnonymous() throws IOException {
        var docs = loadRoleDocs();

        assertHasAnonymousAggregateRole(docs, "qsl-management-public-view");
        assertHasAnonymousAggregateRole(docs, "qsl-management-public-submit");
    }

    @Test
    void shouldProvideCardPrintToolRoleTemplate() throws IOException {
        var docs = loadRoleDocs();

        assertRoleDependsOn(
            docs,
            "qsl-management-card-print-tool",
            "qsl-management-station-profile-view",
            "qsl-management-card-record-edit",
            "qsl-management-qso-record-view",
            "qsl-management-address-bureau-view",
            "qsl-management-offline-activity-view"
        );
        assertHasRule(loadRules(), "qsl-management.bi1kbu.com", "offline-activities", "get");
        assertHasRule(loadRules(), "qsl-management.bi1kbu.com", "offline-activities", "list");
    }

    @Test
    void shouldGrantAuditLogWriteToConsoleEditRoles() throws IOException {
        var docs = loadRoleDocs();

        // 控制台前端在通联、卡片、地址、系统参数等模块内直接创建 qsl-audit-logs；
        // 缺这条规则时业务写入会先成功、审计写入再 403，页面报错但数据已改
        assertHasRule(loadRules(), "qsl-management.bi1kbu.com", "qsl-audit-logs", "create");

        for (var role : List.of(
            "qsl-management-system-settings-edit",
            "qsl-management-station-profile-edit",
            "qsl-management-qso-record-edit",
            "qsl-management-card-record-edit",
            "qsl-management-card-issue-edit",
            "qsl-management-card-mutation-edit",
            "qsl-management-mail-send-confirm-edit",
            "qsl-management-mail-receive-confirm-edit",
            "qsl-management-exchange-request-review-edit",
            "qsl-management-qso-query-edit",
            "qsl-management-address-bureau-edit",
            "qsl-management-equipment-catalog-edit"
        )) {
            assertRoleDependsOn(docs, role, "qsl-management-audit-log-write");
        }
    }

    @Test
    void shouldNotUsePluginPrefixInResources() throws IOException {
        var rules = loadRules();
        var hasPrefixedResource = rules.stream()
            .flatMap(rule -> rule.resources().stream())
            .anyMatch(resource -> resource.startsWith("qsl-management/"));

        assertTrue(!hasPrefixedResource, "resources 不应包含 qsl-management/ 前缀");
    }

    @Test
    void shouldGrantReceiveRecordDeleteToCardQueryRole() throws IOException {
        var docs = loadRoleDocs();
        var rules = loadRules();

        // 收卡记录查询页的「删除」按钮走扩展 DELETE；缺 delete 动词时非超管一律 403，
        // 且界面只显示「删除失败」，看不出是权限问题。
        assertHasRule(rules, EXTENSION_API_GROUP, "receive-records", "delete");
        // 删除成功后要写审计日志，缺这条依赖会「记录删掉了、审计没留痕」。
        assertRoleDependsOn(docs, "qsl-management-card-query-edit", "qsl-management-audit-log-write");
    }

    private void assertHasAnonymousAggregateRole(List<Map<String, Object>> docs, String roleName) {
        var matched = docs.stream().anyMatch(doc -> {
            var metadata = toMap(doc.get("metadata"));
            if (!roleName.equals(stringValue(metadata.get("name")))) {
                return false;
            }
            var labels = toMap(metadata.get("labels"));
            return "true".equals(stringValue(labels.get("rbac.authorization.halo.run/aggregate-to-anonymous")));
        });

        assertTrue(matched, "缺少 anonymous 聚合角色：" + roleName);
    }

    private void assertRoleDependsOn(List<Map<String, Object>> docs, String roleName, String... dependencies) {
        var matched = docs.stream().anyMatch(doc -> {
            var metadata = toMap(doc.get("metadata"));
            if (!roleName.equals(stringValue(metadata.get("name")))) {
                return false;
            }
            var labels = toMap(metadata.get("labels"));
            if (!"true".equals(stringValue(labels.get("halo.run/role-template")))) {
                return false;
            }
            var annotations = toMap(metadata.get("annotations"));
            var rawDependencies = stringValue(annotations.get("rbac.authorization.halo.run/dependencies"));
            for (var dependency : dependencies) {
                if (!rawDependencies.contains("\"" + dependency + "\"")) {
                    return false;
                }
            }
            return true;
        });

        assertTrue(matched, "缺少权限模板或依赖不完整：" + roleName);
    }

    private void assertHasRule(List<RoleRule> rules, String apiGroup, String resource, String verb) {
        var matched = rules.stream().anyMatch(rule ->
            rule.apiGroups().contains(apiGroup)
                && rule.resources().contains(resource)
                && rule.verbs().contains(verb)
        );
        assertTrue(matched, "权限模板缺少规则：apiGroup=" + apiGroup + ", resource=" + resource + ", verb=" + verb);
    }

    private Set<String> loadUiPermissions() throws IOException {
        var permissions = new TreeSet<String>();
        for (var doc : loadRoleDocs()) {
            var annotations = toMap(toMap(doc.get("metadata")).get("annotations"));
            var raw = stringValue(annotations.get("rbac.authorization.halo.run/ui-permissions"));
            if (raw.isBlank()) {
                continue;
            }
            permissions.addAll(toStringList(new Yaml().load(raw)));
        }
        return permissions;
    }

    private Set<String> matches(String source, String regex) {
        var matcher = Pattern.compile(regex).matcher(source);
        var result = new TreeSet<String>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private List<RoleRule> loadRules() throws IOException {
        var docs = loadRoleDocs();
        var rules = new ArrayList<RoleRule>();
        for (var doc : docs) {
            var roleRules = toList(doc.get("rules"));
            for (var roleRuleObj : roleRules) {
                var roleRule = toMap(roleRuleObj);
                rules.add(new RoleRule(
                    toStringList(roleRule.get("apiGroups")),
                    toStringList(roleRule.get("resources")),
                    toStringList(roleRule.get("verbs"))
                ));
            }
        }
        return rules;
    }

    private List<Map<String, Object>> loadRoleDocs() throws IOException {
        var yamlContent = Files.readString(ROLE_TEMPLATE_PATH);
        var yaml = new Yaml();
        var docs = new ArrayList<Map<String, Object>>();
        for (var document : yaml.loadAll(yamlContent)) {
            if (document instanceof Map<?, ?> map) {
                var normalized = new LinkedHashMap<String, Object>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                docs.add(normalized);
            }
        }
        return docs;
    }

    private Map<String, Object> toMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var normalized = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
        return normalized;
    }

    private List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record RoleRule(
        List<String> apiGroups,
        List<String> resources,
        List<String> verbs
    ) {
    }
}
