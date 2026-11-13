package com.reconiq.service;

import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Transaction;
import com.reconiq.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayCsvParserTest {

    private final GatewayCsvParser parser = new GatewayCsvParser();

    @Test
    void parsesAValidGatewayFileAndNormalisesValues() throws Exception {
        String csv = """
                order_id,transaction_id,customer_name,gateway,transaction_amount,transaction_date,payment_status
                ORD1001,TXN9001,"Sharma, Aarav",Razorpay,"₹1,500.00",01/06/2026 10:15,paid
                """;

        GatewayCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(result.transactions()).hasSize(1);

        Transaction transaction = result.transactions().get(0);
        assertThat(transaction.getOrderId()).isEqualTo("ORD1001");
        assertThat(transaction.getTransactionId()).isEqualTo("TXN9001");
        assertThat(transaction.getCustomerName()).isEqualTo("Sharma, Aarav");
        assertThat(transaction.getTransactionAmount()).isEqualByComparingTo("1500.00");
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getTransactionDate()).hasHour(10).hasMinute(15);
    }

    @Test
    void acceptsAlternativeHeaderNamesAndUtf8Bom() throws Exception {
        String csv = "\uFEFFOrder ID,Transaction ID,Gateway,Amount,Date,Status\n"
                + "ORD2001,TXN2001,Paytm,999,2026-06-02,SUCCESS\n";

        GatewayCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.errors()).isEmpty();
        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().get(0).getTransactionAmount()).isEqualByComparingTo("999.00");
    }

    @Test
    void keepsValidRowsAndReportsInvalidOnes() throws Exception {
        String csv = """
                order_id,transaction_id,gateway,transaction_amount,transaction_date,payment_status
                ORD1,T1,Razorpay,500,2026-06-01,SUCCESS
                ORD2,,Razorpay,500,2026-06-01,SUCCESS
                ORD3,T3,Razorpay,not-a-number,2026-06-01,SUCCESS
                ORD4,T4,Razorpay,500,2026-06-01,REFUNDED
                """;

        GatewayCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.transactions()).hasSize(1);
        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors().get(0)).contains("Row 3").contains("transaction_id is required");
        assertThat(result.errors().get(1)).contains("not a valid number");
        assertThat(result.errors().get(2)).contains("payment_status");
    }

    @Test
    void rejectsFilesWithoutRequiredColumns() {
        String csv = "order_id,transaction_id,gateway\nORD1,T1,Razorpay\n";

        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing required column(s)")
                .hasMessageContaining("transaction_amount");
    }

    @Test
    void rejectsEmptyFiles() {
        assertThatThrownBy(() -> parser.parse(stream("")))
                .isInstanceOf(BadRequestException.class);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
