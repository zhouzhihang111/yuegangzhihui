import type { AxiosInstance } from "axios";
import { apiData, browserDeviceId } from "./http";
import type {
    AuthenticationResult,
    CaptchaChallenge,
    JwtIdentity,
    SessionUser,
    TokenPair,
} from "./types";

export interface LoginCommand {
    principal: string;
    password: string;
    captchaChallengeId?: string;
    captchaAnswer?: string;
}

export interface RegisterCommand {
    principal: string;
    password: string;
    confirmPassword: string;
    captchaChallengeId: string;
    captchaAnswer: string;
    agreementAccepted: boolean;
}

export async function fetchCaptcha(
    client: AxiosInstance,
): Promise<CaptchaChallenge> {
    return apiData(await client.get("/api/v1/auth/captcha"));
}

export async function login(
    client: AxiosInstance,
    command: LoginCommand,
): Promise<AuthenticationResult> {
    return apiData(
        await client.post("/api/v1/auth/login", {
            ...command,
            deviceId: browserDeviceId(),
        }),
    );
}

export async function register(
    client: AxiosInstance,
    command: RegisterCommand,
): Promise<AuthenticationResult> {
    return apiData(await client.post("/api/v1/auth/register", command));
}

export async function requestPasswordReset(
    client: AxiosInstance,
    command: {
        principal: string;
        captchaChallengeId: string;
        captchaAnswer: string;
    },
): Promise<{ expiresInSeconds: number }> {
    return apiData(
        await client.post("/api/v1/auth/password-reset/request", command),
    );
}

export async function confirmPasswordReset(
    client: AxiosInstance,
    command: {
        resetToken: string;
        newPassword: string;
        confirmPassword: string;
    },
): Promise<void> {
    await client.post("/api/v1/auth/password-reset/confirm", command);
}

export async function changePassword(
    client: AxiosInstance,
    command: {
        currentPassword: string;
        newPassword: string;
        confirmPassword: string;
    },
): Promise<void> {
    await client.put("/api/v1/auth/password", command);
}

export async function refresh(
    client: AxiosInstance,
    refreshToken: string,
): Promise<TokenPair> {
    return apiData(
        await client.post("/api/v1/auth/refresh", {
            refreshToken,
            deviceId: browserDeviceId(),
        }),
    );
}

export async function logout(
    client: AxiosInstance,
    refreshToken: string,
): Promise<void> {
    await client.post("/api/v1/auth/logout", { refreshToken });
}

export function identityFromAccessToken(accessToken: string): JwtIdentity {
    const segments = accessToken.split(".");
    if (segments.length !== 3) throw new Error("Access token is not a JWT");
    const payload = segments[1];
    if (!payload) throw new Error("JWT payload is missing");
    const encoded = payload.replaceAll("-", "+").replaceAll("_", "/");
    const padded = encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "=");
    const claims = JSON.parse(atob(padded)) as Record<string, unknown>;
    if (typeof claims.sub !== "string")
        throw new Error("JWT subject is missing");
    return {
        subject: claims.sub,
        roles: stringArray(claims.roles),
        permissions: stringArray(claims.permissions),
    };
}

export function sessionUserFromAuthentication(
    result: AuthenticationResult,
    principal: string,
    displayName = principal,
): SessionUser {
    const identity = identityFromAccessToken(result.tokens.accessToken);
    if (identity.subject !== result.userId)
        throw new Error("Authentication identity mismatch");
    return {
        id: result.userId,
        username: principal,
        displayName,
        roles: identity.roles,
        permissions: identity.permissions,
    };
}

function stringArray(value: unknown): string[] {
    return Array.isArray(value)
        ? value.filter((item): item is string => typeof item === "string")
        : [];
}
