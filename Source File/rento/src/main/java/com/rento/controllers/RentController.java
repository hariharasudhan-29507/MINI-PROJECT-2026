package com.rento.controllers;

import com.rento.dao.VehicleDAO;
import com.rento.models.Payment;
import com.rento.models.PaymentMethodProfile;
import com.rento.models.Rental;
import com.rento.models.User;
import com.rento.models.Vehicle;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.PaymentMethodService;
import com.rento.services.PaymentService;
import com.rento.services.RentalService;
import com.rento.utils.AlertUtil;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Role-aware marketplace for supplier listings and user rental requests.
 */
public class RentController implements Initializable {

    @FXML private TextField  makeField;
    @FXML private Label      makeError;
    @FXML private TextField  modelField;
    @FXML private Label      modelError;
    @FXML private TextField  yearField;
    @FXML private Label      yearError;
    @FXML private TextField  plateField;
    @FXML private Label      plateError;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> fuelCombo;
    @FXML private TextField  seatsField;
    @FXML private Label      seatsError;
    @FXML private TextField  priceField;
    @FXML private Label      priceError;
    @FXML private TextField  depotField;
    @FXML private TextArea   descField;
    @FXML private Label      descError;
    @FXML private Label      errorLabel;        // top banner — SHORT
    @FXML private Button profileBtn;
    @FXML private Label pageSubtitle;

    @FXML private VBox supplierListingSection;
    @FXML private FlowPane listingGrid;
    @FXML private Label noListingsLabel;
    @FXML private Label supplierActionLabel;

    @FXML private VBox marketplaceSection;
    @FXML private FlowPane marketplaceGrid;
    @FXML private Label noMarketplaceLabel;
    @FXML private Label selectedVehicleLabel;
    @FXML private Label selectedSupplierLabel;
    @FXML private Label selectedRateLabel;
    @FXML private DatePicker requestStartDate;
    @FXML private DatePicker requestEndDate;
    @FXML private ComboBox<String> requestStartTimeCombo;
    @FXML private ComboBox<String> requestEndTimeCombo;
    @FXML private ComboBox<String> requestUnitCombo;
    @FXML private TextField requestHoursField;
    @FXML private ComboBox<String> rentalPaymentMethodCombo;
    @FXML private TextField  rentalPaymentReferenceField;
    @FXML private Label      rentalPayRefError;
    @FXML private TextField  rentalPaymentHolderField;
    @FXML private Label      rentalPayHolderError;
    @FXML private Label requestInfoLabel;
    @FXML private Button requestRentalBtn;

    @FXML private VBox myRentalsSection;
    @FXML private FlowPane myRentalsGrid;
    @FXML private Label noUserRentalsLabel;

    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final RentalService rentalService = new RentalService();
    private final PaymentService paymentService = new PaymentService();
    private final PaymentMethodService paymentMethodService = new PaymentMethodService();
    private Vehicle selectedVehicle;
    private Vehicle editingVehicle;
    private PaymentMethodProfile preferredPaymentProfile;

    // CSS for field highlighting
    private static final String STYLE_ERROR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_CLEAR =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (SessionManager.getInstance().getCurrentRole() == User.Role.DRIVER) {
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml"); return;
        }
        if (SessionManager.getInstance().getCurrentRole() == User.Role.ADMIN) {
            NavigationManager.navigateTo("/fxml/admin_dashboard.fxml"); return;
        }
        updateProfileButton();

        categoryCombo.setItems(FXCollections.observableArrayList(
            "SEDAN", "SUV", "HATCHBACK", "COUPE", "TRUCK", "VAN", "BIKE", "SCOOTER", "BUS"));
        categoryCombo.setValue("SEDAN");
        fuelCombo.setItems(FXCollections.observableArrayList(
            "PETROL", "DIESEL", "ELECTRIC", "HYBRID", "CNG"));
        fuelCombo.setValue("PETROL");

