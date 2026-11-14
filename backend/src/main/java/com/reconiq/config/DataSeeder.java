package com.reconiq.config;

import com.reconiq.entity.Order;
import com.reconiq.entity.OrderStatus;
import com.reconiq.entity.PaymentStatus;
import com.reconiq.entity.Role;
import com.reconiq.entity.Transaction;
import com.reconiq.entity.UploadBatch;
import com.reconiq.entity.User;
import com.reconiq.repository.OrderRepository;
import com.reconiq.repository.TransactionRepository;
import com.reconiq.repository.UploadBatchRepository;
import com.reconiq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the demo account on first start and, when enabled, a small dataset that
 * demonstrates every reconciliation status. Runs only when the tables are empty.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean enabled;
    private final boolean demoData;
    private final String adminEmployeeId;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      OrderRepository orderRepository,
                      TransactionRepository transactionRepository,
                      UploadBatchRepository uploadBatchRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${reconiq.seed.enabled:true}") boolean enabled,
                      @Value("${reconiq.seed.demo-data:true}") boolean demoData,
                      @Value("${reconiq.seed.admin.employee-id:ADMIN001}") String adminEmployeeId,
                      @Value("${reconiq.seed.admin.name:ReconIQ Admin}") String adminName,
                      @Value("${reconiq.seed.admin.email:admin@reconiq.local}") String adminEmail,
                      @Value("${reconiq.seed.admin.password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.uploadBatchRepository = uploadBatchRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.demoData = demoData;
        this.adminEmployeeId = adminEmployeeId;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        User admin = seedAdmin();
        if (demoData && orderRepository.count() == 0 && transactionRepository.count() == 0) {
            seedDemoData(admin);
        }
    }

    private User seedAdmin() {
        return userRepository.findByEmployeeIdIgnoreCase(adminEmployeeId).orElseGet(() -> {
            User user = new User();
            user.setEmployeeId(adminEmployeeId);
            user.setName(adminName);
            user.setEmail(adminEmail);
            user.setPassword(passwordEncoder.encode(adminPassword));
            user.setRole(Role.ADMIN);
            User saved = userRepository.save(user);
            log.info("Created demo account with employee id '{}'. Change the password before deploying.", adminEmployeeId);
            return saved;
        });
    }

    private void seedDemoData(User admin) {
        LocalDate today = LocalDate.now();

        UploadBatch batch = new UploadBatch();
        batch.setFileName("gateway_transactions_june.csv");
        batch.setFileType("CSV");
        batch.setDataType(UploadBatch.DataType.GATEWAY);
        batch.setStatus(UploadBatch.Status.COMPLETED);
        batch.setUploadedBy(admin);
        batch.setTotalRecords(13);
        batch.setImportedRecords(13);
        batch = uploadBatchRepository.save(batch);

        List<Order> orders = new ArrayList<>();
        orders.add(order("ORD1001", "Aarav Sharma", "1500.00", today.minusDays(26), "Razorpay", OrderStatus.PAID));
        orders.add(order("ORD1002", "Diya Patel", "1299.00", today.minusDays(25), "Paytm", OrderStatus.PAID));
        orders.add(order("ORD1003", "Kabir Singh", "2499.00", today.minusDays(24), "Stripe", OrderStatus.PAID));
        orders.add(order("ORD1004", "Ishita Verma", "899.00", today.minusDays(23), "Razorpay", OrderStatus.CREATED));
        orders.add(order("ORD1005", "Rohan Mehta", "450.00", today.minusDays(22), "Paytm", OrderStatus.CREATED));
        orders.add(order("ORD1006", "Ananya Rao", "3200.00", today.minusDays(21), "Razorpay", OrderStatus.PAID));
        orders.add(order("ORD1007", "Vihaan Nair", "750.00", today.minusDays(20), "Paytm", OrderStatus.CREATED));
        orders.add(order("ORD1008", "Sara Khan", "999.00", today.minusDays(18), "Stripe", OrderStatus.PAID));
        orders.add(order("ORD1009", "Aditya Joshi", "1800.00", today.minusDays(16), "Razorpay", OrderStatus.PAID));
        orders.add(order("ORD1010", "Meera Iyer", "2750.00", today.minusDays(14), "Paytm", OrderStatus.PAID));
        orders.add(order("ORD1011", "Arjun Desai", "1250.00", today.minusDays(12), "Stripe", OrderStatus.PAID));
        orders.add(order("ORD1012", "Nisha Gupta", "640.00", today.minusDays(10), "Razorpay", OrderStatus.PAID));
        orderRepository.saveAll(orders);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(txn("TXN9001", "ORD1001", "Aarav Sharma", "Razorpay", "1500.00", today.minusDays(26), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9002", "ORD1002", "Diya Patel", "Paytm", "1299.00", today.minusDays(25), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9003", "ORD1003", "Kabir Singh", "Stripe", "2399.00", today.minusDays(24), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9004", "ORD1004", "Ishita Verma", "Razorpay", "899.00", today.minusDays(23), PaymentStatus.FAILED, batch));
        transactions.add(txn("TXN9005", "ORD1005", "Rohan Mehta", "Paytm", "450.00", today.minusDays(22), PaymentStatus.PENDING, batch));
        transactions.add(txn("TXN9006", "ORD1006", "Ananya Rao", "Razorpay", "3200.00", today.minusDays(21), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9006", "ORD1006", "Ananya Rao", "Razorpay", "3200.00", today.minusDays(21), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9008", "ORD9999", "Walk-in Customer", "Razorpay", "500.00", today.minusDays(19), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9009", "ORD1008", "Sara Khan", "Stripe", "999.00", today.minusDays(18), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9010", "ORD1009", "Aditya Joshi", "Razorpay", "1800.00", today.minusDays(16), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9011", "ORD1009", "Aditya Joshi", "Razorpay", "1800.00", today.minusDays(16), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9012", "ORD1010", "Meera Iyer", "Paytm", "2750.00", today.minusDays(14), PaymentStatus.SUCCESS, batch));
        transactions.add(txn("TXN9013", "ORD1011", "Arjun Desai", "Stripe", "1200.00", today.minusDays(12), PaymentStatus.SUCCESS, batch));
        transactionRepository.saveAll(transactions);

        log.info("Seeded {} orders and {} gateway transactions for the demo dataset.",
                orders.size(), transactions.size());
    }

    private Order order(String orderId, String customer, String amount, LocalDate date,
                        String gateway, OrderStatus status) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerName(customer);
        order.setOrderAmount(new BigDecimal(amount));
        order.setOrderDate(date);
        order.setGateway(gateway);
        order.setStatus(status);
        return order;
    }

    private Transaction txn(String transactionId, String orderId, String customer, String gateway,
                            String amount, LocalDate date, PaymentStatus status, UploadBatch batch) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setCustomerName(customer);
        transaction.setGateway(gateway);
        transaction.setTransactionAmount(new BigDecimal(amount));
        transaction.setTransactionDate(date.atTime(11, 30));
        transaction.setPaymentStatus(status);
        transaction.setUploadBatch(batch);
        return transaction;
    }
}
