package com.yuegang.zhihui.common.mq;

/** Generates an unpredictable capability for each consumption claim attempt. */
@FunctionalInterface
public interface MessageClaimOwnerGenerator {

    String generate();
}
