package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.DriverDAO;
import com.rento.models.Booking;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.services.FareCalculator;
import com.rento.services.NotificationService;
import com.rento.services.OfflineMapService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.bson.types.ObjectId;
import java.util.Date;

/**
 * ActiveDriverRideController — driver driving rider to drop point.
 *
 * Timeline ticks every 1s:
 *  - Steps driver toward drop location
 *  - Updates elapsed timer, distance remaining, earning label on map overlay
 *  - Auto-completes when arrived at drop
 *
 * "End Trip" button → gate: must be on trip → sets booking.status=COMPLETED
 */
public class ActiveDriverRideController {

    @FXML private WebView mapWebView;
    @FXML private Label elapsedLabel;
    @FXML private Label earningLabel;
    @FXML private Label destLabel;
    @FXML private Label distLabel;
    @FXML private Label riderLabel;
    @FXML private Button endTripBtn;
    @FXML private Button cancelTripBtn;
    @FXML private Label endTripErrorLabel;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverDAO  driverDAO  = new DriverDAO();
    private final BookingService bookingService = new BookingService();
    private final FareCalculator fareCalc = new FareCalculator();
    private final NotificationService notificationService = new NotificationService();

    private Timeline rideTicker;
    private WebEngine webEngine;
    private int secondsElapsed = 0;
    private double driverX, driverY, pickupX, pickupY, dropX, dropY;
    private String bookingId;

