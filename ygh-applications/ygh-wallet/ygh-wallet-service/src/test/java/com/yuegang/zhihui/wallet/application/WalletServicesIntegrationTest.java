package com.yuegang.zhihui.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.wallet.api.WalletCommand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WalletServicesIntegrationTest {
    @Test
    void supportsRechargePaymentRefundIdempotencyLedgerAndConcurrentNoOverdraft() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var wallet = new WalletService(dataSource);
            var query = new WalletQueryService(dataSource);

            assertThat(wallet.get(42).availableBalance()).isEqualByComparingTo("0.00");
            var recharge = command("recharge-1", "topup-1", "100.00", "CNY");
            var recharged = wallet.recharge(42, recharge);
            assertThat(recharged.type()).isEqualTo("RECHARGE");
            assertThat(wallet.recharge(42, recharge)).isEqualTo(recharged);
            assertThat(wallet.get(42).availableBalance()).isEqualByComparingTo("100.00");
            assertBusinessError(() -> wallet.pay(42, command("wrong-currency", "order-usd", "1.00", "USD")), ErrorCode.BUSINESS_CONFLICT);

            var payment = command("payment-1", "order-1", "35.50", "CNY");
            assertThat(wallet.pay(42, payment).type()).isEqualTo("PAYMENT");
            assertThat(wallet.pay(42, payment).transactionId()).isNotBlank();
            assertThat(wallet.get(42).availableBalance()).isEqualByComparingTo("64.50");
            assertBusinessError(() -> wallet.pay(42, command("payment-too-large", "order-2", "100.00", "CNY")), ErrorCode.BUSINESS_CONFLICT);

            var refund = command("refund-1", "order-1", "10.50", "CNY");
            assertThat(wallet.refund(42, refund).type()).isEqualTo("REFUND");
            assertThat(wallet.refund(42, refund).referenceId()).isEqualTo("order-1");
            assertThat(wallet.get(42).availableBalance()).isEqualByComparingTo("75.00");
            assertThat(query.list(42, 0)).hasSize(1);
            assertThat(query.list(42, 1000)).hasSize(3);

            wallet.recharge(84, command("seed-84", "topup-84", "10.00", "CNY"));
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 20; i++) {
                int sequence = i;
                tasks.add(() -> {
                    try {
                        wallet.pay(84, command("pay-" + sequence, "order-" + sequence, "1.00", "CNY"));
                        return true;
                    } catch (BusinessException error) {
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT);
                        return false;
                    }
                });
            }
            try (var executor = Executors.newFixedThreadPool(8)) {
                long successes = executor.invokeAll(tasks).stream().filter(future -> {
                    try { return future.get(); } catch (Exception error) { throw new AssertionError(error); }
                }).count();
                assertThat(successes).isEqualTo(10);
            }
            assertThat(wallet.get(84).availableBalance()).isEqualByComparingTo("0.00");
        }
    }

    private static WalletCommand command(String request, String reference, String amount, String currency) {
        return new WalletCommand(request, reference, new BigDecimal(amount), currency);
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