        requestStartDate.setValue(LocalDate.now().plusDays(1));
        requestEndDate.setValue(LocalDate.now().plusDays(1));
        requestStartTimeCombo.setItems(FXCollections.observableArrayList(DateTimeUtil.buildTimeSlots()));
        requestEndTimeCombo.setItems(FXCollections.observableArrayList(DateTimeUtil.buildTimeSlots()));
        requestStartTimeCombo.setValue("09:00 AM");
        requestEndTimeCombo.setValue("06:00 PM");
        requestUnitCombo.setItems(FXCollections.observableArrayList("DAYS", "HOURS"));
        requestUnitCombo.setValue("DAYS");
        requestUnitCombo.valueProperty().addListener((obs, o, n) -> {
            updateRentalUnitState();
            clearAll();
        });
        rentalPaymentMethodCombo.setItems(FXCollections.observableArrayList("Credit Card", "UPI", "Cash on Delivery"));
        rentalPaymentMethodCombo.setValue("Credit Card");
        rentalPaymentMethodCombo.valueProperty().addListener((obs, o, n) -> {
            updateRentalPaymentFields();
            clearAll();
        });
        updateRentalPaymentFields();
        updateRentalUnitState();

        // Clear highlights on type — supplier form
        attachClear(makeField,  makeError);
        attachClear(modelField, modelError);
        attachClear(yearField,  yearError);
        attachClear(plateField, plateError);
        attachClear(seatsField, seatsError);
        attachClear(priceField, priceError);
        // Clear highlights on type — rental request form
        attachClear(rentalPaymentReferenceField, rentalPayRefError);
        attachClear(rentalPaymentHolderField,    rentalPayHolderError);

