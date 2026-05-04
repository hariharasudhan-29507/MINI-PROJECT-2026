package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.RentalDAO;
import com.rento.dao.UserDAO;
import com.rento.models.Booking;
import com.rento.models.PaymentMethodProfile;
import com.rento.models.Rental;
import com.rento.models.User;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.AuthService;
import com.rento.services.BookingService;
import com.rento.services.NotificationService;
import com.rento.services.OfflineMapService;
import com.rento.services.PaymentService;
import com.rento.services.PaymentMethodService;
import com.rento.utils.AlertUtil;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.MapPoint;
import com.rento.utils.Session;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Profile page.
 */
public class ProfileController implements Initializable {

    @FXML private Label avatarLabel;
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label roleLabel;
    @FXML private VBox guestNotice;
    @FXML private VBox userInfoSection;
    @FXML private HBox statsSection;
    @FXML private VBox quickActionsSection;
    @FXML private VBox walletTopUpSection;
    @FXML private VBox paymentMethodsSection;
    @FXML private Label bookingCount;
    @FXML private Label rentalCount;
    @FXML private Label accountAge;
    @FXML private Label detailName;
    @FXML private Label detailEmail;
    @FXML private Label detailPhone;
    @FXML private Label detailRole;
    @FXML private Label detailStatus;
    @FXML private Label detailAge;
    @FXML private Label detailWallet;
    @FXML private Button logoutBtn;
    @FXML private Button roleDashboardBtn;
    @FXML private Label notificationsSummaryLabel;
    @FXML private VBox currentRideCard;
    @FXML private Label currentRideStatusLabel;
    @FXML private Label currentRideRouteLabel;
    @FXML private Label currentRideMetaLabel;
    @FXML private Button openCurrentRideBtn;
    @FXML private VBox currentRentalCard;
    @FXML private Label currentRentalStatusLabel;
    @FXML private Label currentRentalVehicleLabel;
    @FXML private Label currentRentalMetaLabel;
    @FXML private Button openCurrentRentalBtn;
    @FXML private TextField walletAmountField;
    @FXML private ComboBox<String> walletMethodCombo;
    @FXML private TextField walletHolderNameField;
    @FXML private TextField walletReferenceField;
    @FXML private TextField walletExpiryField;
    @FXML private PasswordField walletCvvField;
    @FXML private Label walletTopUpStatusLabel;
    @FXML private VBox walletTopUpHistoryList;
    @FXML private ComboBox<String> paymentMethodTypeCombo;
    @FXML private TextField paymentProfileNameField;
    @FXML private TextField paymentHolderNameField;
    @FXML private TextField paymentReferenceField;
    @FXML private TextField paymentProviderField;
    @FXML private TextField paymentBillingAddressField;
    @FXML private CheckBox preferredPaymentCheck;
    @FXML private Label paymentMethodStatusLabel;
    @FXML private VBox paymentMethodList;

