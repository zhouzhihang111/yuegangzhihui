package com.yuegang.zhihui.user.api;

import java.time.OffsetDateTime;

public record AddressView(
        String id, String label, String recipientName, String recipientPhone,
        String countryCode, String provinceCode, String provinceName, String cityName,
        String districtName, String addressDetail, String postalCode, boolean defaultAddress,
        long version, OffsetDateTime updatedAt
) { }
