package com.yuegang.zhihui.wallet.api;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record WalletAdminTransactionView(String transactionId,String userId,String type,String status,BigDecimal amount,String currency,String referenceId,OffsetDateTime createdAt) {}