    private final AuthService authService = new AuthService();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final RentalDAO rentalDAO = new RentalDAO();
    private final UserDAO userDAO = new UserDAO();
    private final NotificationService notificationService = new NotificationService();
    private final PaymentService paymentService = new PaymentService();
    private final PaymentMethodService paymentMethodService = new PaymentMethodService();
    private final BookingService bookingService = new BookingService();
    private Booking activeBooking;
    private Rental activeRental;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        paymentMethodTypeCombo.setItems(FXCollections.observableArrayList("Credit Card", "UPI"));
        paymentMethodTypeCombo.setValue("Credit Card");
        paymentMethodTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePaymentMethodHints());
        updatePaymentMethodHints();
        walletMethodCombo.setItems(FXCollections.observableArrayList("Credit Card", "UPI"));
        walletMethodCombo.setValue("Credit Card");
        walletMethodCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateWalletTopUpHints());
        updateWalletTopUpHints();
        if (SessionManager.getInstance().isGuest()) {
            showGuestMode();
        } else {
            showUserProfile();
        }
    }

    private void showGuestMode() {
        guestNotice.setVisible(true);
        guestNotice.setManaged(true);
        userInfoSection.setVisible(false);
        userInfoSection.setManaged(false);

        nameLabel.setText("Guest");
        emailLabel.setText("Not signed in");
        roleLabel.setText("GUEST");
        avatarLabel.setText("G");
        logoutBtn.setVisible(false);
        roleDashboardBtn.setVisible(false);
        roleDashboardBtn.setManaged(false);
    }

    private void showUserProfile() {
        guestNotice.setVisible(false);
        guestNotice.setManaged(false);
        userInfoSection.setVisible(true);
        userInfoSection.setManaged(true);
        logoutBtn.setVisible(true);

        User sessionUser = SessionManager.getInstance().getCurrentUser();
        User user = sessionUser != null ? userDAO.findById(sessionUser.getId()) : null;
        if (user != null) {
            boolean isAdmin = user.getRole() == User.Role.ADMIN;
            nameLabel.setText(user.getFullName());
            emailLabel.setText(user.getEmail());
            roleLabel.setText(formatRole(user.getRole()));
            avatarLabel.setText(user.getFullName().substring(0, 1).toUpperCase());

            detailName.setText(user.getFullName());
            detailEmail.setText(user.getEmail());
            detailPhone.setText(user.getPhone() != null ? user.getPhone() : "Not set");
            detailRole.setText(formatRole(user.getRole()));
            detailStatus.setText(user.isLocked()
                ? "● Locked"
                : user.isVerified() ? "● Verified" : "● Unverified");
            detailAge.setText(user.getAge() > 0 ? String.valueOf(user.getAge()) : "Not set");
            detailWallet.setText(com.rento.utils.ValidationUtil.formatCurrency(user.getWalletBalance()));

            if (user.getCreatedAt() != null) {
                accountAge.setText(new SimpleDateFormat("MMM yyyy").format(user.getCreatedAt()));
            }

            if (!isAdmin) {
                if (user.getRole() == User.Role.DRIVER) {
                    bookingCount.setText(String.valueOf(bookingDAO.findByDriver(user.getId()).size()));
                } else {
                    bookingCount.setText(String.valueOf(bookingDAO.findByUser(user.getId()).size()));
                }
                if (user.getRole() == User.Role.SUPPLIER) {
                    rentalCount.setText(String.valueOf(rentalDAO.findBySupplier(user.getId()).size()));
                } else {
                    rentalCount.setText(String.valueOf(rentalDAO.findByRenter(user.getId()).size()));
                }
            }
            roleDashboardBtn.setVisible(true);
            roleDashboardBtn.setManaged(true);
            roleDashboardBtn.setText(getRoleDashboardLabel(user.getRole()));
            statsSection.setVisible(!isAdmin);
            statsSection.setManaged(!isAdmin);
            quickActionsSection.setVisible(!isAdmin);
            quickActionsSection.setManaged(!isAdmin);
            walletTopUpSection.setVisible(!isAdmin);
            walletTopUpSection.setManaged(!isAdmin);
            paymentMethodsSection.setVisible(!isAdmin);
            paymentMethodsSection.setManaged(!isAdmin);
            loadNotificationsSummary(user);
            if (!isAdmin) {
                loadCurrentRideCard(user);
                loadCurrentRentalCard(user);
                loadWalletTopUpHistory(user);
                loadPaymentMethods(user);
            }
        }
    }

    private void loadCurrentRideCard(User user) {
        if (currentRideCard == null) {
            return;
        }
        List<Booking> bookings = user.getRole() == User.Role.DRIVER
            ? bookingDAO.findByDriver(user.getId())
            : bookingDAO.findByUser(user.getId());
        activeBooking = bookings.stream()
            .filter(booking -> {
                String status = booking.getStatusString();
                return "CONFIRMED".equals(status)
                    || "ACCEPTED".equals(status)
                    || "DRIVER_ARRIVED".equals(status)
                    || "ACTIVE".equals(status)
                    || "IN_PROGRESS".equals(status);
            })
            .findFirst()
            .orElse(null);
        boolean show = activeBooking != null;
        currentRideCard.setVisible(show);
        currentRideCard.setManaged(show);
        if (openCurrentRideBtn != null) {
            openCurrentRideBtn.setDisable(!show);
        }
        if (!show) {
            return;
        }
        currentRideStatusLabel.setText(activeBooking.getStatusString().replace('_', ' '));
        currentRideRouteLabel.setText(
            (activeBooking.getPickupLocation() != null ? activeBooking.getPickupLocation() : "Pickup")
                + " → "
                + (activeBooking.getDropoffLocation() != null ? activeBooking.getDropoffLocation() : "Drop"));
        currentRideMetaLabel.setText(DateTimeUtil.formatDateTime(activeBooking.getPickupDateTime())
            + " • " + activeBooking.getRentalDurationLabel());
    }

    private void loadCurrentRentalCard(User user) {
        if (currentRentalCard == null || user == null || user.getRole() != User.Role.USER) {
            if (currentRentalCard != null) {
                currentRentalCard.setVisible(false);
                currentRentalCard.setManaged(false);
            }
            return;
        }
        activeRental = rentalDAO.findByRenter(user.getId()).stream()
            .filter(rental -> {
                String status = rental.getStatus() != null ? rental.getStatus().name() : "";
                return "REQUESTED".equals(status)
                    || "APPROVED".equals(status)
                    || "ACTIVE".equals(status)
                    || "OVERDUE".equals(status);
            })
            .findFirst()
            .orElse(null);
        boolean show = activeRental != null;
        currentRentalCard.setVisible(show);
        currentRentalCard.setManaged(show);
        if (openCurrentRentalBtn != null) {
            openCurrentRentalBtn.setDisable(!show);
        }
        if (!show) {
            return;
        }
        currentRentalStatusLabel.setText(activeRental.getStatus().name().replace('_', ' '));
        currentRentalVehicleLabel.setText((activeRental.getVehicleName() != null ? activeRental.getVehicleName() : "Vehicle")
            + " • " + (activeRental.getVehicleLicensePlate() != null ? activeRental.getVehicleLicensePlate() : "Plate pending"));
        currentRentalMetaLabel.setText("Supplier: " + (activeRental.getSupplierName() != null ? activeRental.getSupplierName() : "Supplier")
            + " • Depot: " + (activeRental.getDepotLocation() != null ? activeRental.getDepotLocation() : "Supplier Depot")
            + " • " + activeRental.getRentalDurationLabel());
    }

    @FXML
    private void onOpenCurrentRide() {
        if (activeBooking == null) {
            AlertUtil.showInfo("Current Ride", "No current ride is available right now.");
            return;
        }
        seedRideSession(activeBooking);
        if (activeBooking.getStatus() == Booking.BookingStatus.IN_PROGRESS
            || activeBooking.getStatus() == Booking.BookingStatus.ACTIVE) {
            NavigationManager.navigateTo("/fxml/active_ride.fxml");
        } else {
            NavigationManager.navigateTo("/fxml/booking_confirmed.fxml");
        }
    }

    @FXML
    private void onOpenCurrentRental() {
        if (activeRental == null) {
            AlertUtil.showInfo("Current Rental", "No current rental is available right now.");
            return;
        }
        Session.activeRentalId = activeRental.getId() != null ? activeRental.getId().toHexString() : null;
        NavigationManager.navigateTo("/fxml/rent.fxml");
    }

    private void loadNotificationsSummary(User user) {
        List<org.bson.Document> notifications = notificationService.getNotifications(user.getId());
        notificationsSummaryLabel.setText(notifications.isEmpty()
            ? "No notifications yet."
            : "You have " + notifications.size() + " notification(s). Click Download to save all.");
    }

    @FXML
    private void onLogout() {
        authService.logout();
        NavigationManager.clearHistory();
        NavigationManager.navigateTo("/fxml/landing.fxml");
    }

    @FXML private void onLogin() { NavigationManager.navigateTo("/fxml/login.fxml"); }
    @FXML private void onRegister() { NavigationManager.navigateTo("/fxml/register.fxml"); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onBack() {
        if (NavigationManager.canGoBack()) {
            NavigationManager.goBack();
        } else {
            NavigationManager.navigateTo("/fxml/landing.fxml");
        }
    }
    @FXML private void onNavBook() {
        if (SessionManager.getInstance().getCurrentRole() == User.Role.USER) {
            NavigationManager.navigateTo("/fxml/booking.fxml");
        } else {
            onOpenRoleDashboard();
        }
    }
    @FXML private void onNavRent() {
        if (SessionManager.getInstance().getCurrentRole() == User.Role.USER) {
            NavigationManager.navigateTo("/fxml/rent.fxml");
        } else {
            onOpenRoleDashboard();
        }
    }
    @FXML private void onNavContact() { NavigationManager.navigateTo("/fxml/contact.fxml"); }
    @FXML private void onNavHistory() {
        User.Role role = SessionManager.getInstance().getCurrentRole();
        if (role == User.Role.DRIVER) {
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml", controller -> {
                if (controller instanceof DriverDashboardController driverDashboardController) {
                    driverDashboardController.onShowHistory();
                }
            });
        } else if (role == User.Role.SUPPLIER) {
            NavigationManager.navigateTo("/fxml/supplier_dashboard.fxml", controller -> {
                if (controller instanceof SupplierDashboardController supplierDashboardController) {
                    supplierDashboardController.onShowHistory();
                }
            });
        } else {
            NavigationManager.navigateTo("/fxml/rides_history.fxml");
        }
    }
    @FXML private void onOpenRoleDashboard() {
        User.Role role = SessionManager.getInstance().getCurrentRole();
        switch (role) {
            case DRIVER:
                NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
                break;
            case SUPPLIER:
                NavigationManager.navigateTo("/fxml/supplier_dashboard.fxml");
                break;
            case ADMIN:
                NavigationManager.navigateTo("/fxml/admin_dashboard.fxml");
                break;
            default:
                NavigationManager.navigateTo("/fxml/rent.fxml");
                break;
        }
    }

    @FXML
    private void onShowNotifications() {
        if (SessionManager.getInstance().isGuest() || SessionManager.getInstance().getCurrentUser() == null) {
            AlertUtil.showInfo("Notifications", "Please sign in to view notifications.");
            return;
        }
        List<org.bson.Document> notifications = notificationService.getNotifications(SessionManager.getInstance().getCurrentUser().getId());
        if (notifications.isEmpty()) {
            AlertUtil.showInfo("Notifications", "No notifications yet.");
            return;
        }
        StringBuilder message = new StringBuilder();
        for (org.bson.Document n : notifications) {
            message.append("• ").append(n.getString("title")).append("\n");
        }
        AlertUtil.showInfo("Notifications", message.toString());
    }

    @FXML
    private void onDownloadNotifications() {
        if (SessionManager.getInstance().isGuest() || SessionManager.getInstance().getCurrentUser() == null) {
            AlertUtil.showInfo("Notifications", "Please sign in to download notifications.");
            return;
        }
        try {
            String outputDir = System.getProperty("user.home") + "\\Documents\\RentoNotifications";
            String path = notificationService.exportNotifications(SessionManager.getInstance().getCurrentUser().getId(), outputDir);
            AlertUtil.showSuccess("Notifications downloaded:\n" + path);
        } catch (Exception ex) {
            AlertUtil.showError("Download Failed", ex.getMessage());
        }
    }

    @FXML
    private void onSavePaymentMethod() {
        if (SessionManager.getInstance().isGuest() || SessionManager.getInstance().getCurrentUser() == null) {
            AlertUtil.showInfo("Payment Methods", "Please sign in to save a payment method.");
            return;
        }
        String error = paymentMethodService.savePaymentMethod(
            SessionManager.getInstance().getCurrentUser().getId(),
            mapMethodType(),
            paymentProfileNameField.getText(),
            paymentHolderNameField.getText(),
            paymentReferenceField.getText(),
            paymentProviderField.getText(),
            paymentBillingAddressField.getText(),
            preferredPaymentCheck.isSelected()
        );
        if (error != null) {
            paymentMethodStatusLabel.setText(error);
            return;
        }
        paymentMethodStatusLabel.setText("Payment method saved successfully.");
        paymentProfileNameField.clear();
        paymentHolderNameField.clear();
        paymentReferenceField.clear();
        paymentProviderField.clear();
        paymentBillingAddressField.clear();
        preferredPaymentCheck.setSelected(false);
        if (SessionManager.getInstance().getCurrentUser() != null) {
            User user = userDAO.findById(SessionManager.getInstance().getCurrentUser().getId());
            if (user != null) {
                loadPaymentMethods(user);
            }
        }
    }

    @FXML
    private void onTopUpWallet() {
        if (SessionManager.getInstance().isGuest() || SessionManager.getInstance().getCurrentUser() == null) {
            AlertUtil.showInfo("Wallet", "Please sign in to add money to your wallet.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(walletAmountField.getText().trim());
        } catch (Exception ex) {
            walletTopUpStatusLabel.setText("Enter a valid wallet top-up amount.");
            return;
        }
        PaymentService topUpService = paymentService;
        com.rento.models.Payment.PaymentMethod method = "UPI".equals(walletMethodCombo.getValue())
            ? com.rento.models.Payment.PaymentMethod.UPI
            : com.rento.models.Payment.PaymentMethod.CREDIT_CARD;
        com.rento.models.Payment payment = topUpService.topUpWallet(
            SessionManager.getInstance().getCurrentUser().getId(),
            amount,
            method,
            walletReferenceField.getText(),
            walletHolderNameField.getText(),
            walletExpiryField.getText(),
            walletCvvField.getText()
        );
        if (payment == null) {
            walletTopUpStatusLabel.setText("Wallet top-up failed. Check payment details and try again.");
            return;
        }
        walletTopUpStatusLabel.setText("Wallet top-up successful: " + com.rento.utils.ValidationUtil.formatCurrency(amount)
            + " • Ref " + payment.getTransactionRef());
        walletAmountField.clear();
        walletReferenceField.clear();
        walletHolderNameField.clear();
        walletExpiryField.clear();
        walletCvvField.clear();
        User refreshed = userDAO.findById(SessionManager.getInstance().getCurrentUser().getId());
        if (refreshed != null) {
            detailWallet.setText(com.rento.utils.ValidationUtil.formatCurrency(refreshed.getWalletBalance()));
            loadWalletTopUpHistory(refreshed);
        }
    }

    private String formatRole(User.Role role) {
        return role == null ? "Guest" : role.name().replace('_', ' ');
    }

    private String getRoleDashboardLabel(User.Role role) {
        switch (role) {
            case DRIVER:
                return "Driver Dashboard";
            case SUPPLIER:
                return "Supplier Dashboard";
            case ADMIN:
                return "Admin Dashboard";
            default:
                return "Rental Marketplace";
        }
    }

    private void loadPaymentMethods(User user) {
        paymentMethodList.getChildren().clear();
        for (PaymentMethodProfile profile : paymentMethodService.getPaymentMethodsForUser(user.getId())) {
            Label label = new Label(profile.getProfileName() + " • "
                + profile.getMethodType().name().replace('_', ' ') + " • "
                + profile.getMaskedReference()
                + (profile.isPreferred() ? " • Preferred" : ""));
            label.getStyleClass().add("text-body");
            paymentMethodList.getChildren().add(label);
        }
        if (paymentMethodList.getChildren().isEmpty()) {
            Label empty = new Label("No saved payment methods yet.");
            empty.getStyleClass().add("text-muted");
            paymentMethodList.getChildren().add(empty);
        }
    }

    private void loadWalletTopUpHistory(User user) {
        walletTopUpHistoryList.getChildren().clear();
        for (com.rento.models.Payment payment : paymentService.getWalletTopUpsByUser(user.getId())) {
            Label item = new Label(
                com.rento.utils.ValidationUtil.formatCurrency(payment.getTotalAmount())
                    + " • " + payment.getPaymentMethod()
                    + " • " + payment.getTransactionRef()
            );
            item.getStyleClass().add("text-body");
            walletTopUpHistoryList.getChildren().add(item);
        }
        if (walletTopUpHistoryList.getChildren().isEmpty()) {
            Label empty = new Label("No wallet top-ups yet.");
            empty.getStyleClass().add("text-muted");
            walletTopUpHistoryList.getChildren().add(empty);
        }
    }

    private void updatePaymentMethodHints() {
        String type = paymentMethodTypeCombo.getValue();
        if ("UPI".equals(type)) {
            paymentReferenceField.setPromptText("name@bank");
            paymentProviderField.setPromptText("UPI app or bank");
        } else {
            paymentReferenceField.setPromptText("1234 5678 9012 3456");
            paymentProviderField.setPromptText("Card issuer");
        }
    }

    private void updateWalletTopUpHints() {
        boolean upi = "UPI".equals(walletMethodCombo.getValue());
        walletReferenceField.setPromptText(upi ? "name@bank" : "1234 5678 9012 3456");
        walletHolderNameField.setPromptText(upi ? "UPI account holder" : "Card holder name");
        walletExpiryField.setDisable(upi);
        walletCvvField.setDisable(upi);
        if (upi) {
            walletExpiryField.clear();
            walletCvvField.clear();
        }
    }

    private PaymentMethodProfile.MethodType mapMethodType() {
        return switch (paymentMethodTypeCombo.getValue()) {
            case "UPI" -> PaymentMethodProfile.MethodType.UPI;
            default -> PaymentMethodProfile.MethodType.CREDIT_CARD;
        };
    }

    private void seedRideSession(Booking booking) {
        Session.activeBookingId = booking.getId() != null ? booking.getId().toHexString() : null;
        Session.pendingPickupAddress = booking.getPickupAddress() != null ? booking.getPickupAddress() : booking.getPickupLocation();
        Session.pendingDropAddress = booking.getDropAddress() != null ? booking.getDropAddress() : booking.getDropoffLocation();
        Session.pendingVehicleCategory = booking.getVehicleCategory();
        Session.pendingFareEstimate = booking.getFinalFare();
        Session.activeBookingOtp = booking.getOtp();
        Session.activeDriverId = booking.getDriverId() != null ? booking.getDriverId().toHexString() : null;
        if (booking.getPickupLat() != 0 || booking.getPickupLng() != 0) {
            Session.pendingPickupX = booking.getPickupLat();
            Session.pendingPickupY = booking.getPickupLng();
        }
        if (booking.getDropLat() != 0 || booking.getDropLng() != 0) {
            Session.pendingDropX = booking.getDropLat();
            Session.pendingDropY = booking.getDropLng();
        }
        MapPoint pickupPoint = OfflineMapService.findByLabel(Session.pendingPickupAddress);
        MapPoint dropPoint = OfflineMapService.findByLabel(Session.pendingDropAddress);
        if ((Session.pendingPickupX == 0 && Session.pendingPickupY == 0) && pickupPoint != null) {
            Session.pendingPickupX = pickupPoint.getX();
            Session.pendingPickupY = pickupPoint.getY();
        }
        if ((Session.pendingDropX == 0 && Session.pendingDropY == 0) && dropPoint != null) {
            Session.pendingDropX = dropPoint.getX();
            Session.pendingDropY = dropPoint.getY();
        }
        if (booking.getDriverId() != null) {
            var driverBooking = bookingService.getBookingById(booking.getId());
            if (driverBooking != null && driverBooking.getDriverId() != null) {
                Session.activeDriverId = driverBooking.getDriverId().toHexString();
            }
        }
    }
}
