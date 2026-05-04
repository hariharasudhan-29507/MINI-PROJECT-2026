package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.DriverDAO;
import com.rento.dao.PaymentDAO;
import com.rento.dao.UserDAO;
import com.rento.models.Booking;
import com.rento.models.Driver;
import com.rento.models.Payment;
import com.rento.navigation.NavigationManager;
import com.rento.services.FareCalculator;
import com.rento.services.NotificationService;
import com.rento.services.OfflineMapService;
import com.rento.utils.AlertUtil;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.bson.types.ObjectId;

/**
 * ActiveRideController — live ride tracking screen (rider side).
 *
 * Timeline ticks every 1 second:
 *  - Increments elapsed time
 *  - Moves driver dot on Leaflet map toward drop
 *  - Recalculates live fare (base + dist + time so far)
 *  - Polls booking.status for COMPLETED signal from driver
 *
 * When trip ends (driver presses End Trip):
 *  booking.status → COMPLETED → navigate to trip_end.fxml
 */
public class ActiveRideController {

    @FXML private WebView mapWebView;
    @FXML private Label elapsedTimeLabel;
    @FXML private Label liveFareLabel;
    @FXML private Label destinationLabel;
    @FXML private Label distLabel;
    @FXML private Label driverNameLabel;
    @FXML private Label driverRatingLabel;
    @FXML private Label pickupLabel;
    @FXML private Label dropLabel;
    @FXML private Label tripTimeLabel;
    @FXML private Label paymentAmountLabel;
    @FXML private Label paymentStatusLabel;
    @FXML private Button sosBtn;
    @FXML private Button backToBookingsBtn;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverDAO driverDAO = new DriverDAO();
    private final UserDAO userDAO = new UserDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final FareCalculator fareCalc = new FareCalculator();
    private final NotificationService notificationService = new NotificationService();

    private Timeline rideTicker;
    private int secondsElapsed = 0;
    private String bookingId;
    private double surgeMultiplier = 1.0;
    private WebEngine webEngine;

    // Driver position tracking
    private double driverX, driverY;
    private double dropX, dropY;

