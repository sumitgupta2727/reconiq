package com.reconiq.service;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
import com.reconiq.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCsvParserTest {

    private final OrderCsvParser parser = new OrderCsvParser();

    @Test
    void parsesAValidOrdersFileAndNormalisesValues() throws Exception {
        String csv = """
                order_id,customer_name,order_amount,order_date,gateway,status
                ORD1001,"Sharma, Aarav","₹1,500.00",01/06/2026,Razorpay,paid
                """;

        OrderCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(result.orders()).hasSize(1);

        Order order = result.orders().get(0);
        assertThat(order.getOrderId()).isEqualTo("ORD1001");
        assertThat(order.getCustomerName()).isEqualTo("Sharma, Aarav");
        assertThat(order.getOrderAmount()).isEqualByComparingTo("1500.00");
        assertThat(order.getOrderDate()).hasToString("2026-06-01");
        assertThat(order.getGateway()).isEqualTo("Razorpay");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void acceptsAlternativeHeaderNamesAndUtf8Bom() throws Exception {
        String csv = "\uFEFFOrder ID,Customer Name,Amount,Date,Payment Gateway,Order Status\n"
                + "ORD2001,Riya Sen,999,2026-06-02,Paytm,created\n";

        OrderCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.errors()).isEmpty();
        assertThat(result.orders()).hasSize(1);
        assertThat(result.orders().get(0).getOrderAmount()).isEqualByComparingTo("999.00");
        assertThat(result.orders().get(0).getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void keepsValidRowsAndReportsInvalidOnes() throws Exception {
        String csv = """
                order_id,customer_name,order_amount,order_date,gateway,status
                ORD1,Neha Kapoor,500,2026-06-01,Razorpay,PAID
                ,Arjun Desai,500,2026-06-01,Razorpay,PAID
                ORD3,Maya Rao,not-a-number,2026-06-01,Stripe,PAID
                ORD4,Kabir Singh,500,not-a-date,Paytm,PAID
                ORD5,Rohan Mehta,500,2026-06-01,Paytm,UNKNOWN
                """;

        OrderCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.orders()).hasSize(1);
        assertThat(result.errors()).hasSize(4);
        assertThat(result.errors().get(0)).contains("Row 3").contains("order_id is required");
        assertThat(result.errors().get(1)).contains("not a valid number");
        assertThat(result.errors().get(2)).contains("not a supported date");
        assertThat(result.errors().get(3)).contains("status");
    }

    @Test
    void rejectsDuplicateOrderIdsInsideTheSameFile() throws Exception {
        String csv = """
                order_id,customer_name,order_amount,order_date,gateway,status
                ORD1,Neha Kapoor,500,2026-06-01,Razorpay,PAID
                ORD1,Neha Kapoor,600,2026-06-01,Razorpay,PAID
                """;

        OrderCsvParser.Result result = parser.parse(stream(csv));

        assertThat(result.orders()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Row 3").contains("duplicated");
    }

    @Test
    void rejectsFilesWithoutRequiredColumns() {
        String csv = "order_id,order_amount\nORD1,100\n";

        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Missing required column(s)")
                .hasMessageContaining("customer_name")
                .hasMessageContaining("order_date");
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
