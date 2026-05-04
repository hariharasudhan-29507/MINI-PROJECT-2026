package com.rento.controllers;

import com.rento.dao.VehicleDAO;
import com.rento.dao.UserDAO;
import com.rento.models.Rental;
import com.rento.models.Vehicle;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.AuthService;
import com.rento.services.PaymentService;
import com.rento.services.RentalService;

import com.rento.utils.AlertUtil;
import com.rento.utils.ValidationUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Supplier Dashboard.
 */
public class SupplierDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label totalVehicles;
    @FXML private Label activeRentals;
    @FXML private Label pendingApprovals;
    @FXML private Label totalRevenue;
    @FXML private Label walletBalance;
    @FXML private VBox dashboardSection;
    @FXML private VBox walletSection;
    @FXML private VBox historySection;
    @FXML private Label walletSectionBalance;
    @FXML private Label walletSectionRevenue;
    @FXML private VBox walletTransactionList;
    @FXML private TextField walletTopUpAmountField;
    @FXML private ComboBox<String> walletTopUpMethodCombo;
    @FXML private TextField walletTopUpHolderField;
    @FXML private TextField walletTopUpReferenceField;
    @FXML private TextField walletTopUpExpiryField;
    @FXML private PasswordField walletTopUpCvvField;
    @FXML private Label walletTopUpStatusLabel;
    @FXML private VBox approvalList;
    @FXML private Label noApprovalsLabel;
    @FXML private FlowPane vehicleFleet;
    @FXML private Label noVehiclesLabel;
    @FXML private Pagination analyticsPagination;
    @FXML private VBox supplierHistoryList;

    private final RentalService rentalService = new RentalService();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final AuthService authService = new AuthService();
    private final UserDAO userDAO = new UserDAO();
    private final PaymentService paymentService = new PaymentService();
    private List<Rental> requestedCache = List.of();
    private List<Rental> approvedCache = List.of();
    private List<Rental> activeCache = List.of();
    private List<Rental> allSupplierRentalsCache = List.of();
    private List<Vehicle> vehicleCache = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + SessionManager.getInstance().getCurrentUserName() + " (Supplier)");
        analyticsPagination.setPageCount(3);
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
        if (walletTopUpMethodCombo != null) {
            walletTopUpMethodCombo.setItems(javafx.collections.FXCollections.observableArrayList("Credit Card", "UPI"));
            walletTopUpMethodCombo.setValue("Credit Card");
            walletTopUpMethodCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateWalletTopUpHints());
            updateWalletTopUpHints();
        }
        showDashboardSection();
        loadDashboard();
    }

    private void loadDashboard() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }

        List<Rental> requested = rentalService.getRentalsBySupplier(SessionManager.getInstance().getCurrentUser().getId()).stream()
            .filter(r -> r.getStatus() == Rental.RentalStatus.REQUESTED)
            .toList();
        List<Rental> approved = rentalService.getAwaitingOtpBySupplier(SessionManager.getInstance().getCurrentUser().getId());
        List<Rental> active = rentalService.getActiveRentalsBySupplier(SessionManager.getInstance().getCurrentUser().getId());
        List<Rental> allSupplierRentals = rentalService.getRentalsBySupplier(SessionManager.getInstance().getCurrentUser().getId());
        requestedCache = requested;
        approvedCache = approved;
        activeCache = active;
        allSupplierRentalsCache = allSupplierRentals;

        int totalPending = requested.size() + approved.size();
        pendingApprovals.setText(String.valueOf(totalPending));
        activeRentals.setText(String.valueOf(active.size()));
        double grossRevenue = allSupplierRentals.stream().mapToDouble(r -> r.getTotalAmount() + r.getPenaltyAmount()).sum();
        totalRevenue.setText(ValidationUtil.formatCurrency(grossRevenue));
        walletSectionRevenue.setText(ValidationUtil.formatCurrency(grossRevenue));
        if (SessionManager.getInstance().getCurrentUser() != null) {
            com.rento.models.User freshUser = userDAO.findById(SessionManager.getInstance().getCurrentUser().getId());
            String wallet = ValidationUtil.formatCurrency(freshUser != null ? freshUser.getWalletBalance() : 0);
            walletBalance.setText(wallet);
            walletSectionBalance.setText(wallet);
        }

        approvalList.getChildren().clear();
        if (requested.isEmpty() && approved.isEmpty()) {
            noApprovalsLabel.setVisible(active.isEmpty());
        } else {
            noApprovalsLabel.setVisible(false);
            // Show requested rentals first (need approval)
            for (Rental r : requested) {
                approvalList.getChildren().add(createRequestCard(r));
            }
            // Then show approved rentals (awaiting OTP confirmation)
            for (Rental r : approved) {
                approvalList.getChildren().add(createApprovedCard(r));
            }
        }
        for (Rental r : active) {
            approvalList.getChildren().add(createActiveRentalCard(r));
        }

        try {
            List<Vehicle> vehicles = vehicleDAO.findByOwner(SessionManager.getInstance().getCurrentUser().getId());
            vehicleCache = vehicles;
            totalVehicles.setText(String.valueOf(vehicles.size()));
            vehicleFleet.getChildren().clear();
            if (!vehicles.isEmpty()) {
                noVehiclesLabel.setVisible(false);
                for (Vehicle v : vehicles) {
                    vehicleFleet.getChildren().add(createVehicleCard(v));
                }
            }
        } catch (Exception ignored) {}

        updateWalletTransactions(allSupplierRentals);
        updateHistory(allSupplierRentals);
        analyticsPagination.setCurrentPageIndex(Math.min(analyticsPagination.getCurrentPageIndex(), 2));
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
    }

    private HBox createRequestCard(Rental rental) {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 16; -fx-background-radius: 12px;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(rental.getVehicleName() != null ? rental.getVehicleName() : "Vehicle");
        nameLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: white; -fx-font-size: 15px;");

        Label priceLabel = new Label("Customer: " + (rental.getRenterName() != null ? rental.getRenterName() : "Unknown"));
        priceLabel.getStyleClass().add("vehicle-price");

        Label periodLabel = new Label("Period: " + rental.getRentalDurationLabel() + " • " + ValidationUtil.formatCurrency(rental.getTotalAmount()));
        periodLabel.getStyleClass().add("text-muted");

        Label statusLabel = new Label("● " + Rental.RentalStatus.REQUESTED.name());
        statusLabel.getStyleClass().addAll("badge", "badge-warning");

        info.getChildren().addAll(nameLabel, priceLabel, periodLabel, statusLabel);

        VBox actions = new VBox(8);
        Button approveBtn = new Button("✓ Approve");
        approveBtn.getStyleClass().add("btn-accent");
        approveBtn.setStyle("-fx-padding: 8 20;");
        approveBtn.setOnAction(e -> {
            boolean ok = rentalService.approveRentalRequest(rental.getId());
            if (ok) {
                AlertUtil.showSuccess("Rental approved. OTP: " + rental.getApprovalOtp() + "\nShare with renter for activation.");
            }
            loadDashboard();
        });

        Button rejectBtn = new Button("✗ Reject");
        rejectBtn.getStyleClass().addAll("btn-danger", "btn-small");
        rejectBtn.setOnAction(e -> {
            rentalService.rejectRentalRequest(rental.getId());
            AlertUtil.showInfo("Rejected", "Rental request rejected and the vehicle remains available.");
            loadDashboard();
        });

        actions.getChildren().addAll(approveBtn, rejectBtn);
        card.getChildren().addAll(info, actions);
        return card;
    }

    private HBox createApprovedCard(Rental rental) {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 16; -fx-background-radius: 12px; -fx-border-color: #06d6a0; -fx-border-width: 2;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(rental.getVehicleName() != null ? rental.getVehicleName() : "Vehicle");
        nameLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #06d6a0; -fx-font-size: 15px;");

        Label priceLabel = new Label("Customer: " + (rental.getRenterName() != null ? rental.getRenterName() : "Unknown"));
        priceLabel.getStyleClass().add("vehicle-price");

        Label periodLabel = new Label("Period: " + rental.getRentalDurationLabel() + " • " + ValidationUtil.formatCurrency(rental.getTotalAmount()));
        periodLabel.getStyleClass().add("text-muted");

        Label statusLabel = new Label("● Awaiting OTP Confirmation");
        statusLabel.getStyleClass().addAll("badge", "badge-success");

        Label paymentLabel = new Label("Payment: " + (rental.getPaymentMethod() != null ? rental.getPaymentMethod().replace('_', ' ') : "Pending"));
        paymentLabel.getStyleClass().add("text-muted");

        info.getChildren().addAll(nameLabel, priceLabel, periodLabel, statusLabel, paymentLabel);

        VBox actions = new VBox(8);
        TextField otpField = new TextField();
        otpField.setPromptText("OTP");
        otpField.setText(rental.getApprovalOtp() != null ? rental.getApprovalOtp() : "");
        otpField.setStyle("-fx-padding: 8;");

        Button confirmOtpBtn = new Button("Confirm OTP & Activate");
        confirmOtpBtn.getStyleClass().add("btn-secondary");
        confirmOtpBtn.setStyle("-fx-padding: 8 20;");
        confirmOtpBtn.setOnAction(e -> {
            boolean ok = rentalService.confirmSupplierOtp(rental.getId(), otpField.getText());
            if (ok) {
                AlertUtil.showSuccess("Rental confirmed and activated.");
            } else {
                AlertUtil.showWarning("Invalid OTP", "Please enter the correct OTP to activate rental.");
            }
            loadDashboard();
        });

        actions.getChildren().addAll(otpField, confirmOtpBtn);
        if (rental.isCashPaymentPending() && !rental.isPaidVerified()) {
            Button paidBtn = new Button("Mark Cash Paid");
            paidBtn.getStyleClass().add("btn-accent");
            paidBtn.setOnAction(e -> {
                boolean ok = rentalService.verifyCashPaymentForRental(rental.getId(), SessionManager.getInstance().getCurrentUserName());
                if (ok) {
                    AlertUtil.showSuccess("Cash payment verified. Receipt is available for the renter.");
                }
                loadDashboard();
            });
            actions.getChildren().add(paidBtn);
        } else if (rental.isPaidVerified()) {
            Label paidBadge = new Label("Paid Verified");
            paidBadge.getStyleClass().addAll("badge", "badge-success");
            actions.getChildren().add(paidBadge);
        }
        card.getChildren().addAll(info, actions);
        return card;
    }

    private HBox createActiveRentalCard(Rental rental) {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 16; -fx-background-radius: 12px; -fx-border-color: #ff9f43; -fx-border-width: 2;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(rental.getVehicleName() != null ? rental.getVehicleName() : "Vehicle");
        nameLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: white; -fx-font-size: 15px;");

        Label renterLabel = new Label("Customer: " + (rental.getRenterName() != null ? rental.getRenterName() : "Unknown"));
        renterLabel.getStyleClass().add("vehicle-price");

        Label depotLabel = new Label("Depot: " + (rental.getDepotLocation() != null ? rental.getDepotLocation() : "Supplier Depot")
            + " • Plate: " + (rental.getVehicleLicensePlate() != null ? rental.getVehicleLicensePlate() : "Pending"));
        depotLabel.getStyleClass().add("text-muted");

        Label statusLabel = new Label("● " + rental.getStatus().name().replace('_', ' '));
        statusLabel.getStyleClass().addAll("badge", rental.getStatus() == Rental.RentalStatus.OVERDUE ? "badge-danger" : "badge-warning");

        info.getChildren().addAll(nameLabel, renterLabel, depotLabel, statusLabel);

        VBox actions = new VBox(8);
        Button endBtn = new Button("End Rental From Supplier");
        endBtn.getStyleClass().add("btn-primary");
        endBtn.setOnAction(e -> {
            boolean ok = rentalService.markRentalReturnedBySupplier(rental.getId());
            if (ok) {
                AlertUtil.showSuccess(rental.isRenterMarkedComplete()
                    ? "Rental closed successfully."
                    : "Supplier return recorded. Waiting for renter side confirmation.");
                loadDashboard();
            } else {
                AlertUtil.showError("Cannot end rental", "Only active rentals can be completed from the supplier side.");
            }
        });
        actions.getChildren().add(endBtn);

        card.getChildren().addAll(info, actions);
        return card;
    }

    private VBox createVehicleCard(Vehicle vehicle) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPrefWidth(230);

        Label name = new Label(vehicle.getDisplayName());
        name.getStyleClass().add("card-title");

        String statusText = vehicle.getStatus() != null ? vehicle.getStatus().name().replace("_", " ") : "UNKNOWN";
        if (vehicle.getStatus() == Vehicle.Status.IN_USE) {
            statusText = "ON DUTY";
        } else if (vehicle.getStatus() == Vehicle.Status.MAINTENANCE || vehicle.getStatus() == Vehicle.Status.UNDER_INSPECTION) {
            statusText = "ON SERVICE";
        }
        Label status = new Label("● " + statusText);
        String statusStyle = "badge-primary";
        if (vehicle.getStatus() == Vehicle.Status.AVAILABLE) statusStyle = "badge-success";
        else if (vehicle.getStatus() == Vehicle.Status.IN_USE) statusStyle = "badge-warning";
        else if (vehicle.getStatus() == Vehicle.Status.MAINTENANCE) statusStyle = "badge-danger";
        status.getStyleClass().addAll("badge", statusStyle);

        Label price = new Label("₹" + String.format("%.0f", vehicle.getDailyRate()) + "/day");
        price.getStyleClass().add("text-body");

        Label depot = new Label("Depot: " + (vehicle.getBranchLocation() != null ? vehicle.getBranchLocation() : "Supplier Depot"));
        depot.getStyleClass().add("text-muted");

        HBox actions = new HBox(8);
        Button availableBtn = new Button("Available");
        availableBtn.getStyleClass().add("btn-secondary");
        availableBtn.setOnAction(e -> updateVehicleStatus(vehicle, Vehicle.Status.AVAILABLE));
        Button serviceBtn = new Button("On Service");
        serviceBtn.getStyleClass().add("btn-danger");
        serviceBtn.setOnAction(e -> updateVehicleStatus(vehicle, Vehicle.Status.MAINTENANCE));
        actions.getChildren().addAll(availableBtn, serviceBtn);

        card.getChildren().addAll(name, status, price, depot, actions);
        return card;
    }

    @FXML private void onRefresh() { loadDashboard(); }
    @FXML private void onShowDashboard() { showDashboardSection(); }
    @FXML private void onShowWallet() { showWalletSection(); }
    @FXML public void onShowHistory() { showHistorySection(); }
    @FXML private void onAddVehicle() { NavigationManager.navigateTo("/fxml/rent.fxml"); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onLogout() {
        authService.logout();
        NavigationManager.clearHistory();
        NavigationManager.navigateTo("/fxml/landing.fxml");
    }

    private void showDashboardSection() {
        showOnly(dashboardSection);
    }

    private void showWalletSection() {
        showOnly(walletSection);
    }

    private void showHistorySection() {
        showOnly(historySection);
        updateHistory(allSupplierRentalsCache);
    }

    private void showOnly(VBox section) {
        for (VBox candidate : List.of(dashboardSection, walletSection, historySection)) {
            if (candidate != null) {
                boolean show = candidate == section;
                candidate.setManaged(show);
                candidate.setVisible(show);
            }
        }
    }

    private void updateWalletTransactions(List<Rental> rentals) {
        walletTransactionList.getChildren().clear();
        List<Rental> completed = rentals.stream()
            .filter(rental -> rental.getStatus() == Rental.RentalStatus.COMPLETED)
            .toList();
        if (completed.isEmpty()) {
            Label empty = new Label("No supplier wallet credits yet.");
            empty.getStyleClass().add("text-muted");
            walletTransactionList.getChildren().add(empty);
            return;
        }
        for (Rental rental : completed) {
            double value = rental.getTotalAmount() + rental.getPenaltyAmount();
            Label item = new Label(
                rental.getVehicleName() + " • +" + ValidationUtil.formatCurrency(value)
                    + " • Settled from " + (rental.getRenterName() != null ? rental.getRenterName() : "renter")
            );
            item.getStyleClass().add("text-body");
            walletTransactionList.getChildren().add(item);
        }
    }

    @FXML
    private void onTopUpWallet() {
        AlertUtil.showInfo("Wallet is read-only", "Supplier wallets show rental settlement transactions only. Money is credited after successful rentals.");
        return;
    }

    private void unusedTopUpWalletLegacy() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(walletTopUpAmountField.getText().trim());
        } catch (Exception ex) {
            walletTopUpStatusLabel.setText("Enter a valid amount.");
            return;
        }
        com.rento.models.Payment.PaymentMethod method = "UPI".equals(walletTopUpMethodCombo.getValue())
            ? com.rento.models.Payment.PaymentMethod.UPI
            : com.rento.models.Payment.PaymentMethod.CREDIT_CARD;
        com.rento.models.Payment payment = paymentService.topUpWallet(
            SessionManager.getInstance().getCurrentUser().getId(),
            amount,
            method,
            walletTopUpReferenceField.getText(),
            walletTopUpHolderField.getText(),
            walletTopUpExpiryField.getText(),
            walletTopUpCvvField.getText()
        );
        if (payment == null) {
            walletTopUpStatusLabel.setText("Wallet top-up failed. Check details and try again.");
            return;
        }
        walletTopUpStatusLabel.setText("Added " + ValidationUtil.formatCurrency(amount) + " • Ref " + payment.getTransactionRef());
        walletTopUpAmountField.clear();
        walletTopUpHolderField.clear();
        walletTopUpReferenceField.clear();
        walletTopUpExpiryField.clear();
        walletTopUpCvvField.clear();
        loadDashboard();
    }

    private void updateWalletTopUpHints() {
        if (walletTopUpMethodCombo == null || walletTopUpReferenceField == null) {
            return;
        }
        boolean upi = "UPI".equals(walletTopUpMethodCombo.getValue());
        walletTopUpReferenceField.setPromptText(upi ? "name@bank" : "1234 5678 9012 3456");
        walletTopUpHolderField.setPromptText(upi ? "UPI account holder" : "Card holder name");
        walletTopUpExpiryField.setDisable(upi);
        walletTopUpCvvField.setDisable(upi);
        if (upi) {
            walletTopUpExpiryField.clear();
            walletTopUpCvvField.clear();
        }
    }

    private void updateHistory(List<Rental> rentals) {
        if (supplierHistoryList == null) return;
        supplierHistoryList.getChildren().clear();
        List<Rental> history = rentals.stream()
            .filter(rental -> rental.getStatus() == Rental.RentalStatus.COMPLETED
                || rental.getStatus() == Rental.RentalStatus.CANCELLED
                || rental.getStatus() == Rental.RentalStatus.REJECTED)
            .toList();
        if (history.isEmpty()) {
            Label empty = new Label("No past supplier actions yet.");
            empty.getStyleClass().add("text-muted");
            supplierHistoryList.getChildren().add(empty);
            return;
        }
        for (Rental rental : history) {
            Label item = new Label(
                (rental.getVehicleName() != null ? rental.getVehicleName() : "Vehicle")
                    + " • " + rental.getStatus().name()
                    + " • " + (rental.getRenterName() != null ? rental.getRenterName() : "Customer")
                    + " • " + ValidationUtil.formatCurrency(rental.getTotalAmount() + rental.getPenaltyAmount())
            );
            item.getStyleClass().add("text-body");
            supplierHistoryList.getChildren().add(item);
        }
    }

    private void updateVehicleStatus(Vehicle vehicle, Vehicle.Status newStatus) {
        if (vehicle == null || vehicle.getId() == null) {
            return;
        }
        boolean activeRentalExists = allSupplierRentalsCache.stream()
            .anyMatch(rental -> rental.getVehicleId() != null
                && rental.getVehicleId().equals(vehicle.getId())
                && (rental.getStatus() == Rental.RentalStatus.ACTIVE || rental.getStatus() == Rental.RentalStatus.OVERDUE));
        if (activeRentalExists && newStatus != Vehicle.Status.IN_USE) {
            AlertUtil.showError("Vehicle Busy", "This depot vehicle is still tied to an active rental.");
            return;
        }
        vehicleDAO.updateStatus(vehicle.getId(), newStatus);
        loadDashboard();
    }

    private Node createAnalyticsPage(Integer index) {
        int pageIndex = index == null ? 0 : index;
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        if (pageIndex == 0) {
            long available = vehicleCache.stream().filter(v -> v.getStatus() == Vehicle.Status.AVAILABLE).count();
            long inUse = vehicleCache.stream().filter(v -> v.getStatus() == Vehicle.Status.IN_USE).count();
            long maintenance = vehicleCache.stream().filter(v -> v.getStatus() == Vehicle.Status.MAINTENANCE).count();
            Label title = new Label("Fleet Status");
            title.getStyleClass().add("heading-3");
            PieChart chart = new PieChart();
            chart.setPrefHeight(280);
            chart.getData().setAll(
                new PieChart.Data("Available", available),
                new PieChart.Data("In Use", inUse),
                new PieChart.Data("Maintenance", maintenance)
            );
            container.getChildren().addAll(title, chart);
            return container;
        }

        if (pageIndex == 1) {
            Label title = new Label("Rental Stage Pipeline");
            title.getStyleClass().add("heading-3");
            BarChart<String, Number> chart = createBarChart("Stage", "Count");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Requested", requestedCache.size()));
            series.getData().add(new XYChart.Data<>("Approved", approvedCache.size()));
            series.getData().add(new XYChart.Data<>("Active", activeCache.stream().filter(r -> r.getStatus() == Rental.RentalStatus.ACTIVE).count()));
            series.getData().add(new XYChart.Data<>("Overdue", activeCache.stream().filter(r -> r.getStatus() == Rental.RentalStatus.OVERDUE).count()));
            series.getData().add(new XYChart.Data<>("Completed", allSupplierRentalsCache.stream().filter(r -> r.getStatus() == Rental.RentalStatus.COMPLETED).count()));
            chart.getData().add(series);
            container.getChildren().addAll(title, chart);
            return container;
        }

        Label title = new Label("Revenue And Exposure");
        title.getStyleClass().add("heading-3");
        BarChart<String, Number> chart = createBarChart("Metric", "Amount");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Completed Revenue", allSupplierRentalsCache.stream()
            .filter(r -> r.getStatus() == Rental.RentalStatus.COMPLETED)
            .mapToDouble(r -> r.getTotalAmount() + r.getPenaltyAmount()).sum()));
        series.getData().add(new XYChart.Data<>("Active Exposure", activeCache.stream()
            .mapToDouble(r -> r.getTotalAmount() + r.getPenaltyAmount()).sum()));
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
        chart.setPrefHeight(280);
        return chart;
    }
}
