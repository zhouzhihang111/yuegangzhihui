package com.yuegang.zhihui.auth.domain;

public interface CompromisedPasswordChecker {
    boolean isCompromised(char[] password);

    String datasetVersion();
}
