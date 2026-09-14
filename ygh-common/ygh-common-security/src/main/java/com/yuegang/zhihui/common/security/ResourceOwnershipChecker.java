package com.yuegang.zhihui.common.security;

/** Domain extension point for checking whether the current user owns a resource. */
@FunctionalInterface
public interface ResourceOwnershipChecker<I> {

    boolean isOwner(CurrentUserPrincipal principal, I resourceId);
}
