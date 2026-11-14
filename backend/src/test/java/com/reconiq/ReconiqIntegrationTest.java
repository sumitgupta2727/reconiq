package com.reconiq;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Role;
import com.reconiq.entity.Transaction;
import com.reconiq.entity.User;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.ReconciliationRepository;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.repository.UploadBatchRepository;
import com.reconiq.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the HTTP layer against an in-memory H2 database.
 *
 * <p>It exercises the paths a reviewer is most likely to try first: login, the
 * reconciliation engine, search/filter/sort, CSV and PDF export, and CSV upload
 * including the partial-import path.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconiqIntegrationTest {

    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @Autowired
    private UploadBatchRepository uploadBatchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        reconciliationRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        uploadBatchRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User analyst = new User();
        analyst.setEmployeeId("EMP001");
        analyst.setName("Priya Nair");
        analyst.setEmail("priya@reconiq.local");
        analyst.setPassword(passwordEncoder.encode(PASSWORD));
        analyst.setRole(Role.ANALYST);
        userRepository.save(analyst);

        orderRepository.saveAll(List.of(
                order("ORD-MATCH", "Aarav Sharma", "100.00", "Razorpay"),
                order("ORD-MISMATCH", "Diya Patel", "200.00", "Paytm"),
                order("ORD-MISSING", "Kabir Singh", "300.00", "Stripe"),
                order("ORD-DUP", "Ishita Verma", "400.00", "Razorpay"),
                order("ORD-FAILED", "Rohan Mehta", "500.00", "Paytm"),
                order("ORD-PENDING", "Ananya Rao", "600.00", "Stripe")));

        transactionRepository.saveAll(List.of(
                transaction("TXN-MATCH", "ORD-MATCH", "Aarav Sharma", "Razorpay", "100.00", PaymentStatus.SUCCESS),
                transaction("TXN-MISMATCH", "ORD-MISMATCH", "Diya Patel", "Paytm", "150.00", PaymentStatus.SUCCESS),
                transaction("TXN-DUP-1", "ORD-DUP", "Ishita Verma", "Razorpay", "400.00", PaymentStatus.SUCCESS),
                transaction("TXN-DUP-2", "ORD-DUP", "Ishita Verma", "Razorpay", "400.00", PaymentStatus.SUCCESS),
                transaction("TXN-FAILED", "ORD-FAILED", "Rohan Mehta", "Paytm", "500.00", PaymentStatus.FAILED),
                transaction("TXN-PENDING", "ORD-PENDING", "Ananya Rao", "Stripe", "600.00", PaymentStatus.PENDING),
                transaction("TXN-ORPHAN", "ORD-GHOST", "Walk-in Customer", "Razorpay", "75.00", PaymentStatus.SUCCESS)));
    }

    @Test
    @DisplayName("Login succeeds with valid credentials and is rejected otherwise")
    void loginFlow() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"EMP001\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("EMP001"))
                .andExpect(jsonPath("$.role").value("ANALYST"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"EMP001\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid employee ID or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reconciliation classifies every status and supports filtered search")
    void reconciliationRunAndSearch() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRecords").value(8))
                .andExpect(jsonPath("$.matched").value(2))
                .andExpect(jsonPath("$.unmatched").value(1))
                .andExpect(jsonPath("$.amountMismatch").value(1))
                .andExpect(jsonPath("$.duplicate").value(1))
                .andExpect(jsonPath("$.missing").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.matchedAmount").value(500.00));

        mockMvc.perform(get("/api/reconciliation").param("status", "MATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("MATCHED"));

        mockMvc.perform(get("/api/reconciliation").param("search", "ORD-DUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/reconciliation").param("gateway", "Razorpay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        mockMvc.perform(get("/api/reconciliation").param("status", "MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value("ORD-MISSING"))
                .andExpect(jsonPath("$.content[0].difference").value(-300.00));

        mockMvc.perform(get("/api/reconciliation/gateways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Transactions support filtering, sorting and pagination")
    void transactionSearch() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("status", "SUCCESS")
                        .param("sortBy", "transactionAmount")
                        .param("direction", "asc")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].transactionId").value("TXN-ORPHAN"))
                .andExpect(jsonPath("$.content[0].uploadBatchId").doesNotExist());

        mockMvc.perform(get("/api/transactions").param("search", "diya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value("TXN-MISMATCH"));
    }

    @Test
    @DisplayName("Reports provide summary, CSV and PDF exports")
    void reportExports() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(6))
                .andExpect(jsonPath("$.totalTransactions").value(7))
                .andExpect(jsonPath("$.totalReconciliationRecords").value(8))
                .andExpect(jsonPath("$.matchedRecords").value(2))
                .andExpect(jsonPath("$.issueRecords").value(6))
                .andExpect(jsonPath("$.statusCounts.MATCHED").value(2))
                .andExpect(jsonPath("$.gateways.length()").value(3))
                // 50 mismatch + 300 missing + 75 unmatched; duplicates/failed/pending are amount-neutral here
                .andExpect(jsonPath("$.amountAtRisk").value(425.00));

        byte[] csv = mockMvc.perform(get("/api/reports/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
                .andReturn().getResponse().getContentAsByteArray();

        String csvText = new String(csv, StandardCharsets.UTF_8);
        assertThat(csvText)
                .startsWith("\uFEFF")
                .contains("order_id,transaction_id,gateway,order_amount,transaction_amount,difference,status")
                .contains("ORD-MATCH,TXN-MATCH,Razorpay,100.00,100.00,0.00,MATCHED")
                .contains("AMOUNT_MISMATCH")
                .contains("UNMATCHED");

        byte[] pdf = mockMvc.perform(get("/api/reports/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".pdf")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    @Test
    @DisplayName("A valid CSV upload is imported and tracked as a completed batch")
    void uploadValidCsv() throws Exception {
        String csv = """
                order_id,transaction_id,customer_name,gateway,transaction_amount,transaction_date,payment_status
                UP-1,UP-TXN-1,Neha Kapoor,Razorpay,111.00,2026-06-01 10:15:00,SUCCESS
                UP-2,UP-TXN-2,Arjun Desai,Paytm,222.00,2026-06-01 10:20:00,SUCCESS
                """;

        mockMvc.perform(multipart("/api/uploads")
                        .file(csvFile("gateway-june.csv", csv))
                        .param("uploadedBy", "EMP001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("gateway-june.csv"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.importedRecords").value(2))
                .andExpect(jsonPath("$.uploadedBy").value("Priya Nair"));

        mockMvc.perform(get("/api/transactions").param("search", "UP-TXN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].uploadBatchFileName").value("gateway-june.csv"));

        mockMvc.perform(get("/api/uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("A CSV with bad rows is imported partially and keeps the audit trail")
    void uploadPartiallyInvalidCsv() throws Exception {
        String csv = """
                order_id,transaction_id,customer_name,gateway,transaction_amount,transaction_date,payment_status
                UP-3,UP-TXN-3,Neha Kapoor,Razorpay,333.00,2026-06-02 11:00:00,SUCCESS
                UP-4,UP-TXN-4,Broken Row,Paytm,not-a-number,2026-06-02 11:05:00,SUCCESS
                UP-5,UP-TXN-5,Maya Rao,Stripe,555.00,2026-06-02 11:10:00,PAID
                """;

        mockMvc.perform(multipart("/api/uploads").file(csvFile("partial.csv", csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.importedRecords").value(2))
                .andExpect(jsonPath("$.errorMessage").value(containsString("1 row(s) were skipped")));

        mockMvc.perform(get("/api/uploads").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Non-CSV uploads and malformed CSVs are rejected with a clear message")
    void uploadValidation() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(".csv")));

        String missingHeaders = "transaction_id,amount\nTXN-1,100.00\n";
        mockMvc.perform(multipart("/api/uploads").file(csvFile("bad.csv", missingHeaders)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Missing required column")));
    }

    @Test
    @DisplayName("Upload template is downloadable and matches the parser headers")
    void uploadTemplate() throws Exception {
        String template = mockMvc.perform(get("/api/uploads/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("template")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(template).contains("order_id,transaction_id,customer_name,gateway,"
                + "transaction_amount,transaction_date,payment_status");
    }

    @Test
    @DisplayName("Orders CSV upload updates existing rows and creates new ones")
    void uploadOrdersUpserts() throws Exception {
        String csv = """
                order_id,customer_name,order_amount,order_date,gateway,status
                ORD-MATCH,Aarav Sharma,123.45,2026-06-02,Razorpay,PAID
                NEW-ORD,Nikhil Rao,321.00,2026-06-02,Paytm,CREATED
                """;

        mockMvc.perform(multipart("/api/uploads/orders")
                        .file(csvFile("orders.csv", csv))
                        .param("uploadedBy", "EMP001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("orders.csv"))
                .andExpect(jsonPath("$.dataType").value("ORDERS"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.importedRecords").value(2))
                .andExpect(jsonPath("$.uploadedBy").value("Priya Nair"));

        Order updated = orderRepository.findByOrderId("ORD-MATCH").orElseThrow();
        assertThat(updated.getOrderAmount()).isEqualByComparingTo("123.45");
        assertThat(updated.getOrderDate()).hasToString("2026-06-02");

        Order created = orderRepository.findByOrderId("NEW-ORD").orElseThrow();
        assertThat(created.getCustomerName()).isEqualTo("Nikhil Rao");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(orderRepository.count()).isEqualTo(7);

        mockMvc.perform(get("/api/uploads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dataType").value("ORDERS"));
    }

    @Test
    @DisplayName("Orders CSV upload rejects duplicate order ids within one file")
    void uploadOrdersRejectsDuplicateIds() throws Exception {
        String csv = """
                order_id,customer_name,order_amount,order_date,gateway,status
                DUP-1,Neha Kapoor,500.00,2026-06-01,Razorpay,PAID
                DUP-1,Neha Kapoor,600.00,2026-06-01,Razorpay,PAID
                """;

        mockMvc.perform(multipart("/api/uploads/orders").file(csvFile("duplicate.csv", csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalRecords").value(1))
                .andExpect(jsonPath("$.importedRecords").value(1))
                .andExpect(jsonPath("$.errorMessage").value(containsString("duplicated")));

        Order saved = orderRepository.findByOrderId("DUP-1").orElseThrow();
        assertThat(saved.getOrderAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Orders template is downloadable and matches the parser headers")
    void ordersTemplate() throws Exception {
        String template = mockMvc.perform(get("/api/uploads/orders/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("orders-template")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(template).contains("order_id,customer_name,order_amount,order_date,gateway,status");
    }

    private static MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static Order order(String orderId, String customer, String amount, String gateway) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerName(customer);
        order.setOrderAmount(new BigDecimal(amount));
        order.setOrderDate(LocalDate.of(2026, 6, 1));
        order.setGateway(gateway);
        order.setStatus(OrderStatus.PAID);
        return order;
    }

    private static Transaction transaction(String transactionId, String orderId, String customer,
                                           String gateway, String amount, PaymentStatus status) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setCustomerName(customer);
        transaction.setGateway(gateway);
        transaction.setTransactionAmount(new BigDecimal(amount));
        transaction.setTransactionDate(LocalDateTime.of(2026, 6, 1, 10, 15));
        transaction.setPaymentStatus(status);
        return transaction;
    }
}
