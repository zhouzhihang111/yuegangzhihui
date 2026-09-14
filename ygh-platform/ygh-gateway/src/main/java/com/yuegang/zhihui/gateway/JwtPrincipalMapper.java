package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Converts validated JWT claims into the minimal immutable internal principal. */
@Component
final class JwtPrincipalMapper {

    private static final int MAX_AUTHORITIES = 128;
    private static final int MAX_ENCODED_AUTHORITIES = 4096;
    private static final Pattern SAFE_SUBJECT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z][A-Za-z0-9:_-]{0,127}");

    CurrentUserPrincipal map(Jwt jwt) {
        if (jwt == null) {
            throw new BadCredentialsException("JWT must not be null");
        }
        String subject = jwt.getSubject();
        if (subject == null || !SAFE_SUBJECT.matcher(subject).matches()) {
            throw new BadCredentialsException("JWT subject is missing or unsafe");
        }
        return new CurrentUserPrincipal(
                subject,
                claimSet(jwt, "roles"),
                claimSet(jwt, "permissions"));
    }

    private static Set<String> claimSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (claim == null) {
            return Set.of();
        }
        if (!(claim instanceof Collection<?> values)) {
            throw new BadCredentialsException("JWT " + claimName + " claim must be an array");
        }
        if (values.size() > MAX_AUTHORITIES) {
            throw new BadCredentialsException("JWT " + claimName + " claim exceeds count limit");
        }
        var result = new LinkedHashSet<String>(values.size());
        int encodedLength = 0;
        for (Object value : values) {
            if (!(value instanceof String authority) || !SAFE_AUTHORITY.matcher(authority).matches()) {
                throw new BadCredentialsException("JWT " + claimName + " claim contains an unsafe value");
            }
            if (result.add(authority)) {
                encodedLength += authority.length() + (result.size() == 1 ? 0 : 1);
            }
        }
        if (encodedLength > MAX_ENCODED_AUTHORITIES) {
            throw new BadCredentialsException("JWT " + claimName + " claim exceeds length limit");
        }
        return Set.copyOf(result);
    }
}
