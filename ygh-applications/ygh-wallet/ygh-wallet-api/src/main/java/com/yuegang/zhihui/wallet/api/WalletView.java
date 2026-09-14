package com.yuegang.zhihui.wallet.api;import java.math.BigDecimal;public record WalletView(String userId,BigDecimal availableBalance,BigDecimal frozenBalance,String currency,long version){}