    @FXML
    public void initialize() {
        bookingId = Session.activeBookingId;
        if (bookingId == null) {
            AlertUtil.showGateError("No active booking found.");
            NavigationManager.navigateTo("/fxml/booking.fxml");
            return;
        }

        destinationLabel.setText(nvl(Session.pendingDropAddress, "—"));
        pickupLabel.setText(nvl(Session.pendingPickupAddress, "—"));
        dropLabel.setText(nvl(Session.pendingDropAddress, "—"));
        surgeMultiplier = Session.activeSurgeMultiplier;
        hydrateSessionFromBooking();

        // Map position
        driverX = Session.driverCurrentX;
        driverY = Session.driverCurrentY;
        dropX   = Session.pendingDropX;
        dropY   = Session.pendingDropY;

        // Load Leaflet map
        webEngine = mapWebView.getEngine();
        String mapUrl = getClass().getResource("/map/map.html").toExternalForm();
        webEngine.load(mapUrl);
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                initMapPins();
            }
        });

        loadRideDetails();
        startRideTicker();
    }

    private void hydrateSessionFromBooking() {
        try {
            Booking booking = bookingDAO.findById(new ObjectId(bookingId));
            if (booking == null) {
                return;
            }
            Session.pendingPickupAddress = nvl(booking.getPickupAddress(), booking.getPickupLocation());
            Session.pendingDropAddress = nvl(booking.getDropAddress(), booking.getDropoffLocation());
            Session.pendingVehicleCategory = booking.getVehicleCategory();
            Session.pendingFareEstimate = booking.getFinalFare();
            Session.activeDriverId = booking.getDriverId() != null ? booking.getDriverId().toHexString() : Session.activeDriverId;
            if (booking.getPickupLat() != 0 || booking.getPickupLng() != 0) {
                Session.pendingPickupX = booking.getPickupLat();
                Session.pendingPickupY = booking.getPickupLng();
            }
            if (booking.getDropLat() != 0 || booking.getDropLng() != 0) {
                Session.pendingDropX = booking.getDropLat();
                Session.pendingDropY = booking.getDropLng();
            }
            Driver driver = booking.getDriverId() != null ? driverDAO.findByUserId(booking.getDriverId()) : null;
            if (driver != null) {
                Session.driverCurrentX = driver.getCurrentLat();
                Session.driverCurrentY = driver.getCurrentLong();
            }
        } catch (Exception ignored) {}
    }

    private void loadRideDetails() {
        try {
            Booking booking = bookingDAO.findById(new ObjectId(bookingId));
            if (booking == null) {
                return;
            }
            pickupLabel.setText(nvl(booking.getPickupAddress(), booking.getPickupLocation()));
            dropLabel.setText(nvl(booking.getDropAddress(), booking.getDropoffLocation()));
            destinationLabel.setText(nvl(booking.getDropAddress(), booking.getDropoffLocation()));
            tripTimeLabel.setText(booking.getPickupDateTime() != null
                ? DateTimeUtil.formatDateTime(booking.getPickupDateTime())
                : "Time pending");
            paymentAmountLabel.setText(String.format("₹%.2f", booking.getFinalFare()));

            String paymentState = "Payment pending";
            if (booking.isPaidVerified()) {
                paymentState = "Paid";
            } else if (booking.isCashPaymentPending()) {
                paymentState = "Cash to collect";
            } else if (booking.getPaymentStatus() != null && !booking.getPaymentStatus().isBlank()) {
                paymentState = booking.getPaymentStatus().replace('_', ' ');
            } else if (booking.getPaymentId() != null) {
                Payment payment = paymentDAO.findById(booking.getPaymentId());
                if (payment != null && payment.getStatus() != null) {
                    paymentState = payment.getStatus().name().replace('_', ' ');
                }
            }
            paymentStatusLabel.setText(paymentState);

            Driver driver = null;
            if (booking.getDriverId() != null) {
                driver = driverDAO.findByUserId(booking.getDriverId());
            } else if (Session.activeDriverId != null) {
                driver = driverDAO.findByUserId(new ObjectId(Session.activeDriverId));
            }
            driverNameLabel.setText(driver != null ? nvl(driver.getFullName(), "Assigned driver") : nvl(booking.getAssignedDriverName(), "Assigned driver"));
            driverRatingLabel.setText(driver != null
                ? String.format("⭐ %.1f", driver.getRating())
                : "⭐ —");
        } catch (Exception ignored) {}
    }

    private void initMapPins() {
        try {
            webEngine.executeScript(
                OfflineMapService.jsSetPickup(
                    Session.pendingPickupX, Session.pendingPickupY,
                    nvl(Session.pendingPickupAddress, "Pickup"))
            );
            webEngine.executeScript(
                OfflineMapService.jsSetDrop(dropX, dropY,
                    nvl(Session.pendingDropAddress, "Drop"))
            );
            webEngine.executeScript(OfflineMapService.jsUpdateMarker("driver", driverX, driverY));
            webEngine.executeScript("fitTripBounds();");
        } catch (Exception ignored) {}
    }

    private void startRideTicker() {
        rideTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsElapsed++;
            updateElapsedLabel();
            moveDriver();
            updateLiveFare();
            pollForTripEnd();
        }));
        rideTicker.setCycleCount(Timeline.INDEFINITE);
        rideTicker.play();
    }

    private void updateElapsedLabel() {
        int mins = secondsElapsed / 60;
        int secs = secondsElapsed % 60;
        Platform.runLater(() ->
            elapsedTimeLabel.setText(String.format("%02d:%02d", mins, secs))
        );
    }

    private void moveDriver() {
        double[] next = OfflineMapService.stepToward(driverX, driverY, dropX, dropY, 3.0);
        driverX = next[0];
        driverY = next[1];
        double distKm = OfflineMapService.distanceKm(driverX, driverY, dropX, dropY);
        try {
            final String jsMove = OfflineMapService.jsUpdateMarker("driver", driverX, driverY);
            Platform.runLater(() -> {
                try { webEngine.executeScript(jsMove); } catch (Exception ignored) {}
                distLabel.setText(String.format("%.1f km", distKm));
            });
        } catch (Exception ignored) {}
    }

    private void updateLiveFare() {
        double distanceUnits = OfflineMapService.euclideanDistance(
            Session.pendingPickupX, Session.pendingPickupY, dropX, dropY
        );
        FareCalculator.FareBreakdown bd = fareCalc.calculateFare(
            "Chennai",
            nvl(Session.pendingVehicleCategory, "MINI"),
            distanceUnits,
            secondsElapsed / 60,
            surgeMultiplier,
            0
        );
        if (bd != null) {
            Platform.runLater(() ->
                liveFareLabel.setText(String.format("₹%.2f", bd.finalFare))
            );
        }
    }

    private void pollForTripEnd() {
        try {
            Booking booking = bookingDAO.findById(new ObjectId(bookingId));
            if (booking != null && "COMPLETED".equals(booking.getStatusString())) {
                rideTicker.stop();
                Platform.runLater(() ->
                    NavigationManager.navigateTo("/fxml/trip_end.fxml")
                );
            } else if (booking != null && "CANCELLED".equals(booking.getStatusString())) {
                rideTicker.stop();
                Platform.runLater(() -> {
                    AlertUtil.showInfo("Ride Cancelled", "This ride was cancelled. Any refund that applies has been added to your wallet.");
                    Session.clearBookingContext();
                    NavigationManager.navigateTo("/fxml/booking.fxml");
                });
            }
        } catch (Exception ignored) {}
    }

    @FXML
    private void onSOS() {
        AlertUtil.showInfo("🆘 SOS Triggered",
            "Emergency services have been notified.\n"
            + "Your real-time location has been shared with Rento safety team.\n"
            + "Stay calm — help is on the way."
        );
    }

    @FXML
    private void onBackToBookings() {
        if (rideTicker != null) {
            rideTicker.stop();
        }
        NavigationManager.navigateTo("/fxml/booking.fxml");
    }

    private String nvl(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