    @FXML
    public void initialize() {
        bookingId = Session.activeBookingId;
        if (bookingId == null) {
            AlertUtil.showGateError("No active booking found.");
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
            return;
        }

        destLabel.setText(nvl(Session.pendingDropAddress, "—"));
        riderLabel.setText(nvl(Session.activeRiderName, "Rider"));
        hydrateSessionFromBooking();
        driverX = Session.driverCurrentX;
        driverY = Session.driverCurrentY;
        pickupX = Session.pendingPickupX;
        pickupY = Session.pendingPickupY;
        dropX   = Session.pendingDropX;
        dropY   = Session.pendingDropY;

        webEngine = mapWebView.getEngine();
        String mapUrl = getClass().getResource("/map/map.html").toExternalForm();
        webEngine.load(mapUrl);
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                initMapPins();
            }
        });

        startTicker();
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
            if (booking.getPickupLat() != 0 || booking.getPickupLng() != 0) {
                Session.pendingPickupX = booking.getPickupLat();
                Session.pendingPickupY = booking.getPickupLng();
            }
            if (booking.getDropLat() != 0 || booking.getDropLng() != 0) {
                Session.pendingDropX = booking.getDropLat();
                Session.pendingDropY = booking.getDropLng();
            }
            if (Session.activeDriverObjectId != null) {
                var driver = driverDAO.findById(Session.activeDriverObjectId);
                if (driver != null) {
                    Session.driverCurrentX = driver.getCurrentLat();
                    Session.driverCurrentY = driver.getCurrentLong();
                }
            }
        } catch (Exception ignored) {}
    }

    private void initMapPins() {
        try {
            webEngine.executeScript(OfflineMapService.jsSetPickup(pickupX, pickupY,
                nvl(Session.pendingPickupAddress, "Pickup")));
            webEngine.executeScript(OfflineMapService.jsSetDrop(dropX, dropY,
                nvl(Session.pendingDropAddress, "Drop")));
            webEngine.executeScript(OfflineMapService.jsUpdateMarker("driver", driverX, driverY));
            webEngine.executeScript("fitTripBounds();");
        } catch (Exception ignored) {}
    }

    private void startTicker() {
        rideTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsElapsed++;
            double[] next = OfflineMapService.stepToward(driverX, driverY, dropX, dropY, 4.0);
            driverX = next[0]; driverY = next[1];

            double distKm = OfflineMapService.distanceKm(driverX, driverY, dropX, dropY);
            double earning = calcEarning();
            int mins = secondsElapsed / 60, secs = secondsElapsed % 60;

            if (Session.activeDriverObjectId != null)
                driverDAO.updateLocation(Session.activeDriverObjectId, driverX, driverY);

            Platform.runLater(() -> {
                elapsedLabel.setText(String.format("%02d:%02d", mins, secs));
                distLabel.setText(String.format("%.1f km", distKm));
                earningLabel.setText("₹" + String.format("%.2f", earning));
                try {
                    webEngine.executeScript(OfflineMapService.jsUpdateMarker("driver", driverX, driverY));
                } catch (Exception ignored) {}
            });

            // Auto-end on arrival
            if (OfflineMapService.hasArrived(driverX, driverY, dropX, dropY, 5.0)) {
                rideTicker.stop();
                Platform.runLater(this::completeTrip);
            }
        }));
        rideTicker.setCycleCount(Timeline.INDEFINITE);
        rideTicker.play();
    }

    private double calcEarning() {
        double distUnits = OfflineMapService.euclideanDistance(
            Session.pendingPickupX, Session.pendingPickupY, dropX, dropY);
        FareCalculator.FareBreakdown bd = fareCalc.calculateFare(
            "Chennai", nvl(Session.pendingVehicleCategory, "MINI"),
            distUnits, secondsElapsed / 60,
            Math.max(1.0, Session.activeSurgeMultiplier), 0);
        return bd != null ? bd.driverShare : 0;
    }

    @FXML
    private void onEndTrip() {
        AlertUtil.clearInlineError(endTripErrorLabel);
        // GATE: must be on trip
        if (Session.activeDriverObjectId != null) {
            try {
                var driver = driverDAO.findById(Session.activeDriverObjectId);
                if (driver != null && !driver.isOnTrip()) {
                    AlertUtil.showInlineError(endTripErrorLabel, "You are not currently on a trip.");
                    return;
                }
            } catch (Exception ignored) {}
        }
        if (!AlertUtil.showConfirmation("End Trip",
            "Confirm that you have dropped the rider at the destination?")) return;
        if (rideTicker != null) rideTicker.stop();
        completeTrip();
    }

    private void completeTrip() {
        try {
            Booking b = bookingDAO.findById(new ObjectId(bookingId));
            if (b != null) {
                b.setStatus("COMPLETED");
                b.setEndTime(new Date());
                b.setDurationMinutes(secondsElapsed / 60);
                bookingDAO.updateBooking(b);
                if (b.getUserId() != null) {
                    notificationService.addNotification(
                        b.getUserId(),
                        "Thank you for riding with Rento",
                        "Your driver has ended the trip. Please rate your driver with stars and share feedback on the trip end screen.",
                        null
                    );
                }
            }
            if (Session.activeDriverObjectId != null)
                driverDAO.setTripState(Session.activeDriverObjectId, false, (String) null);
        } catch (Exception ignored) {}
        NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
    }

    @FXML
    private void onCancelTrip() {
        AlertUtil.clearInlineError(endTripErrorLabel);
        if (!AlertUtil.showConfirmation("Cancel Trip Midway",
            "Use this only when the ride cannot continue, such as a vehicle breakdown. The rider will receive a refund.")) {
            return;
        }
        if (rideTicker != null) {
            rideTicker.stop();
        }
        boolean ok = SessionManager.getInstance().getCurrentUser() != null
            && bookingService.cancelActiveRideByDriver(
                new ObjectId(bookingId),
                SessionManager.getInstance().getCurrentUser().getId(),
                "Vehicle issue / unable to continue"
            );
        if (ok) {
            AlertUtil.showInfo("Trip cancelled", "The rider was notified and the applicable refund was credited.");
            Session.clearBookingContext();
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
        } else {
            AlertUtil.showInlineError(endTripErrorLabel, "Only active trips can be cancelled here.");
        }
    }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
