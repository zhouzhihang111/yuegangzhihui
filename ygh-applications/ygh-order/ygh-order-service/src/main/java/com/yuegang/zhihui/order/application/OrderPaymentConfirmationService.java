package com.yuegang.zhihui.order.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.order.api.OrderStatus;
import com.yuegang.zhihui.order.api.OrderView;
import com.yuegang.zhihui.order.infrastructure.CommerceReconciliationClient;
import java.math.BigDecimal;

public final class OrderPaymentConfirmationService {
    private final OrderService orders;
    private final OrderInventoryFacade inventory;
    private final CommerceReconciliationClient reconciliation;

    public OrderPaymentConfirmationService(
            OrderService orders,
            OrderInventoryFacade inventory,
            CommerceReconciliationClient reconciliation
    ) {
        this.orders = orders;
        this.inventory = inventory;
        this.reconciliation = reconciliation;
    }

    public OrderView confirmWalletPayment(long userId, String orderId) {
        OrderView current = orders.get(userId, orderId);
        if (current.status() != OrderStatus.PENDING_PAYMENT) {
            return current;
        }
        var wallet = reconciliation.wallet(orderId);
        if (!wallet.paymentSucceeded()
                || !Long.toString(userId).equals(wallet.userId())
                || wallet.paidAmount().compareTo(new BigDecimal(current.totalAmount().toPlainString())) < 0
                || !current.currency().equals(wallet.currency())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        }
        inventory.paymentSucceeded("wallet-payment-" + orderId, orderId);
        return orders.get(userId, orderId);
    }
}
