package com.rento.controllers;

import com.rento.dao.VehicleDAO;
import com.rento.models.Booking;
import com.rento.models.User;
import com.rento.models.Vehicle;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the vehicle booking page.
 */
public class BookingController implements Initializable {

    @FXML private FlowPane vehicleGrid;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> fuelFilter;
    @FXML private Slider priceSlider;
    @FXML private Label priceLabel;
    @FXML private Button profileBtn;
    @FXML private VBox emptyState;
    @FXML private VBox currentRideCard;
    @FXML private Label currentRideStatusLabel;
    @FXML private Label currentRideRouteLabel;
    @FXML private Label currentRideMetaLabel;

    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final BookingService bookingService = new BookingService();
    private List<Vehicle> allVehicles;
    private Booking currentRide;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (isRestrictedRole()) {
            NavigationManager.navigateTo(resolveRoleDashboard());
            return;
        }
        updateProfileButton();

        // Setup filters
        categoryFilter.setItems(FXCollections.observableArrayList(
            "All Categories", "SEDAN", "SUV", "HATCHBACK", "COUPE", "TRUCK", "VAN", "BIKE", "SCOOTER", "BUS"));
        categoryFilter.setValue("All Categories");

        fuelFilter.setItems(FXCollections.observableArrayList(
            "All Fuel Types", "PETROL", "DIESEL", "ELECTRIC", "HYBRID", "CNG"));
        fuelFilter.setValue("All Fuel Types");

