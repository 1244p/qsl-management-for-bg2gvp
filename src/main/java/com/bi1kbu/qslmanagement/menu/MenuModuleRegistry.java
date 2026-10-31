package com.bi1kbu.qslmanagement.menu;

import java.util.List;
import java.util.Optional;
public class MenuModuleRegistry {

    private static final String PREFIX = "plugin:qsl-management:";

    private final List<MenuModuleDefinition> modules = List.of(
        module(
            "overview-dashboard",
            "总览看板",
            List.of(
                view("qso-record"),
                view("card-record"),
                view("mail-send-confirm"),
                view("mail-receive-confirm")
            ),
            List.of(view("overview-dashboard"))
        ),
        module("system-settings", "系统参数", List.of(), List.of(view("system-settings"))),
        module(
            "station-profile",
            "通信地址、本台设备、本台卡片",
            List.of(view("equipment-catalog")),
            List.of(view("station-profile"), view("equipment-catalog"))
        ),
        module(
            "qso-record",
            "通联记录",
            List.of(view("equipment-catalog"), view("station-profile")),
            List.of(view("qso-record"))
        ),
        module(
            "card-record",
            "卡片记录",
            List.of(view("qso-record"), view("station-profile")),
            List.of(view("card-record"), view("qso-record"))
        ),
        module(
            "qsl-card-request",
            "实体卡申请审核",
            List.of(view("qso-record"), view("card-record"), view("address-bureau")),
            List.of(view("qsl-card-request"), edit("card-record"), view("address-bureau"))
        ),
        module(
            "card-issue",
            "制卡签发",
            List.of(view("card-record"), view("address-bureau")),
            List.of(view("card-issue"), view("card-record"), view("address-bureau"))
        ),
        module(
            "mail-send-confirm",
            "发信确认",
            List.of(view("card-record")),
            List.of(view("mail-send-confirm"), view("card-record"))
        ),
        module(
            "mail-receive-confirm",
            "送达确认",
            List.of(view("card-record"), view("qso-record")),
            List.of(view("mail-receive-confirm"), view("card-record"), view("qso-record"))
        ),
        module(
            "card-mutation",
            "卡片异动",
            List.of(view("card-record"), view("qso-record"), view("address-bureau")),
            List.of(view("card-mutation"), edit("card-record"), view("qso-record"), view("address-bureau"))
        ),
        module(
            "exchange-request-review",
            "换卡申请审核",
            List.of(view("address-bureau"), view("card-record")),
            List.of(view("exchange-request-review"), edit("card-record"))
        ),
        module("qso-query", "通联记录查询", List.of(view("qso-record")), List.of(view("qso-query"))),
        module("card-query", "卡片记录查询", List.of(view("card-record")), List.of(view("card-query"))),
        module(
            "report-auditlog",
            "统计报表、审计日志",
            List.of(
                view("qso-query"),
                view("card-query"),
                view("exchange-request-review"),
                view("mail-send-confirm"),
                view("mail-receive-confirm")
            ),
            List.of(view("report-auditlog"))
        ),
        module("address-bureau", "地址管理、卡片局管理", List.of(), List.of(view("address-bureau"))),
        module("equipment-catalog", "设备库维护", List.of(), List.of(view("equipment-catalog"))),
        module(
            "import-export",
            "导入导出",
            List.of(
                view("qso-query"),
                view("card-query"),
                view("exchange-request-review"),
                view("address-bureau"),
                view("equipment-catalog")
            ),
            List.of(
                view("import-export"),
                edit("qso-record"),
                edit("card-record"),
                edit("exchange-request-review"),
                edit("address-bureau"),
                edit("equipment-catalog")
            )
        ),
        module(
            "log-sync",
            "外部日志同步",
            List.of(view("qso-record")),
            List.of(view("log-sync"), view("qso-record"))
        )
    );

    public List<MenuModuleDefinition> list() {
        return modules;
    }

    public Optional<MenuModuleDefinition> findByKey(String key) {
        return modules.stream().filter(module -> module.key().equals(key)).findFirst();
    }

    private static MenuModuleDefinition module(
        String key,
        String title,
        List<String> viewDependencies,
        List<String> editDependencies
    ) {
        return new MenuModuleDefinition(
            key,
            title,
            view(key),
            edit(key),
            List.copyOf(viewDependencies),
            List.copyOf(editDependencies)
        );
    }

    private static String view(String key) {
        return PREFIX + key + ":view";
    }

    private static String edit(String key) {
        return PREFIX + key + ":edit";
    }
}
