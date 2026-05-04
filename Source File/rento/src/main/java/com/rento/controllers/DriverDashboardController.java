package com.rento.controllers;

import com.rento.models.Booking;
import com.rento.models.Driver;
import com.rento.dao.UserDAO;
import com.rento.dao.DriverDAO;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.AuthService;
import com.rento.services.BookingService;
import com.rento.services.OfflineMapService;
import com.rento.services.PaymentService;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.AlertUtil;
import com.rento.utils.MapPoint;
import com.rento.utils.Session;
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

import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Driver Dashboard.
 */
public class DriverDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label activeRides;
    @FXML private Label completedRides;
    @FXML private Label pendingRequests;
    @FXML private Label earnings;
    @FXML private Label walletBalance;
    @FXML private VBox dashboardSection;
    @FXML private VBox currentDriveSection;
    @FXML private VBox historySection;
    @FXML private VBox walletSection;
    @FXML private Label walletSectionBalance;
    @FXML private Label walletSectionEarnings;
    @FXML private VBox walletTransactionList;
    @FXML private TextField walletTopUpAmountField;
    @FXML private ComboBox<String> walletTopUpMethodCombo;
    @FXML private TextField walletTopUpHolderField;
    @FXML private TextField walletTopUpReferenceField;
    @FXML private TextField walletTopUpExpiryField;
    @FXML private PasswordField walletTopUpCvvField;
    @FXML private Label walletTopUpStatusLabel;
    @FXML private VBox requestList;
    @FXML private Label noRequestsLabel;
    @FXML private TextField otpInput;
    @FXML private Label otpResult;
    @FXML private Pagination analyticsPagination;
    @FXML private ComboBox<String> availabilityStatusCombo;
    @FXML private ComboBox<String> vehicleCategoryCombo;
    @FXML private Label availabilityStatusLabel;
    @FXML private TextField workCityField;
    @FXML private TextField workStreetField;
    @FXML private TextField workAddressLine1Field;
    @FXML private TextField workLandmarkField;
    @FXML private TextField assignedVehicleNameField;
    @FXML private Label currentDriveTitleLabel;
    @FXML private Label currentDriveStatusLabel;
    @FXML private Label currentCustomerLabel;
    @FXML private Label currentPickupLabel;
    @FXML private Label currentDropLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label currentFareLabel;
    @FXML private TextField currentOtpInput;
    @FXML private Label currentDriveActionLabel;
    @FXML private Button currentVerifyOtpBtn;
    @FXML private Button currentEndDriveBtn;
    @FXML private Button currentCancelDriveBtn;
    @FXML private VBox driverHistoryList;

    private final BookingService bookingService = new BookingService();
    private final AuthService authService = new AuthService();
    private final UserDAO userDAO = new UserDAO();
    private final DriverDAO driverDAO = new DriverDAO();
    private final PaymentService paymentService = new PaymentService();
    private List<Booking> pendingBookingCache = List.of();
    private List<Booking> confirmedBookingCache = List.of();
    private List<Booking> allBookingCache = List.of();
    private Driver currentDriverProfile;
    private Booking currentDrive;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + SessionManager.getInstance().getCurrentUserName() + " (Driver)");
        analyticsPagination.setPageCount(3);
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
        availabilityStatusCombo.setItems(javafx.collections.FXCollections.observableArrayList("ONLINE", "OFFLINE", "BREAK"));
        vehicleCategoryCombo.setItems(javafx.collections.FXCollections.observableArrayList("SUV", "SEDAN", "HATCHBACK", "COUPE", "TRUCK", "VAN", "BIKE", "SCOOTER", "BUS"));
        vehicleCategoryCombo.setValue("SUV");
        if (walletTopUpMethodCombo != null) {
            walletTopUpMethodCombo.setItems(javafx.collections.FXCollections.observableArrayList("Credit Card", "UPI"));
            walletTopUpMethodCombo.setValue("Credit Card");
            walletTopUpMethodCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateWalletTopUpHints());
            updateWalletTopUpHints();
        }
        showDashboardSection();
        loadDashboard();
    }

    private void loadDashboard() {
        loadDriverProfile();
        List<Booking> pending = SessionManager.getInstance().getCurrentUser() != null
            ? bookingService.getPendingBookingsForDriver(SessionManager.getInstance().getCurrentUser().getId())
            : List.of();
        List<Booking> confirmed = SessionManager.getInstance().getCurrentUser() != null
            ? bookingService.getConfirmedBookingsForDriver(SessionManager.getInstance().getCurrentUser().getId())
            : List.of();
        List<Booking> myBookings = SessionManager.getInstance().getCurrentUser() != null
            ? bookingService.getBookingsByDriver(SessionManager.getInstance().getCurrentUser().getId())
            : List.of();
        pendingBookingCache = pending;
        confirmedBookingCache = confirmed;
        allBookingCache = myBookings;
        currentDrive = SessionManager.getInstance().getCurrentUser() != null
            ? bookingService.getCurrentBookingForDriver(SessionManager.getInstance().getCurrentUser().getId())
            : null;
        pendingRequests.setText(String.valueOf(pending.size()));
        activeRides.setText(currentDrive != null ? "1" : "0");
        completedRides.setText(String.valueOf(myBookings.stream()
            .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
            .count()));
        double driverRevenue = myBookings.stream()
            .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
            .mapToDouble(Booking::getTotalCost)
            .sum() * 0.15;
        earnings.setText(ValidationUtil.formatCurrency(driverRevenue));
        if (SessionManager.getInstance().getCurrentUser() != null) {
            com.rento.models.User freshUser = userDAO.findById(SessionManager.getInstance().getCurrentUser().getId());
            String wallet = ValidationUtil.formatCurrency(freshUser != null ? freshUser.getWalletBalance() : 0);
            walletBalance.setText(wallet);
            walletSectionBalance.setText(wallet);
        }
        walletSectionEarnings.setText(ValidationUtil.formatCurrency(driverRevenue));

        requestList.getChildren().clear();
        if (pending.isEmpty() && confirmed.isEmpty()) {
            noRequestsLabel.setVisible(true);
        } else {
            noRequestsLabel.setVisible(false);
            // Pending requests (for acceptance)
            for (Booking b : pending) {
                requestList.getChildren().add(createRequestCard(b));
            }
            // Confirmed bookings (for OTP verification)
            for (Booking b : confirmed) {
                requestList.getChildren().add(createConfirmedCard(b));
            }
        }

        updateWalletTransactions(myBookings);
        updateCurrentDrivePanel();
        updateHistoryPanel(myBookings);
        analyticsPagination.setCurrentPageIndex(Math.min(analyticsPagination.getCurrentPageIndex(), 2));
        analyticsPagination.setPageFactory(this::createAnalyticsPage);
    }

    private HBox createConfirmedCard(Booking booking) {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 16; -fx-background-radius: 12px; -fx-border-color: #06d6a0; -fx-border-width: 2;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        VBox.setVgrow(info, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

        Label vehicleLabel = new Label(booking.getVehicleName() != null ? booking.getVehicleName() : "Vehicle");
        vehicleLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #06d6a0; -fx-font-size: 15px;");

        Label statusLabel = new Label("● Confirmed - Awaiting OTP");
        statusLabel.getStyleClass().addAll("badge", "badge-success");

        Label locationLabel = new Label(
            (booking.getPickupLocation() != null ? booking.getPickupLocation() : "N/A") +
            " → " +
            (booking.getDropoffLocation() != null ? booking.getDropoffLocation() : "N/A"));
        locationLabel.getStyleClass().add("text-muted");

        Label dateLabel = new Label(booking.getPickupDateTime() != null ?
            DateTimeUtil.formatDateTime(booking.getPickupDateTime()) + " • " + booking.getRentalDurationLabel() : "TBD");
        dateLabel.getStyleClass().add("text-muted");

        Label otpLabel = new Label("OTP: " + (booking.getOtp() != null ? booking.getOtp() : "Pending"));
        otpLabel.setStyle("-fx-text-fill: #06d6a0; -fx-font-weight: 700;");

        info.getChildren().addAll(vehicleLabel, statusLabel, locationLabel, dateLabel, otpLabel);

        Label detailLabel = new Label("Payment: " + (booking.getPaymentMethod() != null ? booking.getPaymentMethod().replace('_', ' ') : "Pending"));
        detailLabel.getStyleClass().add("text-muted");
        info.getChildren().add(detailLabel);

        VBox actions = new VBox(8);
        if (booking.isCashPaymentPending() && !booking.isPaidVerified()) {
            Button paidBtn = new Button("Mark Cash Paid");
            paidBtn.getStyleClass().add("btn-accent");
            paidBtn.setOnAction(event -> {
                boolean ok = bookingService.verifyCashPaymentForBooking(booking.getId(), SessionManager.getInstance().getCurrentUserName());
                if (ok) {
                    AlertUtil.showSuccess("Cash payment verified. Receipt is now available to the customer.");
                }
                loadDashboard();
            });
            actions.getChildren().add(paidBtn);
        } else if (booking.isPaidVerified()) {
            Label paidLabel = new Label("Paid Verified");
            paidLabel.getStyleClass().addAll("badge", "badge-success");
            actions.getChildren().add(paidLabel);
        }

        card.getChildren().addAll(info, actions);
        return card;
    }

    private HBox createRequestCard(Booking booking) {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setStyle("-fx-padding: 16; -fx-background-radius: 12px;");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        VBox.setVgrow(info, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

        Label vehicleLabel = new Label(booking.getVehicleName() != null ? booking.getVehicleName() : "Vehicle");
        vehicleLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: white; -fx-font-size: 15px;");

        Label locationLabel = new Label(
            (booking.getPickupLocation() != null ? booking.getPickupLocation() : "N/A") +
            " → " +
            (booking.getDropoffLocation() != null ? booking.getDropoffLocation() : "N/A"));
        locationLabel.getStyleClass().add("text-muted");

        Label dateLabel = new Label(booking.getPickupDateTime() != null ?
            DateTimeUtil.formatDateTime(booking.getPickupDateTime()) + " • " + booking.getRentalDurationLabel() : "TBD");
        dateLabel.getStyleClass().add("text-muted");

        Label preferenceLabel = new Label(
            booking.getPreferredDriverName() != null
                ? "Requested driver: " + booking.getPreferredDriverName()
                : "Driver preference: Any available driver"
        );
        preferenceLabel.getStyleClass().add("text-muted");

        info.getChildren().addAll(vehicleLabel, locationLabel, dateLabel, preferenceLabel);

        Button acceptBtn = new Button("Accept ✓");
        acceptBtn.getStyleClass().add("btn-accent");
        acceptBtn.setStyle("-fx-padding: 8 20;");
        acceptBtn.setOnAction(e -> {
            if (bookingService.hasActiveDriverBooking(SessionManager.getInstance().getCurrentUser().getId())) {
                AlertUtil.showError("Current Drive Active", "Finish or cancel your current drive before accepting another booking.");
                loadDashboard();
                return;
            }
            boolean success = bookingService.confirmBooking(
                booking.getId(),
                SessionManager.getInstance().getCurrentUser() != null ? SessionManager.getInstance().getCurrentUser().getId() : null
            );
            if (success) {
                AlertUtil.showSuccess("Ride accepted. Waiting for customer OTP verification.");
            } else {
                AlertUtil.showError("Request Locked", "This booking is reserved for another driver, or you already have a current drive.");
            }
            loadDashboard();
        });

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("btn-danger");
        rejectBtn.setStyle("-fx-padding: 8 20;");
        rejectBtn.setOnAction(e -> {
            if (SessionManager.getInstance().getCurrentUser() == null) {
                return;
            }
            boolean proceed = AlertUtil.showConfirmation("Reject Ride Request",
                "Reject this incoming booking request?");
            if (!proceed) {
                return;
            }
            boolean success = bookingService.rejectBookingRequest(
                booking.getId(),
                SessionManager.getInstance().getCurrentUser().getId()
            );
            if (success) {
                AlertUtil.showInfo("Request rejected", "This request was removed from your queue.");
            } else {
                AlertUtil.showError("Cannot reject", "This request is no longer available.");
            }
            loadDashboard();
        });

        VBox actions = new VBox(8, acceptBtn, rejectBtn);
        card.getChildren().addAll(info, actions);
        return card;
    }

    @FXML
    private void onVerifyOTP() {
        String otp = otpInput.getText().trim();
        if (otp.isEmpty()) {
            otpResult.setText("Please enter an OTP");
            otpResult.setStyle("-fx-text-fill: #f72585;");
            otpResult.setVisible(true);
            return;
        }

        if (otp.length() == 6 && otp.matches("\\d+")
            && SessionManager.getInstance().getCurrentUser() != null
            && bookingService.verifyRideOtp(SessionManager.getInstance().getCurrentUser().getId(), otp)) {
            otpResult.setText("✓ OTP Verified! Ride started.");
            otpResult.setStyle("-fx-text-fill: #06d6a0; -fx-font-weight: 700;");
            currentDrive = SessionManager.getInstance().getCurrentUser() != null
                ? bookingService.getCurrentBookingForDriver(SessionManager.getInstance().getCurrentUser().getId())
                : null;
            seedCurrentDriveSession(currentDrive);
            NavigationManager.navigateTo("/fxml/active_driver_ride.fxml");
            loadDashboard();
        } else {
            otpResult.setText("✗ Invalid OTP. Please try again.");
            otpResult.setStyle("-fx-text-fill: #f72585;");
        }
        otpResult.setVisible(true);
    }

    @FXML private void onRefresh() { loadDashboard(); }
    @FXML private void onShowDashboard() { showDashboardSection(); }
    @FXML public void onShowCurrentDrive() { showCurrentDriveSection(); }
    @FXML public void onShowHistory() { showHistorySection(); }
    @FXML private void onShowWallet() { showWalletSection(); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onLogout() {
        authService.logout();
        NavigationManager.clearHistory();
        NavigationManager.navigateTo("/fxml/landing.fxml");
    }

    @FXML
    private void onSaveAvailability() {
        if (currentDriverProfile == null) {
            availabilityStatusLabel.setText("Driver profile is not ready yet.");
            return;
        }
        currentDriverProfile.setWorkCity(workCityField.getText().trim());
        currentDriverProfile.setWorkStreet(workStreetField.getText().trim());
        currentDriverProfile.setWorkAddressLine1(workAddressLine1Field.getText().trim());
        currentDriverProfile.setWorkLandmark(workLandmarkField.getText().trim());
        currentDriverProfile.setDrivesVehicleCategories(List.of(vehicleCategoryCombo.getValue()));
        currentDriverProfile.setAssignedVehicleId(assignedVehicleNameField.getText().trim());
        currentDriverProfile.setPreferredWorkZone(buildServiceAddress());
        com.rento.utils.MapPoint point = findServicePoint();
        if (point != null) {
            currentDriverProfile.setCurrentLat(point.getX());
            currentDriverProfile.setCurrentLong(point.getY());
        }
        currentDriverProfile.setAvailabilityStatus(Driver.AvailabilityStatus.valueOf(availabilityStatusCombo.getValue()));
        driverDAO.updateDriver(currentDriverProfile);
        availabilityStatusLabel.setText("Availability updated. Customers will now match you by address, category, and assigned vehicle.");
        loadDashboard();
    }

    private void showDashboardSection() {
        showOnly(dashboardSection);
    }

    private void showCurrentDriveSection() {
        showOnly(currentDriveSection);
        updateCurrentDrivePanel();
    }

    private void showWalletSection() {
        showOnly(walletSection);
    }

    private void showHistorySection() {
        showOnly(historySection);
        updateHistoryPanel(allBookingCache);
    }

    private void showOnly(VBox section) {
        for (VBox candidate : List.of(dashboardSection, currentDriveSection, walletSection, historySection)) {
            if (candidate != null) {
                boolean show = candidate == section;
                candidate.setManaged(show);
                candidate.setVisible(show);
            }
        }
    }

    private void updateWalletTransactions(List<Booking> myBookings) {
        walletTransactionList.getChildren().clear();
        List<Booking> completedBookings = myBookings.stream()
            .filter(booking -> booking.getStatus() == Booking.BookingStatus.COMPLETED)
            .toList();
        if (completedBookings.isEmpty()) {
            Label empty = new Label("No credited driver transactions yet.");
            empty.getStyleClass().add("text-muted");
            walletTransactionList.getChildren().add(empty);
            return;
        }

        for (Booking booking : completedBookings) {
            double credit = booking.getTotalCost() * 0.15;
            Label item = new Label(
                booking.getVehicleName() + " • +" + ValidationUtil.formatCurrency(credit)
                    + " • Driver share from completed booking"
            );
            item.getStyleClass().add("text-body");
            walletTransactionList.getChildren().add(item);
        }
    }

    @FXML
    private void onTopUpWallet() {
        AlertUtil.showInfo("Wallet is read-only", "Driver wallets show credited transactions only. Money is added after successful completed drives.");
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

    private void loadDriverProfile() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            currentDriverProfile = null;
            return;
        }
        currentDriverProfile = driverDAO.findByUserId(SessionManager.getInstance().getCurrentUser().getId());
        if (currentDriverProfile == null) {
            currentDriverProfile = driverDAO.findByEmail(SessionManager.getInstance().getCurrentUserEmail());
        }
        if (currentDriverProfile != null) {
            availabilityStatusCombo.setValue(
                currentDriverProfile.getAvailabilityStatus() != null
                    ? currentDriverProfile.getAvailabilityStatus().name()
                    : Driver.AvailabilityStatus.OFFLINE.name()
            );
            workCityField.setText(currentDriverProfile.getWorkCity() != null ? currentDriverProfile.getWorkCity() : "");
            workStreetField.setText(currentDriverProfile.getWorkStreet() != null ? currentDriverProfile.getWorkStreet() : "");
            workAddressLine1Field.setText(currentDriverProfile.getWorkAddressLine1() != null ? currentDriverProfile.getWorkAddressLine1() : "");
            workLandmarkField.setText(currentDriverProfile.getWorkLandmark() != null ? currentDriverProfile.getWorkLandmark() : "");
            if (currentDriverProfile.getDrivesVehicleCategories() != null && !currentDriverProfile.getDrivesVehicleCategories().isEmpty()) {
                vehicleCategoryCombo.setValue(currentDriverProfile.getDrivesVehicleCategories().get(0));
            }
            assignedVehicleNameField.setText(currentDriverProfile.getAssignedVehicleId() != null ? currentDriverProfile.getAssignedVehicleId() : "");
        }
    }

    private void updateCurrentDrivePanel() {
        if (currentDrive == null && SessionManager.getInstance().getCurrentUser() != null) {
            currentDrive = bookingService.getCurrentBookingForDriver(SessionManager.getInstance().getCurrentUser().getId());
        }
        boolean hasDrive = currentDrive != null;
        currentDriveTitleLabel.setText(hasDrive ? nvl(currentDrive.getVehicleName(), "Current Drive") : "No Current Drive");
        currentDriveStatusLabel.setText(hasDrive ? currentDrive.getStatusString() : "You can accept a new ride from the dashboard.");
        currentCustomerLabel.setText(hasDrive ? nvl(currentDrive.getUserName(), "Customer") : "-");
        currentPickupLabel.setText(hasDrive ? nvl(currentDrive.getPickupAddress(), nvl(currentDrive.getPickupLocation(), "-")) : "-");
        currentDropLabel.setText(hasDrive ? nvl(currentDrive.getDropAddress(), nvl(currentDrive.getDropoffLocation(), "-")) : "-");
        currentTimeLabel.setText(hasDrive && currentDrive.getPickupDateTime() != null
            ? DateTimeUtil.formatDateTime(currentDrive.getPickupDateTime()) + " to " + DateTimeUtil.formatDateTime(currentDrive.getReturnDateTime())
            : "-");
        currentFareLabel.setText(hasDrive ? ValidationUtil.formatCurrency(currentDrive.getFinalFare()) : "-");
        boolean awaitingOtp = hasDrive && currentDrive.getStatus() == Booking.BookingStatus.CONFIRMED;
        boolean inProgress = hasDrive && currentDrive.getStatus() == Booking.BookingStatus.IN_PROGRESS;
        currentVerifyOtpBtn.setDisable(!awaitingOtp);
        currentOtpInput.setDisable(!awaitingOtp);
        currentCancelDriveBtn.setDisable(!awaitingOtp);
        currentEndDriveBtn.setDisable(!inProgress);
        currentDriveActionLabel.setText(hasDrive
            ? (awaitingOtp ? "Verify customer OTP to start. You can cancel only before OTP is accepted." : "Drive is active. End it after drop-off.")
            : "No accepted drive right now.");
    }

    private void updateHistoryPanel(List<Booking> bookings) {
        if (driverHistoryList == null) return;
        driverHistoryList.getChildren().clear();
        List<Booking> history = bookings.stream()
            .filter(booking -> booking.getStatus() == Booking.BookingStatus.COMPLETED || booking.getStatus() == Booking.BookingStatus.CANCELLED)
            .toList();
        if (history.isEmpty()) {
            Label empty = new Label("No past drives yet.");
            empty.getStyleClass().add("text-muted");
            driverHistoryList.getChildren().add(empty);
            return;
        }
        for (Booking booking : history) {
            Label item = new Label(
                nvl(booking.getVehicleName(), "Drive") + " • " + booking.getStatusString()
                    + " • " + nvl(booking.getPickupAddress(), booking.getPickupLocation())
                    + " → " + nvl(booking.getDropAddress(), booking.getDropoffLocation())
                    + " • " + ValidationUtil.formatCurrency(booking.getFinalFare())
            );
            item.getStyleClass().add("text-body");
            driverHistoryList.getChildren().add(item);
        }
    }

    @FXML
    private void onVerifyCurrentDriveOtp() {
        otpInput.setText(currentOtpInput.getText());
        onVerifyOTP();
    }

    @FXML
    private void onEndCurrentDrive() {
        if (currentDrive == null) return;
        currentDrive.setEndTime(new Date());
        boolean ok = bookingService.completeBooking(currentDrive.getId());
        if (ok) {
            AlertUtil.showSuccess("Drive ended. Driver earnings were credited and the customer was notified.");
            currentDrive = null;
            loadDashboard();
            showCurrentDriveSection();
        } else {
            AlertUtil.showError("Could not end drive", "Please refresh and try again.");
        }
    }

    @FXML
    private void onCancelCurrentDrive() {
        if (currentDrive == null || SessionManager.getInstance().getCurrentUser() == null) return;
        boolean ok = bookingService.cancelDriverBooking(currentDrive.getId(), SessionManager.getInstance().getCurrentUser().getId());
        if (ok) {
            AlertUtil.showInfo("Drive cancelled", "This ride was cancelled before OTP verification. You can now accept another drive.");
            currentDrive = null;
            loadDashboard();
            showCurrentDriveSection();
        } else {
            AlertUtil.showError("Cannot cancel", "Only drives waiting for OTP can be cancelled by the driver.");
        }
    }

    private com.rento.utils.MapPoint findServicePoint() {
        for (String value : List.of(buildServiceAddress(), workLandmarkField.getText(), workAddressLine1Field.getText(), workCityField.getText())) {
            com.rento.utils.MapPoint point = OfflineMapService.findByLabel(value);
            if (point != null) return point;
        }
        return null;
    }

    private String buildServiceAddress() {
        return String.join(", ", List.of(
            workAddressLine1Field.getText().trim(),
            workStreetField.getText().trim(),
            workLandmarkField.getText().trim(),
            workCityField.getText().trim()
        ).stream().filter(value -> !value.isBlank()).toList());
    }

    private String nvl(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private void seedCurrentDriveSession(Booking booking) {
        if (booking == null) {
            return;
        }
        Session.activeBookingId = booking.getId() != null ? booking.getId().toHexString() : null;
        Session.pendingPickupAddress = nvl(booking.getPickupAddress(), booking.getPickupLocation());
        Session.pendingDropAddress = nvl(booking.getDropAddress(), booking.getDropoffLocation());
        Session.pendingVehicleCategory = booking.getVehicleCategory();
        Session.pendingFareEstimate = booking.getFinalFare();
        Session.activeRiderName = nvl(booking.getUserName(), "Rider");
        Session.activeBookingOtp = booking.getOtp();
        MapPoint pickupPoint = OfflineMapService.findByLabel(Session.pendingPickupAddress);
        MapPoint dropPoint = OfflineMapService.findByLabel(Session.pendingDropAddress);
        if (pickupPoint != null) {
            Session.pendingPickupX = pickupPoint.getX();
            Session.pendingPickupY = pickupPoint.getY();
        }
        if (dropPoint != null) {
            Session.pendingDropX = dropPoint.getX();
            Session.pendingDropY = dropPoint.getY();
        }
        if (currentDriverProfile != null) {
            Session.activeDriverObjectId = currentDriverProfile.getId();
            Session.driverCurrentX = currentDriverProfile.getCurrentLat();
            Session.driverCurrentY = currentDriverProfile.getCurrentLong();
        }
    }

    private Node createAnalyticsPage(Integer index) {
        int pageIndex = index == null ? 0 : index;
        long inProgress = allBookingCache.stream().filter(b -> b.getStatus() == Booking.BookingStatus.IN_PROGRESS).count();
        long completed = allBookingCache.stream().filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED).count();

        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        if (pageIndex == 0) {
            Label title = new Label("Ride Mix");
            title.getStyleClass().add("heading-3");
            PieChart chart = new PieChart();
            chart.setPrefHeight(280);
            chart.getData().setAll(
                new PieChart.Data("Pending Pool", pendingBookingCache.size()),
                new PieChart.Data("Confirmed", confirmedBookingCache.size()),
                new PieChart.Data("In Progress", inProgress),
                new PieChart.Data("Completed", completed)
            );
            container.getChildren().addAll(title, chart);
            return container;
        }

        if (pageIndex == 1) {
            Label title = new Label("Ride Stage Volume");
            title.getStyleClass().add("heading-3");
            BarChart<String, Number> chart = createBarChart("Ride Stage", "Count");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Pending", pendingBookingCache.size()));
            series.getData().add(new XYChart.Data<>("Confirmed", confirmedBookingCache.size()));
            series.getData().add(new XYChart.Data<>("In Progress", inProgress));
            series.getData().add(new XYChart.Data<>("Completed", completed));
            chart.getData().add(series);
            container.getChildren().addAll(title, chart);
            return container;
        }

        Label title = new Label("Earnings Snapshot");
        title.getStyleClass().add("heading-3");
        BarChart<String, Number> chart = createBarChart("Metric", "Amount");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Completed Earnings", allBookingCache.stream()
            .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
            .mapToDouble(Booking::getTotalCost).sum() * 0.15));
        series.getData().add(new XYChart.Data<>("In Progress Value", allBookingCache.stream()
            .filter(b -> b.getStatus() == Booking.BookingStatus.IN_PROGRESS)
            .mapToDouble(Booking::getTotalCost).sum() * 0.15));
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