        // Price slider listener
        priceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            priceLabel.setText("Max: ₹" + newVal.intValue() + " base fare");
            filterVehicles();
        });

        categoryFilter.setOnAction(e -> filterVehicles());
        fuelFilter.setOnAction(e -> filterVehicles());

        loadVehicles();
        loadCurrentRide();
    }

    private void updateProfileButton() {
        if (SessionManager.getInstance().isLoggedIn()) {
            profileBtn.setText("⬤ " + SessionManager.getInstance().getCurrentUserName());
        } else {
            profileBtn.setText("⬤ Sign In");
        }
    }

    private void loadVehicles() {
        allVehicles = vehicleDAO.findAvailable();
        filterVehicles();
    }

    private void loadCurrentRide() {
        if (!SessionManager.getInstance().isLoggedIn() || SessionManager.getInstance().getCurrentUser() == null) {
            currentRideCard.setVisible(false);
            currentRideCard.setManaged(false);
            currentRide = null;
            return;
        }
        currentRide = bookingService.getCurrentBookingForUser(SessionManager.getInstance().getCurrentUser().getId());
        boolean hasRide = currentRide != null;
        currentRideCard.setVisible(hasRide);
        currentRideCard.setManaged(hasRide);
        if (!hasRide) return;
        currentRideStatusLabel.setText(currentRide.getStatusString());
        currentRideRouteLabel.setText(
            nvl(currentRide.getPickupAddress(), currentRide.getPickupLocation()) + " → "
                + nvl(currentRide.getDropAddress(), currentRide.getDropoffLocation())
        );
        currentRideMetaLabel.setText(
            nvl(currentRide.getVehicleName(), "Vehicle") + " • "
                + (currentRide.getPickupDateTime() != null ? DateTimeUtil.formatDateTime(currentRide.getPickupDateTime()) : "Pickup time pending")
                + " • " + ValidationUtil.formatCurrency(currentRide.getFinalFare())
        );
    }

    private void filterVehicles() {
        String category = categoryFilter.getValue();
        String fuel = fuelFilter.getValue();
        double maxPrice = priceSlider.getValue();

        List<Vehicle> filtered = allVehicles.stream()
            .filter(v -> v.getOwnerId() == null)
            .filter(v -> v.getApprovalStatus() == null || v.getApprovalStatus() == Vehicle.ApprovalStatus.APPROVED)
            .filter(v -> ("All Categories".equals(category) || (v.getCategory() != null && v.getCategory().name().equals(category))))
            .filter(v -> ("All Fuel Types".equals(fuel) || (v.getFuelType() != null && v.getFuelType().name().equals(fuel))))
            .filter(v -> v.getDailyRate() <= maxPrice)
            .collect(Collectors.toList());

        vehicleGrid.getChildren().clear();

        if (filtered.isEmpty()) {
            emptyState.setVisible(true);
        } else {
            emptyState.setVisible(false);
            for (Vehicle v : filtered) {
                vehicleGrid.getChildren().add(createVehicleCard(v));
            }
        }
    }

    private VBox createVehicleCard(Vehicle vehicle) {
        VBox card = new VBox(0);
        card.getStyleClass().add("vehicle-card");
        card.setPrefWidth(280);
        card.setMaxWidth(280);

        // Image placeholder with gradient
        StackPane imagePane = new StackPane();
        imagePane.setMinHeight(180);
        imagePane.setPrefHeight(180);

        String[] gradients = {
            "linear-gradient(to bottom right, #2d1b69, #1a1040)",
            "linear-gradient(to bottom right, #1b3a4b, #0d2137)",
            "linear-gradient(to bottom right, #3b1d4a, #1a0d2e)",
            "linear-gradient(to bottom right, #1b4b3a, #0d3721)"
        };
        String gradient = gradients[(int)(Math.random() * gradients.length)];
        imagePane.setStyle("-fx-background-color: " + gradient + "; -fx-background-radius: 16 16 0 0;");

        Label carEmoji = new Label(getVehicleEmoji(vehicle.getCategory()));
        carEmoji.setStyle("-fx-font-size: 64px;");
        imagePane.getChildren().add(carEmoji);

        // Status badge
        Label statusBadge = new Label("● Available");
        statusBadge.getStyleClass().addAll("badge", "badge-success");
        StackPane.setAlignment(statusBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(statusBadge, new Insets(12, 12, 0, 0));
        imagePane.getChildren().add(statusBadge);

        // Body
        VBox body = new VBox(8);
        body.getStyleClass().add("vehicle-card-body");

        Label name = new Label(vehicle.getDisplayName());
        name.getStyleClass().add("vehicle-name");

        HBox tags = new HBox(8);
        Label catTag = new Label(vehicle.getCategory() != null ? vehicle.getCategory().name() : "N/A");
        catTag.getStyleClass().add("vehicle-tag");
        Label fuelTag = new Label(vehicle.getFuelType() != null ? vehicle.getFuelType().name() : "N/A");
        fuelTag.getStyleClass().add("vehicle-tag");
        Label seatTag = new Label(vehicle.getSeats() + " seats");
        seatTag.getStyleClass().add("vehicle-tag");
        tags.getChildren().addAll(catTag, fuelTag, seatTag);

        HBox priceRow = new HBox(8);
        priceRow.setAlignment(Pos.CENTER_LEFT);
        Label price = new Label("₹" + String.format("%.0f", vehicle.getDailyRate()));
        price.getStyleClass().add("vehicle-price");
        Label perDay = new Label("/day");
        perDay.setText("/base fare");
        perDay.getStyleClass().add("text-muted");
        priceRow.getChildren().addAll(price, perDay);

        Button bookBtn = new Button("Book Now →");
        bookBtn.getStyleClass().add("btn-primary");
        bookBtn.setMaxWidth(Double.MAX_VALUE);
        bookBtn.setOnAction(e -> onBookVehicle(vehicle));

        body.getChildren().addAll(name, tags, priceRow, bookBtn);
        card.getChildren().addAll(imagePane, body);

        return card;
    }

    private String getVehicleEmoji(Vehicle.Category category) {
        if (category == null) return "🚗";
        switch (category) {
            case SUV: return "🚙";
            case BIKE: return "🏍";
            case SCOOTER: return "🛵";
            case TRUCK: return "🚛";
            case BUS: return "🚌";
            case VAN: return "🚐";
            default: return "🚗";
        }
    }

    private void onBookVehicle(Vehicle vehicle) {
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml");
            return;
        }

        NavigationManager.navigateTo("/fxml/booking_detail.fxml", controller -> {
            if (controller instanceof BookingDetailController) {
                ((BookingDetailController) controller).setVehicle(vehicle);
            }
        });
    }

    @FXML private void onRefresh() { loadVehicles(); loadCurrentRide(); }
    @FXML private void onOpenCurrentRide() {
        if (currentRide == null) return;
        Session.activeBookingId = currentRide.getId() != null ? currentRide.getId().toHexString() : null;
        Session.pendingPickupAddress = nvl(currentRide.getPickupAddress(), currentRide.getPickupLocation());
        Session.pendingDropAddress = nvl(currentRide.getDropAddress(), currentRide.getDropoffLocation());
        Session.pendingVehicleCategory = currentRide.getVehicleCategory();
        Session.pendingFareEstimate = currentRide.getFinalFare();
        if (currentRide.getPickupLat() != 0 || currentRide.getPickupLng() != 0) {
            Session.pendingPickupX = currentRide.getPickupLat();
            Session.pendingPickupY = currentRide.getPickupLng();
        }
        if (currentRide.getDropLat() != 0 || currentRide.getDropLng() != 0) {
            Session.pendingDropX = currentRide.getDropLat();
            Session.pendingDropY = currentRide.getDropLng();
        }
        Session.activeDriverId = currentRide.getDriverId() != null ? currentRide.getDriverId().toHexString() : null;
        if (currentRide.getStatus() == Booking.BookingStatus.IN_PROGRESS) {
            NavigationManager.navigateTo("/fxml/active_ride.fxml");
        } else {
            NavigationManager.navigateTo("/fxml/booking_confirmed.fxml");
        }
    }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onNavAbout() { NavigationManager.navigateTo("/fxml/about.fxml"); }
    @FXML private void onNavRent() { NavigationManager.navigateTo("/fxml/rent.fxml"); }
    @FXML private void onNavContact() { NavigationManager.navigateTo("/fxml/contact.fxml"); }
    @FXML private void onNavProfile() {
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml");
        } else {
            NavigationManager.navigateTo("/fxml/profile.fxml");
        }
    }

    private boolean isRestrictedRole() {
        User.Role role = SessionManager.getInstance().getCurrentRole();
        return role == User.Role.ADMIN || role == User.Role.DRIVER || role == User.Role.SUPPLIER;
    }

    private String resolveRoleDashboard() {
        return switch (SessionManager.getInstance().getCurrentRole()) {
            case ADMIN -> "/fxml/admin_dashboard.fxml";
            case DRIVER -> "/fxml/driver_dashboard.fxml";
            case SUPPLIER -> "/fxml/supplier_dashboard.fxml";
            default -> "/fxml/landing.fxml";
        };
    }

    private String nvl(String value, String fallback) {
        return value != null && !value.isBlank() ? value : (fallback != null ? fallback : "-");
    }
}
