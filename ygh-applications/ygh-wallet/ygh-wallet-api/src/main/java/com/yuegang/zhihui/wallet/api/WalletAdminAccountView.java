package com.yuegang.zhihui.wallet.api;
import java.math.BigDecimal;
public record WalletAdminAccountView(String userId, BigDecimal availableBalance, BigDecimal frozenBalance, String currency, String status, long version) {}
