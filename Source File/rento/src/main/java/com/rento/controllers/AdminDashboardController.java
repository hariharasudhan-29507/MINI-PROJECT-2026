package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.PaymentDAO;
import com.rento.dao.UserDAO;
import com.rento.dao.VehicleDAO;
import com.rento.models.Booking;
import com.rento.models.Payment;
import com.rento.models.Rental;
import com.rento.models.User;
import com.rento.models.Vehicle;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.AuthService;
import com.rento.services.AdminExportService;
import com.rento.utils.AlertUtil;
import com.rento.services.RentalService;
import com.rento.utils.MongoCollections;
import com.rento.utils.MongoDBConnection;
import com.rento.utils.ValidationUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Administrative dashboard for monitoring all actors and platform activity.
 */
public class AdminDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label totalUsers;
    @FXML private Label totalDrivers;
    @FXML private Label totalSuppliers;
    @FXML private Label totalCollections;
    @FXML private Label totalVehicles;
    @FXML private Label totalBookings;
    @FXML private Label totalRentals;
    @FXML private Label overduePenaltyTotal;
    @FXML private VBox userBoardSection;
    @FXML private VBox driverBoardSection;
    @FXML private VBox supplierBoardSection;
    @FXML private Pagination analyticsPagination;
    @FXML private VBox actorList;
    @FXML private VBox userManagementList;
    @FXML private VBox driverManagementList;
    @FXML private VBox supplierManagementList;
    @FXML private VBox incidentList;
    @FXML private VBox supplierChangeList;
    @FXML private Label noSupplierChangesLabel;
    @FXML private VBox bookingAdminList;
    @FXML private VBox rentalAdminList;
    @FXML private VBox vehicleAdminList;
    @FXML private VBox paymentAdminList;
    @FXML private ComboBox<String> mailRecipientCombo;
    @FXML private TextField mailSubjectField;
    @FXML private TextArea mailBodyArea;
    @FXML private TextArea systemDataArea;

    private final UserDAO userDAO = new UserDAO();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final RentalService rentalService = new RentalService();
    private final AuthService authService = new AuthService();
    private final AdminExportService adminExportService = new AdminExportService();
    private List<User> userCache = List.of();
    private List<Vehicle> vehicleCache = List.of();
    private List<Booking> bookingCache = List.of();
    private List<Rental> rentalCache = List.of();
    private List<Payment> paymentCache = List.of();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        welcomeLabel.setText("Welcome, " + SessionManager.getInstance().getCurrentUserName() + " (Administrator)");
        analyticsPagination.setPageCount(4);
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
        showUserBoard();
        loadDashboard();
    }

    private void loadDashboard() {
        List<User> users = userDAO.findAll();
        List<Vehicle> vehicles = vehicleDAO.findAll();
        List<Booking> bookings = bookingDAO.findAll();
        List<Rental> rentals = rentalService.getAllRentals();
        List<Payment> payments = paymentDAO.findAll();
        userCache = users;
        vehicleCache = vehicles;
        bookingCache = bookings;
        rentalCache = rentals;
        paymentCache = payments;

        totalUsers.setText(String.valueOf(users.stream().filter(u -> u.getRole() == User.Role.USER).count()));
        totalDrivers.setText(String.valueOf(users.stream().filter(u -> u.getRole() == User.Role.DRIVER).count()));
        totalSuppliers.setText(String.valueOf(users.stream().filter(u -> u.getRole() == User.Role.SUPPLIER).count()));
        totalCollections.setText(String.valueOf(getCollectionCounts().size()));
        totalVehicles.setText(String.valueOf(vehicles.size()));
        totalBookings.setText(String.valueOf(bookings.size()));
        totalRentals.setText(String.valueOf(rentals.size()));
        overduePenaltyTotal.setText(ValidationUtil.formatCurrency(
            rentals.stream().mapToDouble(Rental::getPenaltyAmount).sum()
        ));

        updateActorList(users, bookings, rentals);
        updateManagedActorLists(users);
        updateIncidentList(rentals, vehicles);
        updateSupplierChangeList(vehicles);
        updateOperationsLists(bookings, rentals, vehicles, payments);
        populateMailRecipients(users);
        updateSystemDataArea(users, vehicles, bookings, rentals, payments);
        analyticsPagination.setCurrentPageIndex(Math.min(analyticsPagination.getCurrentPageIndex(), 3));
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
    }

    private void updateActorList(List<User> users, List<Booking> bookings, List<Rental> rentals) {
        actorList.getChildren().clear();
        for (User user : users.stream().filter(u -> u.getRole() == User.Role.USER).toList()) {
            long userBookings = bookings.stream().filter(booking -> user.getId() != null && user.getId().equals(booking.getUserId())).count();
            long userRentals = rentals.stream().filter(rental -> user.getId() != null && user.getId().equals(rental.getRenterId())).count();
            Label item = new Label(user.getFullName() + " • " + user.getEmail()
                + " • Bookings " + userBookings
                + " • Rentals " + userRentals
                + " • Wallet " + ValidationUtil.formatCurrency(user.getWalletBalance())
                + (user.isLocked() ? " • LOCKED" : " • ACTIVE"));
            item.getStyleClass().add("text-body");
            actorList.getChildren().add(item);
        }
        if (actorList.getChildren().isEmpty()) {
            Label empty = new Label("No customer accounts found.");
            empty.getStyleClass().add("text-muted");
            actorList.getChildren().add(empty);
        }
    }

    private void updateManagedActorLists(List<User> users) {
        userManagementList.getChildren().clear();
        driverManagementList.getChildren().clear();
        supplierManagementList.getChildren().clear();

        for (User user : users) {
            if (user.getRole() == User.Role.USER) {
                userManagementList.getChildren().add(createActorAdminCard(user));
            } else if (user.getRole() == User.Role.DRIVER) {
                driverManagementList.getChildren().add(createDriverAdminCard(user));
            } else if (user.getRole() == User.Role.SUPPLIER) {
                supplierManagementList.getChildren().add(createSupplierAdminCard(user));
            }
        }
    }

    private VBox createActorAdminCard(User user) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label name = new Label(user.getFullName() + " • " + user.getEmail());
        name.getStyleClass().add("card-title");

        Label meta = new Label("Phone: " + user.getPhone() + " • Wallet: " + ValidationUtil.formatCurrency(user.getWalletBalance())
            + " • Status: " + (user.isLocked() ? "Locked" : "Active"));
        meta.getStyleClass().add("text-muted");

        Button actionBtn = new Button(user.isLocked() ? "Unlock Account" : "Lock Account");
        actionBtn.getStyleClass().add(user.isLocked() ? "btn-accent" : "btn-danger");
        actionBtn.setOnAction(event -> {
            boolean updated = userDAO.updateLockState(user.getId(), !user.isLocked(),
                user.isLocked() ? null : "Locked by admin");
            if (updated) {
                loadDashboard();
            }
        });

        card.getChildren().addAll(name, meta, actionBtn);
        return card;
    }

    private VBox createDriverAdminCard(User user) {
        VBox card = createActorAdminCard(user);
        long assigned = bookingCache.stream().filter(booking -> user.getId() != null && user.getId().equals(booking.getDriverId())).count();
        long completed = bookingCache.stream()
            .filter(booking -> user.getId() != null && user.getId().equals(booking.getDriverId()))
            .filter(booking -> booking.getStatus() == Booking.BookingStatus.COMPLETED)
            .count();
        Label metrics = new Label("Assigned rides: " + assigned + " • Completed: " + completed
            + " • Wallet: " + ValidationUtil.formatCurrency(user.getWalletBalance()));
        metrics.getStyleClass().add("text-muted");
        card.getChildren().add(1, metrics);
        return card;
    }

    private VBox createSupplierAdminCard(User user) {
        VBox card = createActorAdminCard(user);
        long fleet = vehicleCache.stream().filter(vehicle -> user.getId() != null && user.getId().equals(vehicle.getOwnerId())).count();
        long active = rentalCache.stream()
            .filter(rental -> user.getId() != null && user.getId().equals(rental.getSupplierId()))
            .filter(rental -> rental.getStatus() == Rental.RentalStatus.ACTIVE || rental.getStatus() == Rental.RentalStatus.OVERDUE)
            .count();
        double revenue = rentalCache.stream()
            .filter(rental -> user.getId() != null && user.getId().equals(rental.getSupplierId()))
            .filter(rental -> rental.getStatus() == Rental.RentalStatus.COMPLETED)
            .mapToDouble(rental -> rental.getTotalAmount() + rental.getPenaltyAmount())
            .sum();
        Label metrics = new Label("Fleet: " + fleet + " • Active rentals: " + active
            + " • Revenue: " + ValidationUtil.formatCurrency(revenue));
        metrics.getStyleClass().add("text-muted");
        card.getChildren().add(1, metrics);
        return card;
    }

    private void updateIncidentList(List<Rental> rentals, List<Vehicle> vehicles) {
        incidentList.getChildren().clear();

        rentals.stream()
            .filter(r -> r.getStatus() == Rental.RentalStatus.OVERDUE)
            .forEach(r -> {
                Label item = new Label("Overdue rental: " + r.getVehicleName() + " • penalty " + ValidationUtil.formatCurrency(r.getPenaltyAmount()));
                item.getStyleClass().add("text-error");
                incidentList.getChildren().add(item);
            });

        vehicles.stream()
            .filter(v -> v.getStatus() == Vehicle.Status.MAINTENANCE)
            .forEach(v -> {
                Label item = new Label("Maintenance alert: " + v.getDisplayName());
                item.getStyleClass().add("text-warning");
                incidentList.getChildren().add(item);
            });

        if (incidentList.getChildren().isEmpty()) {
            Label item = new Label("No critical incidents right now.");
            item.getStyleClass().add("text-muted");
            incidentList.getChildren().add(item);
        }
    }

    private void updateSupplierChangeList(List<Vehicle> vehicles) {
        supplierChangeList.getChildren().clear();
        List<Vehicle> pendingVehicles = vehicles.stream()
            .filter(vehicle -> vehicle.getOwnerId() != null)
            .filter(vehicle -> vehicle.getApprovalStatus() == Vehicle.ApprovalStatus.PENDING)
            .toList();

        noSupplierChangesLabel.setVisible(pendingVehicles.isEmpty());
        for (Vehicle vehicle : pendingVehicles) {
            VBox card = new VBox(8);
            card.getStyleClass().add("card");

            Label title = new Label(vehicle.getDisplayName() + " • " + ValidationUtil.formatCurrency(vehicle.getDailyRate()) + "/day");
            title.getStyleClass().add("card-title");

            Label meta = new Label("Supplier change waiting for admin approval");
            meta.getStyleClass().add("text-muted");

            Button approveButton = new Button("Approve Vehicle");
            approveButton.getStyleClass().add("btn-accent");
            approveButton.setOnAction(event -> {
                vehicle.setApprovalStatus(Vehicle.ApprovalStatus.APPROVED);
                vehicle.setAdminReviewNote("Approved by admin");
                vehicleDAO.updateVehicle(vehicle);
                loadDashboard();
            });

            Button rejectButton = new Button("Reject Vehicle");
            rejectButton.getStyleClass().add("btn-danger");
            rejectButton.setOnAction(event -> {
                vehicle.setApprovalStatus(Vehicle.ApprovalStatus.REJECTED);
                vehicle.setAdminReviewNote("Rejected by admin");
                vehicleDAO.updateVehicle(vehicle);
                loadDashboard();
            });

            card.getChildren().addAll(title, meta, approveButton, rejectButton);
            supplierChangeList.getChildren().add(card);
        }
    }

    private void updateOperationsLists(List<Booking> bookings, List<Rental> rentals, List<Vehicle> vehicles, List<Payment> payments) {
        if (bookingAdminList != null) {
            bookingAdminList.getChildren().clear();
            bookings.stream()
                .sorted((a, b) -> nullSafeTime(b.getUpdatedAt()).compareTo(nullSafeTime(a.getUpdatedAt())))
                .limit(12)
                .forEach(booking -> bookingAdminList.getChildren().add(createBookingOpsCard(booking)));
            addEmptyIfNeeded(bookingAdminList, "No bookings found.");
        }
        if (rentalAdminList != null) {
            rentalAdminList.getChildren().clear();
            rentals.stream()
                .sorted((a, b) -> nullSafeTime(b.getUpdatedAt()).compareTo(nullSafeTime(a.getUpdatedAt())))
                .limit(12)
                .forEach(rental -> rentalAdminList.getChildren().add(createRentalOpsCard(rental)));
            addEmptyIfNeeded(rentalAdminList, "No rentals found.");
        }
        if (vehicleAdminList != null) {
            vehicleAdminList.getChildren().clear();
            vehicles.stream()
                .sorted((a, b) -> nullSafeTime(b.getUpdatedAt()).compareTo(nullSafeTime(a.getUpdatedAt())))
                .limit(16)
                .forEach(vehicle -> vehicleAdminList.getChildren().add(createVehicleOpsCard(vehicle)));
            addEmptyIfNeeded(vehicleAdminList, "No vehicles found.");
        }
        if (paymentAdminList != null) {
            paymentAdminList.getChildren().clear();
            payments.stream()
                .sorted((a, b) -> nullSafeTime(b.getCreatedAt()).compareTo(nullSafeTime(a.getCreatedAt())))
                .limit(12)
                .forEach(payment -> paymentAdminList.getChildren().add(labelCard(
                    payment.getTransactionRef() + " • " + payment.getPaymentMethod() + " • "
                        + payment.getStatus() + " • " + ValidationUtil.formatCurrency(payment.getTotalAmount()))));
            addEmptyIfNeeded(paymentAdminList, "No payments found.");
        }
    }

    private VBox createBookingOpsCard(Booking booking) {
        VBox card = labelCard((booking.getVehicleName() != null ? booking.getVehicleName() : "Booking")
            + " • " + booking.getStatusString()
            + " • " + booking.getPickupLocation() + " -> " + booking.getDropoffLocation()
            + " • Payment " + (booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "PENDING"));
        if (booking.getStatus() != Booking.BookingStatus.COMPLETED && booking.getStatus() != Booking.BookingStatus.CANCELLED) {
            Button cancel = new Button("Admin Cancel");
            cancel.getStyleClass().add("btn-danger");
            cancel.setOnAction(event -> {
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                bookingDAO.updateBooking(booking);
                if (booking.getVehicleId() != null) vehicleDAO.updateStatus(booking.getVehicleId(), Vehicle.Status.AVAILABLE);
                loadDashboard();
            });
            card.getChildren().add(cancel);
        }
        return card;
    }

    private VBox createRentalOpsCard(Rental rental) {
        VBox card = labelCard((rental.getVehicleName() != null ? rental.getVehicleName() : "Rental")
            + " • " + rental.getStatus()
            + " • " + rental.getRenterName()
            + " • " + rental.getSupplierName()
            + " • Payment " + (rental.getPaymentStatus() != null ? rental.getPaymentStatus() : "PENDING"));
        if (rental.getStatus() == Rental.RentalStatus.ACTIVE || rental.getStatus() == Rental.RentalStatus.OVERDUE) {
            Button markReturned = new Button("Admin Mark Returned");
            markReturned.getStyleClass().add("btn-secondary");
            markReturned.setOnAction(event -> {
                rentalService.markRentalReturnedBySupplier(rental.getId());
                loadDashboard();
            });
            card.getChildren().add(markReturned);
        }
        return card;
    }

    private VBox createVehicleOpsCard(Vehicle vehicle) {
        VBox card = labelCard(vehicle.getDisplayName()
            + " • " + vehicle.getStatus()
            + " • " + vehicle.getApprovalStatus()
            + " • Depot " + (vehicle.getBranchLocation() != null ? vehicle.getBranchLocation() : "Direct booking fleet"));
        if (vehicle.getApprovalStatus() == Vehicle.ApprovalStatus.PENDING) {
            Button approve = new Button("Approve");
            approve.getStyleClass().add("btn-accent");
            approve.setOnAction(event -> {
                vehicle.setApprovalStatus(Vehicle.ApprovalStatus.APPROVED);
                vehicle.setAdminReviewNote("Approved by admin");
                vehicleDAO.updateVehicle(vehicle);
                loadDashboard();
            });
            card.getChildren().add(approve);
        }
        return card;
    }

    private VBox labelCard(String text) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        Label label = new Label(text);
        label.getStyleClass().add("text-body");
        label.setWrapText(true);
        card.getChildren().add(label);
        return card;
    }

    private void addEmptyIfNeeded(VBox list, String message) {
        if (!list.getChildren().isEmpty()) {
            return;
        }
        Label empty = new Label(message);
        empty.getStyleClass().add("text-muted");
        list.getChildren().add(empty);
    }

    private java.util.Date nullSafeTime(java.util.Date date) {
        return date != null ? date : new java.util.Date(0);
    }

    private void populateMailRecipients(List<User> users) {
        String currentSelection = mailRecipientCombo.getValue();
        mailRecipientCombo.getItems().clear();
        for (User user : users) {
            mailRecipientCombo.getItems().add(user.getFullName() + " <" + user.getEmail() + ">");
        }
        if (currentSelection != null && mailRecipientCombo.getItems().contains(currentSelection)) {
            mailRecipientCombo.setValue(currentSelection);
        } else if (!mailRecipientCombo.getItems().isEmpty()) {
            mailRecipientCombo.setValue(mailRecipientCombo.getItems().get(0));
        }
    }

    private void updateSystemDataArea(List<User> users, List<Vehicle> vehicles, List<Booking> bookings,
                                      List<Rental> rentals, List<Payment> payments) {
        StringBuilder builder = new StringBuilder();
        builder.append("Collection Summary\n");
        getCollectionCounts().forEach((name, count) ->
            builder.append("- ").append(name).append(": ").append(count).append('\n')
        );
        builder.append("\nUsers\n");
        users.forEach(user -> builder.append(user.getFullName()).append(" | ").append(user.getRole()).append(" | ")
            .append(user.isLocked() ? "LOCKED" : "ACTIVE").append('\n'));
        builder.append("\nVehicles\n");
        vehicles.forEach(vehicle -> builder.append(vehicle.getDisplayName()).append(" | ").append(vehicle.getStatus()).append(" | ")
            .append(vehicle.getApprovalStatus()).append('\n'));
        builder.append("\nBookings\n");
        bookings.forEach(booking -> builder.append(booking.getVehicleName()).append(" | ").append(booking.getStatus()).append(" | ")
            .append(booking.getPaymentStatus()).append('\n'));
        builder.append("\nRentals\n");
        rentals.forEach(rental -> builder.append(rental.getVehicleName()).append(" | ").append(rental.getStatus()).append(" | ")
            .append(rental.getPaymentStatus()).append('\n'));
        builder.append("\nPayments\n");
        payments.forEach(payment -> builder.append(payment.getTransactionRef()).append(" | ")
            .append(payment.getPaymentMethod()).append(" | ").append(payment.getStatus()).append('\n'));
        systemDataArea.setText(builder.toString());
    }

    private Map<String, Long> getCollectionCounts() {
        return MongoDBConnection.getInstance().getCollectionCounts(MongoCollections.ALL_COLLECTIONS);
    }

    @FXML
    private void onSendMail() {
        String recipient = mailRecipientCombo.getValue();
        String subject = mailSubjectField.getText() != null ? mailSubjectField.getText().trim() : "";
        String body = mailBodyArea.getText() != null ? mailBodyArea.getText().trim() : "";

        if (recipient == null || recipient.isBlank() || subject.isBlank() || body.isBlank()) {
            System.out.println("[Admin Mail] Missing recipient, subject, or body.");
            return;
        }

        System.out.println("[Admin Mail] To: " + recipient);
        System.out.println("[Admin Mail] Subject: " + subject);
        System.out.println("[Admin Mail] Body: " + body);

        mailSubjectField.clear();
        mailBodyArea.clear();
    }

    @FXML private void onRefresh() { loadDashboard(); }
    @FXML private void onShowUserBoard() { showUserBoard(); }
    @FXML private void onShowDriverBoard() { showDriverBoard(); }
    @FXML private void onShowSupplierBoard() { showSupplierBoard(); }
    @FXML
    private void onDownloadSystemExport() {
        try {
            String outputDir = System.getProperty("user.home") + "\\Documents\\RentoAdminExports";
            String path = adminExportService.exportAllData(outputDir);
            AlertUtil.showSuccess("System export generated:\n" + path);
        } catch (Exception ex) {
            AlertUtil.showError("Export Failed", ex.getMessage());
        }
    }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onNavProfile() { NavigationManager.navigateTo("/fxml/profile.fxml"); }
    @FXML private void onLogout() {
        authService.logout();
        NavigationManager.clearHistory();
        NavigationManager.navigateTo("/fxml/landing.fxml");
    }

    private void showUserBoard() {
        userBoardSection.setManaged(true);
        userBoardSection.setVisible(true);
        driverBoardSection.setManaged(false);
        driverBoardSection.setVisible(false);
        supplierBoardSection.setManaged(false);
        supplierBoardSection.setVisible(false);
    }

    private void showDriverBoard() {
        userBoardSection.setManaged(false);
        userBoardSection.setVisible(false);
        driverBoardSection.setManaged(true);
        driverBoardSection.setVisible(true);
        supplierBoardSection.setManaged(false);
        supplierBoardSection.setVisible(false);
    }

    private void showSupplierBoard() {
        userBoardSection.setManaged(false);
        userBoardSection.setVisible(false);
        driverBoardSection.setManaged(false);
        driverBoardSection.setVisible(false);
        supplierBoardSection.setManaged(true);
        supplierBoardSection.setVisible(true);
    }

    public void openBoard(String board) {
        if ("DRIVER".equalsIgnoreCase(board)) {
            showDriverBoard();
            return;
        }
        if ("SUPPLIER".equalsIgnoreCase(board)) {
            showSupplierBoard();
            return;
        }
        showUserBoard();
    }

    private Node createAnalyticsPage(Integer index) {
        int pageIndex = index == null ? 0 : index;
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        if (pageIndex == 0) {
            Label title = new Label("Actor Mix");
            title.getStyleClass().add("heading-3");
            PieChart chart = new PieChart();
            chart.setPrefHeight(380);
            chart.getData().setAll(
                new PieChart.Data("Customers", userCache.stream().filter(u -> u.getRole() == User.Role.USER).count()),
                new PieChart.Data("Suppliers", userCache.stream().filter(u -> u.getRole() == User.Role.SUPPLIER).count()),
                new PieChart.Data("Drivers", userCache.stream().filter(u -> u.getRole() == User.Role.DRIVER).count()),
                new PieChart.Data("Admins", userCache.stream().filter(u -> u.getRole() == User.Role.ADMIN).count())
            );
            container.getChildren().addAll(title, chart);
            return container;
        }

        if (pageIndex == 1) {
            Label title = new Label("Platform Volume");
            title.getStyleClass().add("heading-3");
            BarChart<String, Number> chart = createBarChart("Platform Metric", "Count");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Bookings", bookingCache.size()));
            series.getData().add(new XYChart.Data<>("Rentals", rentalCache.size()));
            series.getData().add(new XYChart.Data<>("Payments", paymentCache.size()));
            series.getData().add(new XYChart.Data<>("Overdue", rentalCache.stream().filter(r -> r.getStatus() == Rental.RentalStatus.OVERDUE).count()));
            chart.getData().add(series);
            container.getChildren().addAll(title, chart);
            return container;
        }

        if (pageIndex == 2) {
            Label title = new Label("Wallet Balance By Role");
            title.getStyleClass().add("heading-3");
            BarChart<String, Number> chart = createBarChart("Role", "Wallet Value");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Users", userCache.stream().filter(u -> u.getRole() == User.Role.USER).mapToDouble(User::getWalletBalance).sum()));
            series.getData().add(new XYChart.Data<>("Drivers", userCache.stream().filter(u -> u.getRole() == User.Role.DRIVER).mapToDouble(User::getWalletBalance).sum()));
            series.getData().add(new XYChart.Data<>("Suppliers", userCache.stream().filter(u -> u.getRole() == User.Role.SUPPLIER).mapToDouble(User::getWalletBalance).sum()));
            chart.getData().add(series);
            container.getChildren().addAll(title, chart);
            return container;
        }

        Label title = new Label("Revenue And Penalty Exposure");
        title.getStyleClass().add("heading-3");
        BarChart<String, Number> chart = createBarChart("Metric", "Amount");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Rental Revenue", rentalCache.stream()
            .filter(rental -> rental.getStatus() == Rental.RentalStatus.COMPLETED)
            .mapToDouble(rental -> rental.getTotalAmount() + rental.getPenaltyAmount()).sum()));
        series.getData().add(new XYChart.Data<>("Penalty Exposure", rentalCache.stream().mapToDouble(Rental::getPenaltyAmount).sum()));
        chart.getData().add(series);
        container.getChildren().addAll(title, chart);
        return container;
    }

    private BarChart<String, Number> createBarChart(String xLabel, String yLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCategoryGap(18);
        chart.setPrefHeight(380);
        chart.setPrefWidth(900);
        return chart;
    }
}
