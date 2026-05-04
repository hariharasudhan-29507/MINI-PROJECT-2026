package com.rento.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rento.models.Booking;
import com.rento.models.Driver;
import com.rento.models.Vehicle;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.services.OfflineMapService;
import com.rento.services.PaymentMethodService;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.MapPoint;
import com.rento.utils.MongoDBConnection;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.bson.types.ObjectId;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BookingDetailController implements Initializable {

    @FXML private Label vehicleName;
    @FXML private Label vehicleCategory;
    @FXML private Label vehicleFuel;
    @FXML private Label vehicleSeats;
    @FXML private WebView mapWebView;
    @FXML private Button selectPickupBtn;
    @FXML private Button selectDropBtn;
    @FXML private javafx.scene.control.TextField pickupField;
    @FXML private javafx.scene.control.TextField dropoffField;
    @FXML private ListView<String> pickupSuggestions;
    @FXML private ListView<String> dropSuggestions;
    @FXML private ComboBox<String> driverCombo;
    @FXML private DatePicker pickupDate;
    @FXML private DatePicker returnDate;
    @FXML private ComboBox<String> pickupHourCombo;
    @FXML private ComboBox<String> pickupMinuteCombo;
    @FXML private ToggleButton pickupAmButton;
    @FXML private ToggleButton pickupPmButton;
    @FXML private ComboBox<String> returnHourCombo;
    @FXML private ComboBox<String> returnMinuteCombo;
    @FXML private ToggleButton returnAmButton;
    @FXML private ToggleButton returnPmButton;
    @FXML private Label pickupTimeLabel;
    @FXML private Label returnTimeLabel;
    @FXML private Label arrivalHintLabel;
    @FXML private Label mapSelectionModeLabel;
    @FXML private Label driverHelperLabel;
    @FXML private Label dailyRateLabel;
    @FXML private Label daysLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label surchargeLabel;
    @FXML private Label discountLabel;
    @FXML private Label taxLabel;
    @FXML private Label totalLabel;
    @FXML private Label depositLabel;
    @FXML private HBox surchargeRow;
    @FXML private HBox discountRow;
    @FXML private Label errorLabel;

    private Vehicle currentVehicle;
    private final BookingService bookingService = new BookingService();
    private final PaymentMethodService paymentMethodService = new PaymentMethodService();
    private double[] pricingResult;
    private List<Driver> availableDrivers = List.of();
    private SelectedLocation selectedPickup;
    private SelectedLocation selectedDrop;
    private double routeDistanceKm;
    private double routeDurationMinutes;
    private boolean suppressFieldSearch;
    private boolean adjustingReturnTime;
    private boolean adjustingTimeControls;
    private WebEngine webEngine;
    private final PauseTransition pickupSearchDelay = new PauseTransition(Duration.millis(450));
    private final PauseTransition dropSearchDelay = new PauseTransition(Duration.millis(450));
    private final List<LocationSuggestion> pickupSuggestionData = new ArrayList<>();
    private final List<LocationSuggestion> dropSuggestionData = new ArrayList<>();
    private final ToggleGroup pickupMeridiemGroup = new ToggleGroup();
    private final ToggleGroup returnMeridiemGroup = new ToggleGroup();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private int pickupSearchToken;
    private int dropSearchToken;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        pickupDate.setValue(LocalDate.now().plusDays(1));
        returnDate.setValue(LocalDate.now().plusDays(1));
        initializeTimePickers();

        pickupField.textProperty().addListener((obs, oldVal, newVal) -> scheduleSearch("pickup", newVal));
        dropoffField.textProperty().addListener((obs, oldVal, newVal) -> scheduleSearch("drop", newVal));
        pickupDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            syncReturnTimeFromRoute();
            onCalculatePrice();
        });
        returnDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!adjustingReturnTime) {
                onCalculatePrice();
            }
        });

        initializeMap();
        loadDrivers();
        updateArrivalHint();
    }

    public void setVehicle(Vehicle vehicle) {
        this.currentVehicle = vehicle;
        if (vehicle != null) {
            vehicleName.setText(vehicle.getDisplayName());
            vehicleCategory.setText(vehicle.getCategory() != null ? vehicle.getCategory().name() : "");
            vehicleFuel.setText(vehicle.getFuelType() != null ? vehicle.getFuelType().name() : "");
            vehicleSeats.setText(vehicle.getSeats() + " seats");
            dailyRateLabel.setText(ValidationUtil.formatCurrency(vehicle.getDailyRate()));
        }
        onCalculatePrice();
    }

    private void initializeTimePickers() {
        List<String> hours = new ArrayList<>();
        List<String> minutes = new ArrayList<>();
        for (int hour = 1; hour <= 12; hour++) {
            hours.add(String.format("%02d", hour));
        }
        for (int minute = 0; minute < 60; minute++) {
            minutes.add(String.format("%02d", minute));
        }

        pickupHourCombo.getItems().setAll(hours);
        returnHourCombo.getItems().setAll(hours);
        pickupMinuteCombo.getItems().setAll(minutes);
        returnMinuteCombo.getItems().setAll(minutes);

        pickupAmButton.setToggleGroup(pickupMeridiemGroup);
        pickupPmButton.setToggleGroup(pickupMeridiemGroup);
        returnAmButton.setToggleGroup(returnMeridiemGroup);
        returnPmButton.setToggleGroup(returnMeridiemGroup);

        setPickupTime(LocalTime.of(9, 0));
        setReturnTime(LocalTime.of(9, 30));

        pickupHourCombo.valueProperty().addListener((obs, oldVal, newVal) -> onPickupTimeChanged());
        pickupMinuteCombo.valueProperty().addListener((obs, oldVal, newVal) -> onPickupTimeChanged());
        pickupMeridiemGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> onPickupTimeChanged());

        returnDate.setDisable(true);
        returnHourCombo.setDisable(true);
        returnMinuteCombo.setDisable(true);
        returnAmButton.setDisable(true);
        returnPmButton.setDisable(true);
    }

    private void onPickupTimeChanged() {
        if (adjustingTimeControls) {
            return;
        }
        pickupTimeLabel.setText(getPickupTimeText());
        syncReturnTimeFromRoute();
        onCalculatePrice();
    }

    private void setPickupTime(LocalTime time) {
        adjustingTimeControls = true;
        pickupHourCombo.setValue(String.format("%02d", time.getHour() % 12 == 0 ? 12 : time.getHour() % 12));
        pickupMinuteCombo.setValue(String.format("%02d", time.getMinute()));
        pickupMeridiemGroup.selectToggle(time.getHour() < 12 ? pickupAmButton : pickupPmButton);
        pickupTimeLabel.setText(DateTimeUtil.formatTime(time));
        adjustingTimeControls = false;
    }

    private void setReturnTime(LocalTime time) {
        adjustingTimeControls = true;
        returnHourCombo.setValue(String.format("%02d", time.getHour() % 12 == 0 ? 12 : time.getHour() % 12));
        returnMinuteCombo.setValue(String.format("%02d", time.getMinute()));
        returnMeridiemGroup.selectToggle(time.getHour() < 12 ? returnAmButton : returnPmButton);
        returnTimeLabel.setText(DateTimeUtil.formatTime(time));
        adjustingTimeControls = false;
    }

    private LocalTime getPickupTime() {
        return readTimeFromControls(pickupHourCombo, pickupMinuteCombo, pickupPmButton);
    }

    private LocalTime getReturnTime() {
        return readTimeFromControls(returnHourCombo, returnMinuteCombo, returnPmButton);
    }

    private LocalTime readTimeFromControls(ComboBox<String> hourCombo, ComboBox<String> minuteCombo, ToggleButton pmButton) {
        int hour = Integer.parseInt(hourCombo.getValue() == null ? "09" : hourCombo.getValue());
        int minute = Integer.parseInt(minuteCombo.getValue() == null ? "00" : minuteCombo.getValue());
        int hour24 = hour % 12;
        if (pmButton.isSelected()) {
            hour24 += 12;
        }
        return LocalTime.of(hour24, minute);
    }

    private String getPickupTimeText() {
        return DateTimeUtil.formatTime(getPickupTime());
    }

    private String getReturnTimeText() {
        return DateTimeUtil.formatTime(getReturnTime());
    }

    private void loadDrivers() {
        String category = currentVehicle != null && currentVehicle.getCategory() != null ? currentVehicle.getCategory().name() : "";
        availableDrivers = selectedPickup != null
            ? bookingService.getAvailableDriversForPickup(selectedPickup.lat, selectedPickup.lng, category, currentVehicle != null ? currentVehicle.getDisplayName() : null)
            : List.of();
        driverCombo.getItems().clear();
        driverCombo.getItems().add("Nearest Available Driver");
        for (Driver driver : availableDrivers) {
            String zone = buildDriverServiceAddress(driver);
            String availability = driver.getAvailabilityStatus() != null
                ? driver.getAvailabilityStatus().name()
                : "OFFLINE";
            double distanceKm = distanceFromDriver(driver);
            String vehicleSkills = driver.getDrivesVehicleCategories() != null && !driver.getDrivesVehicleCategories().isEmpty()
                ? String.join("/", driver.getDrivesVehicleCategories())
                : "Any category";
            driverCombo.getItems().add(driver.getFullName() + " • " + availability + " • " + vehicleSkills + " • " + zone + " • " + String.format("%.1f km away", distanceKm));
        }
        driverCombo.setValue("Nearest Available Driver");
        driverHelperLabel.setText(availableDrivers.isEmpty()
            ? "No driver is online near this pickup right now. You can still continue and we will try to auto-assign the closest driver."
            : "Showing online drivers near " + pickupField.getText() + ".");
    }

    @FXML
    private void onCalculatePrice() {
        if (currentVehicle == null) {
            return;
        }
        resolveSelectionsFromFields();
        if (selectedPickup == null || selectedDrop == null) {
            showError("Select both pickup and drop places from the map or search fields");
            return;
        }

        LocalDate pickup = pickupDate.getValue();
        LocalDate ret = returnDate.getValue();
        if (pickup == null || ret == null) {
            showError("Please choose both pickup and drop date/time");
            return;
        }

        Date pickupD = DateTimeUtil.toDate(pickup, getPickupTimeText());
        Date returnD = DateTimeUtil.toDate(ret, getReturnTimeText());
        if (!returnD.after(pickupD)) {
            showError("Drop date/time must be after pickup date/time");
            return;
        }

        clearError();

        try {
            BookingService.BookingQuote quote = bookingService.calculateBookingQuote(
                currentVehicle,
                getCurrentRouteDistanceKm(),
                pickupD,
                returnD
            );
            pricingResult = new double[] {
                quote.durationHours,
                quote.timeCharge,
                quote.distanceKm,
                quote.surcharge,
                quote.tax,
                quote.total,
                quote.deposit,
                quote.distanceCharge
            };

            dailyRateLabel.setText(ValidationUtil.formatCurrency(quote.distanceCharge));
            daysLabel.setText(DateTimeUtil.formatDuration(pickupD, returnD));
            subtotalLabel.setText(ValidationUtil.formatCurrency(quote.timeCharge));
            surchargeRow.setVisible(true);
            surchargeRow.setManaged(true);
            surchargeLabel.setText(String.format("%.1f km", quote.distanceKm));
            if (quote.surcharge > 0) {
                discountRow.setVisible(true);
                discountRow.setManaged(true);
                discountLabel.setText("+" + ValidationUtil.formatCurrency(quote.surcharge));
            } else {
                discountRow.setVisible(false);
                discountRow.setManaged(false);
            }
            taxLabel.setText(ValidationUtil.formatCurrency(quote.tax));
            totalLabel.setText(ValidationUtil.formatCurrency(quote.total));
            depositLabel.setText(ValidationUtil.formatCurrency(quote.deposit));
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onProceedToPayment() {
        clearError();

        if (SessionManager.getInstance().getCurrentUser() == null) {
            NavigationManager.navigateTo("/fxml/login.fxml");
            return;
        }
        if (!paymentMethodService.hasActivePaymentMethod(SessionManager.getInstance().getCurrentUser().getId())) {
            Session.pendingPaymentSetupSource = "BOOKING";
            Session.pendingPaymentSetupReturnPage = "/fxml/booking.fxml";
            showError("Set up a payment profile before continuing to checkout.");
            NavigationManager.navigateTo("/fxml/payment_setup.fxml");
            return;
        }
        if (currentVehicle == null) {
            showError("Please select a vehicle before continuing.");
            return;
        }
        resolveSelectionsFromFields();
        if (selectedPickup == null) {
            showError("Please choose a pickup place from the map or search field");
            return;
        }
        if (selectedDrop == null) {
            showError("Please choose a drop place from the map or search field");
            return;
        }

        LocalDate pickup = pickupDate.getValue();
        LocalDate ret = returnDate.getValue();
        if (pickup == null || ret == null) {
            showError("Please select valid pickup and drop date/time");
            return;
        }

        try {
            Date pickupD = DateTimeUtil.toDate(pickup, getPickupTimeText());
            Date returnD = DateTimeUtil.toDate(ret, getReturnTimeText());
            if (!returnD.after(pickupD)) {
                showError("Drop date/time must be after pickup date/time");
                return;
            }

            Booking booking = bookingService.createBookingWithRouteDistance(
                SessionManager.getInstance().getCurrentUser().getId(),
                currentVehicle.getId(),
                getSelectedDriverId(),
                selectedPickup.label,
                selectedDrop.label,
                pickupD,
                returnD,
                getCurrentRouteDistanceKm(),
                selectedPickup.lat,
                selectedPickup.lng,
                selectedDrop.lat,
                selectedDrop.lng
            );

            NavigationManager.navigateTo("/fxml/payment.fxml", controller -> {
                if (controller instanceof PaymentController) {
                    ((PaymentController) controller).setBooking(booking);
                }
            });
        } catch (Exception e) {
            if (MongoDBConnection.getInstance().isConnected()) {
                showError(e.getMessage());
                return;
            }

            Booking mockBooking = new Booking();
            mockBooking.setPickupLocation(selectedPickup.label);
            mockBooking.setDropoffLocation(selectedDrop.label);
            mockBooking.setPickupLat(selectedPickup.lat);
            mockBooking.setPickupLng(selectedPickup.lng);
            mockBooking.setDropLat(selectedDrop.lat);
            mockBooking.setDropLng(selectedDrop.lng);
            mockBooking.setRouteDistanceKm(getCurrentRouteDistanceKm());
            mockBooking.setPickupDateTime(DateTimeUtil.toDate(pickup, getPickupTimeText()));
            mockBooking.setReturnDateTime(DateTimeUtil.toDate(ret, getReturnTimeText()));
            mockBooking.setVehicleName(currentVehicle.getDisplayName());
            mockBooking.setPreferredDriverId(getSelectedDriverId());
            if (getSelectedDriverId() != null && !availableDrivers.isEmpty()) {
                int selectedIndex = driverCombo.getSelectionModel().getSelectedIndex();
                if (selectedIndex > 0 && selectedIndex - 1 < availableDrivers.size()) {
                    mockBooking.setPreferredDriverName(availableDrivers.get(selectedIndex - 1).getFullName());
                }
            }
            if (pricingResult != null) {
                mockBooking.setTotalCost(pricingResult[5]);
                mockBooking.setTaxAmount(pricingResult[4]);
                mockBooking.setDepositAmount(pricingResult[6]);
                mockBooking.setDiscountApplied(0);
            }

            NavigationManager.navigateTo("/fxml/payment.fxml", controller -> {
                if (controller instanceof PaymentController) {
                    ((PaymentController) controller).setBooking(mockBooking);
                }
            });
        }
    }

    @FXML private void onBack() { NavigationManager.goBack(); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }

    @FXML
    private void onSelectPickupMode() {
        mapSelectionModeLabel.setText("Selecting: Pickup");
        setMapSelectionMode("pickup");
    }

    @FXML
    private void onSelectDropMode() {
        mapSelectionModeLabel.setText("Selecting: Drop");
        setMapSelectionMode("drop");
    }

    @FXML private void onPickupSearch() { searchMap("pickup", pickupField.getText()); }
    @FXML private void onDropSearch() { searchMap("drop", dropoffField.getText()); }
    @FXML private void onPickupSuggestionSelected() { selectSuggestion("pickup"); }
    @FXML private void onDropSuggestionSelected() { selectSuggestion("drop"); }
    @FXML private void onClearPickup() { clearLocation("pickup"); }
    @FXML private void onClearDrop() { clearLocation("drop"); }

    @FXML
    private void onUseCurrentPickupTime() {
        LocalDateTime now = LocalDateTime.now();
        pickupDate.setValue(now.toLocalDate());
        setPickupTime(now.toLocalTime());
        syncReturnTimeFromRoute();
        onCalculatePrice();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
    }

    private ObjectId getSelectedDriverId() {
        int selectedIndex = driverCombo.getSelectionModel().getSelectedIndex();
        if (selectedIndex <= 0 || selectedIndex - 1 >= availableDrivers.size()) {
            return null;
        }
        return availableDrivers.get(selectedIndex - 1).getLinkedUserId();
    }

    private void initializeMap() {
        webEngine = mapWebView.getEngine();
        URL mapUrl = getClass().getResource("/map/map.html");
        if (mapUrl != null) {
            webEngine.load(mapUrl.toExternalForm());
        }

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaBridge", new MapBridge());
                setMapSelectionMode("pickup");

                // WebView inside ScrollPane may not have final bounds at load time;
                // invalidate Leaflet size after layout settles
                Platform.runLater(() -> {
                    webEngine.executeScript("if(typeof map !== 'undefined') map.invalidateSize();");
                    PauseTransition pt = new PauseTransition(Duration.millis(500));
                    pt.setOnFinished(e -> webEngine.executeScript("if(typeof map !== 'undefined') map.invalidateSize();"));
                    pt.play();
                    PauseTransition pt2 = new PauseTransition(Duration.millis(1200));
                    pt2.setOnFinished(e -> webEngine.executeScript("if(typeof map !== 'undefined') map.invalidateSize();"));
                    pt2.play();
                });
            }
        });

        // Re-invalidate map whenever WebView bounds change (e.g., ScrollPane layout)
        mapWebView.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (webEngine != null && newBounds.getWidth() > 0 && newBounds.getHeight() > 0) {
                webEngine.executeScript("if(typeof map !== 'undefined') map.invalidateSize();");
            }
        });
    }

    private void onMapLocationSelected(String role, String label, double lat, double lng) {
        SelectedLocation location = new SelectedLocation(label, lat, lng);
        suppressFieldSearch = true;
        if ("drop".equalsIgnoreCase(role)) {
            selectedDrop = location;
            dropoffField.setText(label);
            hideSuggestions("drop");
            routeDistanceKm = 0;
            routeDurationMinutes = 0;
            mapSelectionModeLabel.setText("Selecting: Pickup");
            setMapSelectionMode("pickup");
        } else {
            selectedPickup = location;
            pickupField.setText(label);
            hideSuggestions("pickup");
            routeDistanceKm = 0;
            routeDurationMinutes = 0;
            loadDrivers();
            mapSelectionModeLabel.setText("Selecting: Drop");
            setMapSelectionMode("drop");
        }
        suppressFieldSearch = false;
        updateArrivalHint();
        onCalculatePrice();
    }

    private void searchMap(String role, String query) {
        if (query == null || query.isBlank()) {
            showError("Type a city, landmark, or address before searching.");
            return;
        }
        requestSuggestions(role, query, true);
    }

    private void scheduleSearch(String role, String query) {
        if (suppressFieldSearch) {
            return;
        }
        clearStaleSelection(role, query);
        PauseTransition delay = "drop".equals(role) ? dropSearchDelay : pickupSearchDelay;
        delay.setOnFinished(event -> searchMapSuggestions(role, query));
        delay.playFromStart();
    }

    private void clearStaleSelection(String role, String query) {
        String value = query == null ? "" : query.trim();
        if ("drop".equals(role) && selectedDrop != null && !labelsCompatible(selectedDrop.label, value)) {
            selectedDrop = null;
            routeDistanceKm = 0;
            routeDurationMinutes = 0;
            updateArrivalHint();
            if (webEngine != null) {
                webEngine.executeScript("clearLocation('drop');");
            }
        } else if (!"drop".equals(role) && selectedPickup != null && !labelsCompatible(selectedPickup.label, value)) {
            selectedPickup = null;
            routeDistanceKm = 0;
            routeDurationMinutes = 0;
            loadDrivers();
            updateArrivalHint();
            if (webEngine != null) {
                webEngine.executeScript("clearLocation('pickup');");
            }
        }
    }

    private void searchMapSuggestions(String role, String query) {
        if (query == null || query.trim().length() < 3) {
            hideSuggestions(role);
            return;
        }
        requestSuggestions(role, query, false);
    }

    private void requestSuggestions(String role, String query, boolean autoSelectFirst) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 3) {
            hideSuggestions(role);
            return;
        }

        int requestToken = "drop".equals(role) ? ++dropSearchToken : ++pickupSearchToken;
        CompletableFuture
            .supplyAsync(() -> fetchLocationSuggestions(normalized))
            .thenAccept(results -> Platform.runLater(() -> {
                int activeToken = "drop".equals(role) ? dropSearchToken : pickupSearchToken;
                if (requestToken != activeToken) {
                    return;
                }
                renderSuggestions(role, results);
                showSuggestionsOnMap(role, results);
                if (autoSelectFirst && !results.isEmpty()) {
                    applySuggestion(role, results.get(0));
                }
            }))
            .exceptionally(error -> {
                Platform.runLater(() -> showError("Location search is not available right now. Please try another nearby landmark."));
                return null;
            });
    }

    private List<LocationSuggestion> fetchLocationSuggestions(String query) {
        List<LocationSuggestion> remoteResults = fetchRemoteLocationSuggestions(query);
        if (!remoteResults.isEmpty()) {
            return remoteResults;
        }
        return fetchOfflineLocationSuggestions(query);
    }

    private List<LocationSuggestion> fetchRemoteLocationSuggestions(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=6&countrycodes=in&q=" + encodedQuery))
                .header("Accept", "application/json")
                .header("User-Agent", "RentoDesktop/1.0")
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();
            List<LocationSuggestion> suggestions = new ArrayList<>();
            Set<String> seenLabels = new LinkedHashSet<>();
            for (int i = 0; i < results.size(); i++) {
                JsonObject result = results.get(i).getAsJsonObject();
                if (!isIndiaResult(result)) {
                    continue;
                }
                String label = result.has("display_name") ? result.get("display_name").getAsString() : "Selected place";
                if (!seenLabels.add(label)) {
                    continue;
                }
                double lat = result.has("lat") ? result.get("lat").getAsDouble() : 0;
                double lng = result.has("lon") ? result.get("lon").getAsDouble() : 0;
                suggestions.add(new LocationSuggestion(label, lat, lng));
            }
            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<LocationSuggestion> fetchOfflineLocationSuggestions(String query) {
        String normalized = query.toLowerCase();
        List<LocationSuggestion> matches = new ArrayList<>();
        OfflineMapService.CITY_POINTS.stream()
            .filter(point -> point.getLabel().toLowerCase().contains(normalized)
                || point.getShortCode().toLowerCase().contains(normalized))
            .limit(6)
            .forEach(point -> matches.add(new LocationSuggestion(
                point.getLabel(),
                OfflineMapService.canvasToLat(point.getY()),
                OfflineMapService.canvasToLng(point.getX())
            )));
        return matches;
    }

    private boolean isIndiaResult(JsonObject result) {
        if (result == null || !result.has("address")) {
            return false;
        }
        JsonObject address = result.getAsJsonObject("address");
        String countryCode = address.has("country_code") ? address.get("country_code").getAsString() : "";
        String country = address.has("country") ? address.get("country").getAsString() : "";
        return "in".equalsIgnoreCase(countryCode) || "india".equalsIgnoreCase(country);
    }

    private void selectSuggestion(String role) {
        ListView<String> listView = "drop".equals(role) ? dropSuggestions : pickupSuggestions;
        int index = listView.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        List<LocationSuggestion> source = "drop".equals(role) ? dropSuggestionData : pickupSuggestionData;
        if (index >= source.size()) {
            return;
        }
        applySuggestion(role, source.get(index));
    }

    private void applySuggestion(String role, LocationSuggestion suggestion) {
        hideSuggestions(role);
        clearError();
        suppressFieldSearch = true;
        if ("drop".equals(role)) {
            dropoffField.setText(suggestion.label);
        } else {
            pickupField.setText(suggestion.label);
        }
        suppressFieldSearch = false;
        if (webEngine != null) {
            setMapSelectionMode(role);
            webEngine.executeScript("setLocation('" + role + "', " + jsString(suggestion.label) + ", " + suggestion.lat + ", " + suggestion.lng + ");");
        } else {
            onMapLocationSelected(role, suggestion.label, suggestion.lat, suggestion.lng);
        }
    }

    private void clearLocation(String role) {
        if ("drop".equals(role)) {
            selectedDrop = null;
            dropoffField.clear();
            hideSuggestions("drop");
        } else {
            selectedPickup = null;
            pickupField.clear();
            availableDrivers = List.of();
            loadDrivers();
            hideSuggestions("pickup");
        }
        routeDistanceKm = 0;
        routeDurationMinutes = 0;
        updateArrivalHint();
        clearError();
        if (webEngine != null) {
            webEngine.executeScript("clearLocation('" + role + "');");
        }
        onCalculatePrice();
    }

    private void onSearchResults(String role, String json) {
        renderSuggestions(role, parseSuggestions(json));
    }

    private List<LocationSuggestion> parseSuggestions(String json) {
        List<LocationSuggestion> suggestions = new ArrayList<>();
        JsonArray results = JsonParser.parseString(json).getAsJsonArray();
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            String label = result.has("label") ? result.get("label").getAsString() : "Selected place";
            double lat = result.has("lat") ? result.get("lat").getAsDouble() : 0;
            double lng = result.has("lng") ? result.get("lng").getAsDouble() : 0;
            suggestions.add(new LocationSuggestion(label, lat, lng));
        }
        return suggestions;
    }

    private void renderSuggestions(String role, List<LocationSuggestion> suggestions) {
        List<LocationSuggestion> targetData = "drop".equals(role) ? dropSuggestionData : pickupSuggestionData;
        ListView<String> targetList = "drop".equals(role) ? dropSuggestions : pickupSuggestions;
        targetData.clear();
        targetList.getItems().clear();

        for (LocationSuggestion suggestion : suggestions) {
            targetData.add(suggestion);
            targetList.getItems().add(suggestion.label);
        }

        boolean hasResults = !targetData.isEmpty();
        targetList.setVisible(hasResults);
        targetList.setManaged(hasResults);
        if (!hasResults) {
            showError("No matching places found in India. Try a clearer landmark or address.");
        } else {
            clearError();
        }
    }

    private void hideSuggestions(String role) {
        ListView<String> listView = "drop".equals(role) ? dropSuggestions : pickupSuggestions;
        listView.setVisible(false);
        listView.setManaged(false);
    }

    private void showSuggestionsOnMap(String role, List<LocationSuggestion> suggestions) {
        if (webEngine == null) {
            return;
        }
        JsonArray payload = new JsonArray();
        for (LocationSuggestion suggestion : suggestions) {
            JsonObject result = new JsonObject();
            result.addProperty("label", suggestion.label);
            result.addProperty("lat", suggestion.lat);
            result.addProperty("lng", suggestion.lng);
            payload.add(result);
        }
        webEngine.executeScript("showSearchResults('" + role + "', " + jsString(payload.toString()) + ");");
    }

    private void setMapSelectionMode(String role) {
        if (webEngine != null) {
            try {
                webEngine.executeScript("setSelectionMode('" + role + "');");
            } catch (Exception ignored) {
            }
        }
    }

    private double getCurrentRouteDistanceKm() {
        if (routeDistanceKm > 0) {
            return routeDistanceKm;
        }
        if (selectedPickup == null || selectedDrop == null) {
            return 0;
        }
        return OfflineMapService.distanceKmLatLng(selectedPickup.lat, selectedPickup.lng, selectedDrop.lat, selectedDrop.lng);
    }

    private void syncReturnTimeFromRoute() {
        if (pickupDate.getValue() == null) {
            return;
        }
        int tripMinutes = routeDurationMinutes > 0 ? Math.max(15, (int) Math.ceil(routeDurationMinutes + 10)) : 30;
        LocalDateTime pickupDateTime = LocalDateTime.of(pickupDate.getValue(), getPickupTime());
        LocalDateTime returnDateTime = pickupDateTime.plusMinutes(tripMinutes);

        adjustingReturnTime = true;
        returnDate.setValue(returnDateTime.toLocalDate());
        setReturnTime(returnDateTime.toLocalTime());
        adjustingReturnTime = false;
        updateArrivalHint();
    }

    private void updateArrivalHint() {
        if (selectedPickup == null || selectedDrop == null || routeDurationMinutes <= 0) {
            arrivalHintLabel.setText("Drop date and time are locked and will auto-update after you choose both points on the map.");
            return;
        }
        int etaMinutes = Math.max(15, (int) Math.ceil(routeDurationMinutes + 10));
        arrivalHintLabel.setText("Auto arrival uses live route time plus a 10 minute buffer. Current ETA: " + etaMinutes + " mins.");
    }

    private String buildDriverServiceAddress(Driver driver) {
        if (driver == null) {
            return "Unassigned zone";
        }
        List<String> parts = new ArrayList<>();
        if (driver.getWorkAddressLine1() != null && !driver.getWorkAddressLine1().isBlank()) {
            parts.add(driver.getWorkAddressLine1().trim());
        }
        if (driver.getWorkStreet() != null && !driver.getWorkStreet().isBlank()) {
            parts.add(driver.getWorkStreet().trim());
        }
        if (driver.getWorkLandmark() != null && !driver.getWorkLandmark().isBlank()) {
            parts.add("Landmark: " + driver.getWorkLandmark().trim());
        }
        if (driver.getWorkCity() != null && !driver.getWorkCity().isBlank()) {
            parts.add(driver.getWorkCity().trim());
        }
        if (parts.isEmpty()) {
            return driver.getPreferredWorkZone() != null
                ? OfflineMapService.getDriverZoneAddress(driver.getPreferredWorkZone())
                : "Unassigned zone";
        }
        return String.join(", ", parts);
    }

    private void resolveSelectionsFromFields() {
        if (selectedPickup == null) {
            SelectedLocation resolvedPickup = resolveLocationFromField(pickupField.getText());
            if (resolvedPickup != null) {
                selectedPickup = resolvedPickup;
                loadDrivers();
            }
        }
        if (selectedDrop == null) {
            SelectedLocation resolvedDrop = resolveLocationFromField(dropoffField.getText());
            if (resolvedDrop != null) {
                selectedDrop = resolvedDrop;
            }
        }
        if (selectedPickup != null && selectedDrop != null) {
            routeDistanceKm = Math.max(routeDistanceKm, OfflineMapService.distanceKmLatLng(
                selectedPickup.lat, selectedPickup.lng, selectedDrop.lat, selectedDrop.lng
            ));
            if (routeDurationMinutes <= 0) {
                routeDurationMinutes = Math.max(15, (routeDistanceKm / 35.0) * 60.0);
            }
            syncReturnTimeFromRoute();
            if (webEngine != null) {
                webEngine.executeScript("setLocation('pickup', " + jsString(selectedPickup.label) + ", " + selectedPickup.lat + ", " + selectedPickup.lng + ");");
                webEngine.executeScript("setLocation('drop', " + jsString(selectedDrop.label) + ", " + selectedDrop.lat + ", " + selectedDrop.lng + ");");
            }
        }
    }

    private SelectedLocation resolveLocationFromField(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.length() < 2) {
            return null;
        }
        List<LocationSuggestion> matches = fetchLocationSuggestions(value);
        if (matches.isEmpty()) {
            return null;
        }
        LocationSuggestion best = matches.stream()
            .sorted((left, right) -> Integer.compare(matchScore(right.label, value), matchScore(left.label, value)))
            .findFirst()
            .orElse(null);
        return best == null ? null : new SelectedLocation(best.label, best.lat, best.lng);
    }

    private boolean labelsCompatible(String selectedLabel, String typedValue) {
        String selected = normalizePlace(selectedLabel);
        String typed = normalizePlace(typedValue);
        return typed.isBlank() || selected.equals(typed) || selected.contains(typed) || typed.contains(selected);
    }

    private int matchScore(String candidate, String typedValue) {
        String candidateNorm = normalizePlace(candidate);
        String typedNorm = normalizePlace(typedValue);
        if (candidateNorm.equals(typedNorm)) {
            return 1000;
        }
        if (candidateNorm.startsWith(typedNorm)) {
            return 700 - (candidateNorm.length() - typedNorm.length());
        }
        if (candidateNorm.contains(typedNorm)) {
            return 500 - (candidateNorm.length() - typedNorm.length());
        }
        return 0;
    }

    private String normalizePlace(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private double distanceFromDriver(Driver driver) {
        if (selectedPickup == null) {
            return 0;
        }
        return OfflineMapService.distanceKm(driver.getCurrentLat(), driver.getCurrentLong(), selectedPickup.lat, selectedPickup.lng);
    }

    private String jsString(String value) {
        return "'" + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")
            + "'";
    }

    private void showOutOfServiceDialog(String label, String country) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Outside Service Area");
        alert.setHeaderText("Rento is not available there yet");
        String place = label == null || label.isBlank() ? "that location" : label;
        String countryText = country == null || country.isBlank() ? "outside India" : country;
        alert.setContentText("You selected " + place + " (" + countryText + "). Please choose a pickup or drop location inside India.");
        DialogPane pane = alert.getDialogPane();
        pane.setStyle("-fx-background-color: #171732; -fx-border-color: rgba(124,58,237,0.45); -fx-border-radius: 18; -fx-background-radius: 18;");
        if (pane.lookup(".content.label") != null) {
            pane.lookup(".content.label").setStyle("-fx-text-fill: #d8d3f0; -fx-font-size: 14px;");
        }
        if (pane.lookup(".header-panel") != null) {
            pane.lookup(".header-panel").setStyle("-fx-background-color: #201744;");
        }
        alert.showAndWait();
    }

    public class MapBridge {
        public void onLocationSelected(String role, String label, double lat, double lng) {
            Platform.runLater(() -> onMapLocationSelected(role, label, lat, lng));
        }

        public void onRouteUpdated(double distanceKm, double durationMinutes) {
            Platform.runLater(() -> {
                routeDistanceKm = distanceKm;
                routeDurationMinutes = durationMinutes;
                syncReturnTimeFromRoute();
                onCalculatePrice();
            });
        }

        public void onOutOfService(String label, String country) {
            Platform.runLater(() -> showOutOfServiceDialog(label, country));
        }

        public void onMapError(String message) {
            Platform.runLater(() -> showError(message));
        }

        public void onSearchResults(String role, String json) {
            Platform.runLater(() -> BookingDetailController.this.onSearchResults(role, json));
        }
    }

    private static class SelectedLocation {
        final String label;
        final double lat;
        final double lng;

        SelectedLocation(String label, double lat, double lng) {
            this.label = label;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static class LocationSuggestion {
        final String label;
        final double lat;
        final double lng;

        LocationSuggestion(String label, double lat, double lng) {
            this.label = label;
            this.lat = lat;
            this.lng = lng;
        }
    }
}
