package com.yuegang.zhihui.user.api;

import jakarta.validation.constraints.*;

public record UpdateAddressRequest(
        @Size(max = 32) String label,
        @NotBlank @Size(max = 80) String recipientName,
        @NotBlank @Pattern(regexp = "^[0-9+() -]{6,24}$") String recipientPhone,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @Size(max = 16) String provinceCode,
        @NotBlank @Size(max = 64) String provinceName,
        @NotBlank @Size(max = 64) String cityName,
        @NotBlank @Size(max = 64) String districtName,
        @NotBlank @Size(max = 500) String addressDetail,
        @Size(max = 16) String postalCode,
        boolean defaultAddress,
        @PositiveOrZero long version
) {
    @Override public String toString() { return "UpdateAddressRequest[pii=REDACTED,version=" + version + "]"; }
}
