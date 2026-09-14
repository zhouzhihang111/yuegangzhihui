package com.yuegang.zhihui.common.mybatis;

/** Supplies the stable external identifier recorded in persistence audit fields. */
@FunctionalInterface
public interface AuditorProvider {

    String SYSTEM_AUDITOR = "SYSTEM";

    String currentAuditor();

    static AuditorProvider system() {
        return () -> SYSTEM_AUDITOR;
    }
}