        configureSectionsByRole();
        loadPreferredPaymentProfile();
        loadSupplierListings();
        loadMarketplace();
        loadMyRentals();
    }

    private void configureSectionsByRole() {
        User.Role role = SessionManager.getInstance().getCurrentRole();
        boolean supplierMode = role == User.Role.SUPPLIER;
        boolean renterMode = role == User.Role.USER;

        supplierListingSection.setManaged(supplierMode);
        supplierListingSection.setVisible(supplierMode);

        marketplaceSection.setManaged(renterMode);
        marketplaceSection.setVisible(renterMode);

        myRentalsSection.setManaged(renterMode);
        myRentalsSection.setVisible(renterMode);

        if (role == User.Role.SUPPLIER) {
            pageSubtitle.setText("Publish vehicles for rent and monitor customer requests from your supplier dashboard.");
        } else {
            pageSubtitle.setText("Choose a supplier vehicle, request a rental period, and finish on time to avoid penalties.");
        }
    }

    private void updateProfileButton() {
        if (SessionManager.getInstance().isLoggedIn()) {
            profileBtn.setText("⬤ " + SessionManager.getInstance().getCurrentUserName());
        }
    }

    @FXML
    private void onSubmitVehicle() {
        clearAll();
        boolean anyError = false;

        if (!ValidationUtil.isNotEmpty(makeField.getText()) || makeField.getText().trim().length() < 2)
            { markF(makeField, makeError, "Vehicle make is required (min 2 characters)"); anyError = true; }
        if (!ValidationUtil.isNotEmpty(modelField.getText()) || modelField.getText().trim().length() < 2)
            { markF(modelField, modelError, "Vehicle model is required (min 2 characters)"); anyError = true; }

        int year = 0;
        if (!ValidationUtil.isNotEmpty(yearField.getText())) {
            markF(yearField, yearError, "Year is required"); anyError = true;
        } else {
            try {
                year = Integer.parseInt(yearField.getText().trim());
                if (year < 2000 || year > 2035)
                    { markF(yearField, yearError, "Year must be between 2000 and 2035"); anyError = true; }
            } catch (NumberFormatException e)
                { markF(yearField, yearError, "Year must be a valid number"); anyError = true; }
        }

        double price = 0;
        if (!ValidationUtil.isNotEmpty(priceField.getText())) {
            markF(priceField, priceError, "Daily rate is required"); anyError = true;
        } else {
            try {
                price = Double.parseDouble(priceField.getText().trim());
                if (price <= 0) { markF(priceField, priceError, "Daily rate must be greater than zero"); anyError = true; }
            } catch (NumberFormatException e)
                { markF(priceField, priceError, "Daily rate must be a valid number"); anyError = true; }
        }

        int seats = 5;
        if (!seatsField.getText().isEmpty()) {
            try {
                seats = Integer.parseInt(seatsField.getText().trim());
                if (seats <= 0 || seats > 60)
                    { markF(seatsField, seatsError, "Seats must be between 1 and 60"); anyError = true; }
            } catch (NumberFormatException e)
                { markF(seatsField, seatsError, "Seats must be a valid number"); anyError = true; }
        }

        if (!plateField.getText().trim().isEmpty() && plateField.getText().trim().length() < 6)
            { markF(plateField, plateError, "License plate must be at least 6 characters"); anyError = true; }

        if (descField.getText() != null && !descField.getText().isBlank()
                && descField.getText().trim().length() < 10)
            { if (descError != null) markF(null, descError, "Description should be at least 10 characters"); anyError = true; }

        if (anyError) { showBanner("⚠  Fix the highlighted fields to continue."); return; }

        try {
            Vehicle vehicle = new Vehicle();
            vehicle.setMake(makeField.getText().trim());
            vehicle.setModel(modelField.getText().trim());
            vehicle.setYear(year);
            vehicle.setLicensePlate(plateField.getText().trim());
            vehicle.setCategory(Vehicle.Category.valueOf(categoryCombo.getValue()));
            vehicle.setFuelType(Vehicle.FuelType.valueOf(fuelCombo.getValue()));
            vehicle.setSeats(seats);
            vehicle.setDailyRate(price);
            vehicle.setDescription(descField.getText() != null ? descField.getText().trim() : "");
            vehicle.setBranchLocation(depotField.getText() != null && !depotField.getText().isBlank()
                ? depotField.getText().trim()
                : "Supplier Hub");
            vehicle.setOwnerId(SessionManager.getInstance().getCurrentUser().getId());
            vehicle.setApprovalStatus(Vehicle.ApprovalStatus.PENDING);
            if (editingVehicle != null) {
                vehicle.setId(editingVehicle.getId());
                vehicle.setCreatedAt(editingVehicle.getCreatedAt());
                vehicleDAO.updateVehicle(vehicle);
                AlertUtil.showSuccess("Vehicle update sent to admin for approval.");
            } else {
                rentalService.addVehicleForRent(vehicle);
                AlertUtil.showSuccess("Vehicle listing sent to admin for approval.");
            }
            clearForm();
            loadSupplierListings();
            loadMarketplace();
        } catch (Exception e) { showBanner("⚠  " + e.getMessage()); }
    }

    private void loadSupplierListings() {
        listingGrid.getChildren().clear();
        noListingsLabel.setVisible(true);

        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }

        List<Vehicle> vehicles = vehicleDAO.findByOwner(SessionManager.getInstance().getCurrentUser().getId());
        if (!vehicles.isEmpty()) {
            noListingsLabel.setVisible(false);
            for (Vehicle vehicle : vehicles) {
                listingGrid.getChildren().add(createListingCard(vehicle));
            }
        }
    }

    private void loadMarketplace() {
        marketplaceGrid.getChildren().clear();
        List<Vehicle> vehicles = rentalService.getMarketplaceVehicles();
        noMarketplaceLabel.setVisible(vehicles.isEmpty());
        for (Vehicle vehicle : vehicles) {
            marketplaceGrid.getChildren().add(createMarketplaceCard(vehicle));
        }
    }

    private void loadMyRentals() {
        myRentalsGrid.getChildren().clear();
        noUserRentalsLabel.setVisible(true);

        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }

        List<Rental> rentals = rentalService.getRentalsByRenter(SessionManager.getInstance().getCurrentUser().getId());
        if (!rentals.isEmpty()) {
            noUserRentalsLabel.setVisible(false);
            for (Rental rental : rentals) {
                myRentalsGrid.getChildren().add(createUserRentalCard(rental));
            }
        }
    }

    private VBox createListingCard(Vehicle vehicle) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPrefWidth(250);

        Label name = new Label(vehicle.getDisplayName());
        name.getStyleClass().add("card-title");

        Label status = new Label("● " + vehicle.getStatus().name());
        status.getStyleClass().addAll("badge", vehicle.getStatus() == Vehicle.Status.AVAILABLE ? "badge-success" : "badge-warning");

        Vehicle.ApprovalStatus approvalStatus = vehicle.getApprovalStatus() != null
            ? vehicle.getApprovalStatus()
            : Vehicle.ApprovalStatus.APPROVED;
        Label approval = new Label("Approval: " + approvalStatus.name());
        approval.getStyleClass().addAll("badge",
            approvalStatus == Vehicle.ApprovalStatus.APPROVED ? "badge-success"
                : approvalStatus == Vehicle.ApprovalStatus.REJECTED ? "badge-danger" : "badge-warning");

        Label rate = new Label("₹" + String.format("%.0f", vehicle.getDailyRate()) + "/day");
        rate.getStyleClass().add("vehicle-price");

        Label details = new Label((vehicle.getCategory() != null ? vehicle.getCategory().name() : "N/A") + " • "
            + vehicle.getSeats() + " seats • Depot: " + nvl(vehicle.getBranchLocation(), "Supplier Depot"));
        details.getStyleClass().add("text-muted");

        Button editBtn = new Button("Edit & Resubmit");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> beginEdit(vehicle));

        card.getChildren().addAll(name, status, approval, rate, details, editBtn);
        return card;
    }

    private VBox createMarketplaceCard(Vehicle vehicle) {
        VBox card = new VBox(10);
        card.getStyleClass().add("vehicle-card");
        card.setPrefWidth(260);

        VBox body = new VBox(8);
        body.getStyleClass().add("vehicle-card-body");

        Label name = new Label(vehicle.getDisplayName());
        name.getStyleClass().add("vehicle-name");

        Label rate = new Label("₹" + String.format("%.0f", vehicle.getDailyRate()) + "/day");
        rate.getStyleClass().add("vehicle-price");

        Label details = new Label((vehicle.getCategory() != null ? vehicle.getCategory().name() : "N/A") + " • "
            + vehicle.getFuelType() + " • " + vehicle.getSeats() + " seats • Depot: " + nvl(vehicle.getBranchLocation(), "Supplier Depot"));
        details.getStyleClass().add("text-muted");

        Button selectBtn = new Button("Select Rental");
        selectBtn.getStyleClass().add("btn-primary");
        selectBtn.setMaxWidth(Double.MAX_VALUE);
        selectBtn.setOnAction(e -> selectVehicle(vehicle));

        body.getChildren().addAll(name, rate, details, selectBtn);
        card.getChildren().add(body);
        return card;
    }

    private VBox createUserRentalCard(Rental rental) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPrefWidth(280);

        Label name = new Label(rental.getVehicleName());
        name.getStyleClass().add("card-title");

        String badgeStyle = rental.getStatus() == Rental.RentalStatus.COMPLETED ? "badge-success"
            : rental.getStatus() == Rental.RentalStatus.OVERDUE ? "badge-danger"
            : rental.getStatus() == Rental.RentalStatus.APPROVED ? "badge-primary"
            : rental.getStatus() == Rental.RentalStatus.REJECTED ? "badge-warning"
            : "badge-primary";
        Label status = new Label("● " + rental.getStatus().name().replace('_', ' '));
        status.getStyleClass().addAll("badge", badgeStyle);

        Label schedule = new Label("Period: " + rental.getRentalDurationLabel() + " • " + rental.getRentalDays() + " billable day(s)");
        schedule.getStyleClass().add("text-muted");

        Label meta = new Label(
            "Supplier: " + nvl(rental.getSupplierName(), "Supplier")
                + " • Plate: " + nvl(rental.getVehicleLicensePlate(), "Pending")
                + " • Depot: " + nvl(rental.getDepotLocation(), "Supplier Depot")
        );
        meta.getStyleClass().add("text-body");

        Label total = new Label("Base: " + ValidationUtil.formatCurrency(rental.getTotalAmount()));
        total.getStyleClass().add("text-body");

        Label penalty = new Label("Penalty: " + ValidationUtil.formatCurrency(rental.getPenaltyAmount()));
        penalty.getStyleClass().add(rental.getPenaltyAmount() > 0 ? "text-error" : "text-muted");

        card.getChildren().addAll(name, status, schedule, meta, total, penalty);

        if (rental.getStatus() == Rental.RentalStatus.REQUESTED || rental.getStatus() == Rental.RentalStatus.APPROVED) {
            Button cancelBtn = new Button("Cancel Rental Request");
            cancelBtn.getStyleClass().add("btn-danger");
            cancelBtn.setOnAction(e -> {
                if (SessionManager.getInstance().getCurrentUser() == null) {
                    return;
                }
                boolean confirmed = AlertUtil.showConfirmation("Cancel Rental",
                    "Cancel this rental request before it starts?");
                if (!confirmed) {
                    return;
                }
                boolean ok = rentalService.cancelRentalByUser(rental.getId(), SessionManager.getInstance().getCurrentUser().getId());
                if (ok) {
                    AlertUtil.showSuccess("Rental request cancelled.");
                    loadMarketplace();
                    loadMyRentals();
                } else {
                    AlertUtil.showError("Cannot cancel", "Only rentals that have not started can be cancelled here.");
                }
            });
            card.getChildren().add(cancelBtn);
        }

        if (rental.getStatus() == Rental.RentalStatus.ACTIVE || rental.getStatus() == Rental.RentalStatus.OVERDUE) {
            Button extendBtn = new Button("Extend Rental");
            extendBtn.getStyleClass().add("btn-secondary");
            extendBtn.setOnAction(e -> onExtendRental(rental));

            Button finishBtn = new Button("End Rental From User Side");
            finishBtn.getStyleClass().add(rental.getStatus() == Rental.RentalStatus.OVERDUE ? "btn-danger" : "btn-accent");
            finishBtn.setOnAction(e -> {
                boolean success = rentalService.markRentalReturnedByRenter(rental.getId());
                if (success) {
                    String message = rental.isSupplierMarkedComplete()
                        ? "Rental closed successfully."
                        : "Your return was recorded. Supplier confirmation is still pending.";
                    AlertUtil.showSuccess(message);
                    loadMarketplace();
                    loadMyRentals();
                }
            });
            card.getChildren().addAll(extendBtn, finishBtn);
        }

        return card;
    }

    private void selectVehicle(Vehicle vehicle) {
        this.selectedVehicle = vehicle;
        selectedVehicleLabel.setText(vehicle.getDisplayName());
        selectedSupplierLabel.setText(vehicle.getOwnerId() != null ? nvl(vehicle.getBranchLocation(), "Approved supplier depot") : "Direct listing");
        selectedRateLabel.setText(ValidationUtil.formatCurrency(vehicle.getDailyRate()) + "/day");
        requestInfoLabel.setText("Choose your rental period and submit a request.");
        requestInfoLabel.getStyleClass().remove("text-error");
    }

    @FXML
    private void onRequestRental() {
        if (selectedVehicle == null) {
            requestInfoLabel.setText("⚠  Please select a vehicle from the marketplace first.");
            requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
            return;
        }
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml"); return;
        }
        if (SessionManager.getInstance().getCurrentRole() == User.Role.SUPPLIER) {
            requestInfoLabel.setText("⚠  Supplier accounts cannot request rentals.");
            requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
            return;
        }
        if (SessionManager.getInstance().getCurrentUser() != null
            && !paymentMethodService.hasActivePaymentMethod(SessionManager.getInstance().getCurrentUser().getId())) {
            Session.pendingPaymentSetupSource = "RENTAL";
            Session.pendingPaymentSetupReturnPage = "/fxml/rent.fxml";
            requestInfoLabel.setText("Set up a payment profile before submitting a rental request.");
            requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
            NavigationManager.navigateTo("/fxml/payment_setup.fxml");
            return;
        }

        clearAll();
        requestInfoLabel.setStyle("");
        List<String> bad = new ArrayList<>();

        LocalDate start = requestStartDate.getValue();
        LocalDate end   = requestEndDate.getValue();
        if (start == null) bad.add("Start Date");
        if (end == null && !"HOURS".equals(requestUnitCombo.getValue())) bad.add("End Date");

        // Payment validation
        String method    = rentalPaymentMethodCombo.getValue();
        String reference = rentalPaymentReferenceField.getText() != null ? rentalPaymentReferenceField.getText().trim() : "";
        String holder    = rentalPaymentHolderField.getText() != null ? rentalPaymentHolderField.getText().trim() : "";
        if (preferredPaymentProfile == null && "Credit Card".equals(method)) {
            if (ValidationUtil.validateCardNumber(reference.replaceAll("\\s","")) != null)
                { markF(rentalPaymentReferenceField, rentalPayRefError, "Enter a valid 16-digit card number"); bad.add("Card Number"); }
            if (!ValidationUtil.isNotEmpty(holder))
                { markF(rentalPaymentHolderField, rentalPayHolderError, "Cardholder name is required"); bad.add("Cardholder Name"); }
        } else if (preferredPaymentProfile == null && "UPI".equals(method)) {
            if (ValidationUtil.validateUpiId(reference) != null)
                { markF(rentalPaymentReferenceField, rentalPayRefError, "Enter a valid UPI ID (e.g. name@sbi)"); bad.add("UPI ID"); }
            if (!ValidationUtil.isNotEmpty(holder))
                { markF(rentalPaymentHolderField, rentalPayHolderError, "Account holder name is required"); bad.add("Account Holder"); }
        }

        if ("HOURS".equals(requestUnitCombo.getValue())) {
            try {
                int hours = Integer.parseInt(requestHoursField.getText().trim());
                if (hours <= 0) { markF(requestHoursField, null, "Rental hours must be greater than zero"); bad.add("Rental Hours"); }
            } catch (Exception e) { markF(requestHoursField, null, "Enter valid rental hours"); bad.add("Rental Hours"); }
        }

        if (!bad.isEmpty()) {
            requestInfoLabel.setText("⚠  Fix the highlighted fields to continue.");
            requestInfoLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
            return;
        }

        try {
            Date startDate = DateTimeUtil.toDate(start, requestStartTimeCombo.getValue());
            Date endDate;
            if ("HOURS".equals(requestUnitCombo.getValue())) {
                int hours = Integer.parseInt(requestHoursField.getText().trim());
                endDate = new Date(startDate.getTime() + (hours * 60L * 60L * 1000L));
            } else {
                endDate = DateTimeUtil.toDate(end, requestEndTimeCombo.getValue());
                if (!endDate.after(startDate)) {
                    requestInfoLabel.setText("⚠  Drop date/time must be after pickup date/time.");
                    requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
                    return;
                }
            }
            Rental rental = rentalService.requestRental(
                selectedVehicle.getId(),
                SessionManager.getInstance().getCurrentUser().getId(),
                SessionManager.getInstance().getCurrentUserName(),
                startDate, endDate
            );
            Payment payment = preferredPaymentProfile != null
                ? paymentService.createSavedRentalPayment(
                    rental.getId(),
                    SessionManager.getInstance().getCurrentUser().getId(),
                    rental.getTotalAmount(),
                    preferredPaymentProfile
                )
                : paymentService.createRentalPayment(
                    rental.getId(),
                    SessionManager.getInstance().getCurrentUser().getId(),
                    rental.getTotalAmount(),
                    rentalMethodFromSelection(),
                    rentalPaymentReferenceField.getText(),
                    rentalPaymentHolderField.getText().isBlank()
                        ? SessionManager.getInstance().getCurrentUserName()
                        : rentalPaymentHolderField.getText().trim()
                );
            if (payment == null) {
                requestInfoLabel.setText("Rental request created, but payment validation failed. Please retry.");
                requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
                return;
            }
            rentalService.attachPaymentToRental(rental.getId(), payment);
            AlertUtil.showSuccess("Rental request sent to supplier for approval.");
            requestInfoLabel.setText("✓ Request submitted for " + rental.getVehicleName() + ".");
            requestInfoLabel.setStyle("-fx-text-fill: #06d6a0;");
            loadMyRentals();
            loadMarketplace();
        } catch (Exception e) {
            requestInfoLabel.setText("⚠  " + e.getMessage());
            requestInfoLabel.setStyle("-fx-text-fill: #f72585;");
        }
    }

    private void clearForm() {
        editingVehicle = null;
        makeField.clear();
        modelField.clear();
        yearField.clear();
        plateField.clear();
        seatsField.clear();
        priceField.clear();
        depotField.clear();
        descField.clear();
        if (supplierActionLabel != null) {
            supplierActionLabel.setText("Publish a brand new supplier vehicle");
        }
    }

    @FXML private void onRefreshMarketplace() { loadMarketplace(); loadMyRentals(); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onNavBook() { NavigationManager.navigateTo("/fxml/booking.fxml"); }
    @FXML private void onNavContact() { NavigationManager.navigateTo("/fxml/contact.fxml"); }
    @FXML private void onNavProfile() {
        if (SessionManager.getInstance().isGuest()) NavigationManager.navigateTo("/fxml/login.fxml");
        else NavigationManager.navigateTo("/fxml/profile.fxml");
    }

    // -----------------------------------------------------------------------
    // Error display
    // -----------------------------------------------------------------------

    private void markF(javafx.scene.control.Control field, Label lbl, String msg) {
        if (field != null) {
            field.setStyle(STYLE_ERROR);
            Tooltip tip = new Tooltip(msg);
            tip.setStyle("-fx-font-size: 12px;");
            Tooltip.install(field, tip);
        }
        if (lbl != null) {
            lbl.setText(msg);
            lbl.setStyle("-fx-text-fill: #f72585; -fx-font-size: 11px;");
            lbl.setVisible(true);
            lbl.setManaged(true);
        }
    }

    private void showBanner(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearAll() {
        // Banner
        if (errorLabel != null) { errorLabel.setText(""); errorLabel.setVisible(false); errorLabel.setManaged(false); }
        // Supplier fields
        clearH(makeField);  clearFE(makeError);
        clearH(modelField); clearFE(modelError);
        clearH(yearField);  clearFE(yearError);
        clearH(plateField); clearFE(plateError);
        clearH(seatsField); clearFE(seatsError);
        clearH(priceField); clearFE(priceError);
        // Rental fields
        clearH(rentalPaymentReferenceField); clearFE(rentalPayRefError);
        clearH(rentalPaymentHolderField);    clearFE(rentalPayHolderError);
        if (requestHoursField != null) clearH(requestHoursField);
    }

    private void clearH(javafx.scene.control.Control c) { c.setStyle(STYLE_CLEAR); Tooltip.install(c, null); }
    private void clearFE(Label lbl) { if (lbl == null) return; lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false); }

    private void attachClear(TextField field, Label lbl) {
        field.textProperty().addListener((obs, o, n) -> { if (!n.equals(o)) { clearH(field); clearFE(lbl); } });
    }

    private void beginEdit(Vehicle vehicle) {
        editingVehicle = vehicle;
        makeField.setText(vehicle.getMake());
        modelField.setText(vehicle.getModel());
        yearField.setText(String.valueOf(vehicle.getYear()));
        plateField.setText(vehicle.getLicensePlate());
        if (vehicle.getCategory() != null) {
            categoryCombo.setValue(vehicle.getCategory().name());
        }
        if (vehicle.getFuelType() != null) {
            fuelCombo.setValue(vehicle.getFuelType().name());
        }
        seatsField.setText(String.valueOf(vehicle.getSeats()));
        priceField.setText(String.valueOf((int) vehicle.getDailyRate()));
        depotField.setText(vehicle.getBranchLocation() != null ? vehicle.getBranchLocation() : "");
        descField.setText(vehicle.getDescription() != null ? vehicle.getDescription() : "");
        if (supplierActionLabel != null) {
            supplierActionLabel.setText("Editing existing vehicle. Save to request admin approval.");
        }
    }

    private void updateRentalUnitState() {
        boolean hourly = "HOURS".equals(requestUnitCombo.getValue());
        requestHoursField.setDisable(!hourly);
        requestEndDate.setDisable(hourly);
        requestEndTimeCombo.setDisable(hourly);
        if (!hourly) {
            requestHoursField.clear();
        }
    }

    // validateRentalPaymentInputs() is now inlined in onRequestRental() above

    private void updateRentalPaymentFields() {
        if (preferredPaymentProfile != null) {
            rentalPaymentMethodCombo.setValue(labelFromProfile(preferredPaymentProfile));
            rentalPaymentMethodCombo.setDisable(true);
            rentalPaymentReferenceField.setDisable(true);
            rentalPaymentHolderField.setDisable(true);
            rentalPaymentReferenceField.setText(preferredPaymentProfile.getMaskedReference());
            rentalPaymentHolderField.setText(preferredPaymentProfile.getHolderName());
            requestInfoLabel.setText("Using saved payment profile: " + preferredPaymentProfile.getProfileName());
            requestInfoLabel.setStyle("-fx-text-fill: #06d6a0;");
            return;
        }

        rentalPaymentMethodCombo.setDisable(false);
        String method = rentalPaymentMethodCombo.getValue();
        boolean cash = "Cash on Delivery".equals(method);
        rentalPaymentReferenceField.setDisable(cash);
        rentalPaymentHolderField.setDisable(cash);
        if (cash) {
            rentalPaymentReferenceField.setText("Cash on delivery");
            rentalPaymentHolderField.clear();
            rentalPaymentReferenceField.setPromptText("Cash will be collected by supplier");
            rentalPaymentHolderField.setPromptText("Verified during handover");
        } else if ("UPI".equals(method)) {
            rentalPaymentReferenceField.clear();
            rentalPaymentHolderField.clear();
            rentalPaymentReferenceField.setPromptText("name@bank");
            rentalPaymentHolderField.setPromptText("UPI holder name");
        } else {
            rentalPaymentReferenceField.clear();
            rentalPaymentHolderField.clear();
            rentalPaymentReferenceField.setPromptText("1234 5678 9012 3456");
            rentalPaymentHolderField.setPromptText("Card holder name");
        }
    }

    private Payment.PaymentMethod rentalMethodFromSelection() {
        return switch (rentalPaymentMethodCombo.getValue()) {
            case "UPI" -> Payment.PaymentMethod.UPI;
            case "Cash on Delivery" -> Payment.PaymentMethod.CASH_ON_DELIVERY;
            default -> Payment.PaymentMethod.CREDIT_CARD;
        };
    }

    private void loadPreferredPaymentProfile() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }
        preferredPaymentProfile = paymentMethodService.getPreferredPaymentMethod(
            SessionManager.getInstance().getCurrentUser().getId()
        );
        updateRentalPaymentFields();
    }

    private String labelFromProfile(PaymentMethodProfile profile) {
        return switch (profile.getMethodType()) {
            case UPI -> "UPI";
            case CASH_ON_DELIVERY -> "Cash on Delivery";
            default -> "Credit Card";
        };
    }

    private void onExtendRental(Rental rental) {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            return;
        }
        if (!AlertUtil.showConfirmation("Extend Rental",
            "Extend this rental by 1 extra day and process additional payment?")) {
            return;
        }
        Date newEndDate = new Date(rental.getEndDate().getTime() + (24L * 60L * 60L * 1000L));
        Payment payment = preferredPaymentProfile != null
            ? paymentService.createSavedRentalPayment(rental.getId(), SessionManager.getInstance().getCurrentUser().getId(), rental.getPricePerDay(), preferredPaymentProfile)
            : paymentService.createRentalPayment(
                rental.getId(),
                SessionManager.getInstance().getCurrentUser().getId(),
                rental.getPricePerDay(),
                rentalMethodFromSelection(),
                rentalPaymentReferenceField.getText(),
                rentalPaymentHolderField.getText().isBlank()
                    ? SessionManager.getInstance().getCurrentUserName()
                    : rentalPaymentHolderField.getText().trim()
            );
        if (payment == null) {
            AlertUtil.showError("Extension Failed", "Additional payment could not be created.");
            return;
        }
        boolean ok = rentalService.extendRental(rental.getId(), newEndDate, payment);
        if (ok) {
            AlertUtil.showSuccess("Rental extended. Supplier has been notified and the payment was recorded.");
            loadMyRentals();
        } else {
            AlertUtil.showError("Extension Failed", "Rental could not be extended.");
        }
    }

    private String nvl(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
