package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.DriverDAO;
import com.rento.models.Booking;
import com.rento.navigation.NavigationManager;
import com.rento.services.OfflineMapService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.bson.types.ObjectId;

/**
 * DriverNavigationController — driver heading to pickup point.
 *
 * Timeline ticks every 1s:
 *  - Steps driver position toward pickup
 *  - Updates distance label and Leaflet map marker
 *  - On arrival: reveals OTP entry section
 *
 * "I've Arrived" button → shows OTP field
 * "Start Trip" → validates OTP → sets booking.status=ACTIVE → navigates to active_driver_ride.fxml
 */
public class DriverNavigationController {

    @FXML private WebView mapWebView;
    @FXML private Label riderNameLabel;
    @FXML private Label etaLabel;
    @FXML private Label distLabel;
    @FXML private Label pickupAddressLabel;
    @FXML private VBox otpSection;
    @FXML private TextField otpField;
    @FXML private Button startTripBtn;
    @FXML private Label otpErrorLabel;
    @FXML private Button arrivedBtn;
    @FXML private Button cancelBtn;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverDAO  driverDAO  = new DriverDAO();

    private Timeline moveTicker;
    private WebEngine webEngine;
    private String bookingId;
    private double driverX, driverY;
    private double pickupX, pickupY;
    private int otpAttempts = 0;
    private static final int MAX_OTP_ATTEMPTS = 3;

    @FXML
    public void initialize() {
        bookingId = Session.activeBookingId;
        if (bookingId == null) {
            AlertUtil.showGateError("No active booking.");
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
            return;
        }

        pickupAddressLabel.setText(nvl(Session.pendingPickupAddress, "—"));
        riderNameLabel.setText(nvl(Session.activeRiderName, "Rider"));
        driverX = Session.driverCurrentX;
        driverY = Session.driverCurrentY;
        pickupX = Session.pendingPickupX;
        pickupY = Session.pendingPickupY;

        // Load map
        webEngine = mapWebView.getEngine();
        String mapUrl = getClass().getResource("/map/map.html").toExternalForm();
        webEngine.load(mapUrl);
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                initMapPins();
            }
        });

        updateDistanceLabel();
        startMovementTicker();
    }

    private void initMapPins() {
        try {
            webEngine.executeScript(
                OfflineMapService.jsSetPickup(pickupX, pickupY,
                    nvl(Session.pendingPickupAddress, "Pickup")));
            webEngine.executeScript(
                OfflineMapService.jsUpdateMarker("driver", driverX, driverY));
            webEngine.executeScript("fitTripBounds();");
        } catch (Exception ignored) {}
    }

    private void startMovementTicker() {
        moveTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            double[] next = OfflineMapService.stepToward(driverX, driverY, pickupX, pickupY, 4.0);
            driverX = next[0]; driverY = next[1];
            updateDistanceLabel();
            try {
                String js = OfflineMapService.jsUpdateMarker("driver", driverX, driverY);
                Platform.runLater(() -> {
                    try { webEngine.executeScript(js); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}

            // Persist driver location
            if (Session.activeDriverObjectId != null)
                driverDAO.updateLocation(Session.activeDriverObjectId, driverX, driverY);

            // Auto-arrive
            if (OfflineMapService.hasArrived(driverX, driverY, pickupX, pickupY, 5.0)) {
                moveTicker.stop();
                Platform.runLater(() -> {
                    arrivedBtn.fire();
                    etaLabel.setText("Arrived!");
                });
            }
        }));
        moveTicker.setCycleCount(Timeline.INDEFINITE);
        moveTicker.play();
    }

    private void updateDistanceLabel() {
        double distKm = OfflineMapService.distanceKm(driverX, driverY, pickupX, pickupY);
        int etaMins = (int) Math.ceil(distKm / (30.0 / 60.0));
        Platform.runLater(() -> {
            distLabel.setText(String.format("%.1f km", distKm));
            etaLabel.setText(etaMins + " min");
        });
    }

    // -----------------------------------------------------------------------
    // Arrived
    // -----------------------------------------------------------------------

    @FXML
    private void onArrived() {
        if (moveTicker != null) moveTicker.stop();
        arrivedBtn.setDisable(true);
        arrivedBtn.setText("✓ Arrived");
        otpSection.setVisible(true);
        otpSection.setManaged(true);

        // Update booking status to DRIVER_ARRIVED
        try {
            Booking b = bookingDAO.findById(new ObjectId(bookingId));
            if (b != null) { b.setStatus("DRIVER_ARRIVED"); bookingDAO.updateBooking(b); }
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // OTP Verification → Start Trip
    // -----------------------------------------------------------------------

    @FXML
    private void onStartTrip() {
        AlertUtil.clearInlineError(otpErrorLabel);
        String enteredOtp = otpField.getText().trim();

        if (enteredOtp.isEmpty()) {
            AlertUtil.showInlineError(otpErrorLabel, "Please enter the OTP.");
            return;
        }
        if (otpAttempts >= MAX_OTP_ATTEMPTS) {
            AlertUtil.showGateError("Maximum OTP attempts exceeded. Booking flagged for admin review.");
            flagAndExit();
            return;
        }

        String expectedOtp = Session.activeBookingOtp;
        if (!enteredOtp.equals(expectedOtp)) {
            otpAttempts++;
            int remaining = MAX_OTP_ATTEMPTS - otpAttempts;
            AlertUtil.showOtpError(remaining);
            if (otpAttempts >= MAX_OTP_ATTEMPTS) flagAndExit();
            return;
        }

        // OTP correct — start trip
        try {
            Booking b = bookingDAO.findById(new ObjectId(bookingId));
            if (b != null) {
                b.setStatus("ACTIVE");
                b.setOtpVerified(true);
                b.setStartTime(new java.util.Date());
                bookingDAO.updateBooking(b);
            }
            if (Session.activeDriverObjectId != null)
                driverDAO.setTripState(Session.activeDriverObjectId, true, new ObjectId(bookingId));
        } catch (Exception ignored) {}

        NavigationManager.navigateTo("/fxml/active_driver_ride.fxml");
    }

    private void flagAndExit() {
        try {
            Booking b = bookingDAO.findById(new ObjectId(bookingId));
            if (b != null) { b.setOtpFlagged(true); b.setStatus("FLAGGED"); bookingDAO.updateBooking(b); }
        } catch (Exception ignored) {}
        Session.clearBookingContext();
        NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
    }

    // -----------------------------------------------------------------------
    // Cancel
    // -----------------------------------------------------------------------

    @FXML
    private void onCancelBooking() {
        boolean confirmed = AlertUtil.showConfirmation("Cancel Booking",
            "Are you sure you want to cancel this booking request?");
        if (!confirmed) return;
        if (moveTicker != null) moveTicker.stop();
        try {
            Booking b = bookingDAO.findById(new ObjectId(bookingId));
            if (b != null) { b.setStatus("CANCELLED"); bookingDAO.updateBooking(b); }
            if (Session.activeDriverObjectId != null) {
                driverDAO.incrementRejectCount(Session.activeDriverObjectId);
                long rejects = driverDAO.findById(Session.activeDriverObjectId).getConsecutiveRejectCount();
                if (rejects >= 3) {
                    driverDAO.setOnlineStatus(Session.activeDriverObjectId, false);
                    AlertUtil.showAutoOfflineWarning();
                }
            }
        } catch (Exception ignored) {}
        Session.clearBookingContext();
        NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
    }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
