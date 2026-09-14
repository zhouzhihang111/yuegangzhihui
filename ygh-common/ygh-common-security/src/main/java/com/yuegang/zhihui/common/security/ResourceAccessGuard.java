package com.yuegang.zhihui.common.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;

/**
 * Enforces resource ownership with an explicit permission for cross-owner access.
 * A role name such as ADMIN never bypasses ownership by itself.
 */
public final class ResourceAccessGuard {

    public void requireOwnerOrPermission(
            CurrentUserPrincipal principal,
            String ownerUserId,
            String crossOwnerPermission
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        boolean owner = ownerUserId != null && ownerUserId.equals(principal.userId());
        boolean explicitlyAllowed = principal.hasPermission(crossOwnerPermission);
        if (!owner && !explicitlyAllowed) {
            throw new PermissionDeniedException();
        }
    }

    public <I> void requireOwnerOrPermission(
            CurrentUserPrincipal principal,
            I resourceId,
            ResourceOwnershipChecker<I> ownershipChecker,
            String crossOwnerPermission
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (ownershipChecker == null) {
            throw new IllegalArgumentException("ownershipChecker must not be null");
        }

        boolean owner = ownershipChecker.isOwner(principal, resourceId);
        boolean explicitlyAllowed = principal.hasPermission(crossOwnerPermission);
        if (!owner && !explicitlyAllowed) {
            throw new PermissionDeniedException();
        }
    }
}
