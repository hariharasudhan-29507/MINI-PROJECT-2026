package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.DriverDAO;
import com.rento.models.Booking;
import com.rento.models.Driver;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.services.OfflineMapService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.bson.types.ObjectId;

/**
 * BookingConfirmedController — OTP display, driver info card, 4-step status timeline.
 * Polls booking.status every 4 seconds. Handles free (≤5 min) / fee cancellation.
 */
public class BookingConfirmedController {

    @FXML private Label statusBadge;
    @FXML private Label driverNameLabel;
    @FXML private Label ratingLabel;
    @FXML private Label ridesLabel;
    @FXML private Label vehicleLabel;
    @FXML private Label plateLabel;
    @FXML private Label etaLabel;
    @FXML private Label dot2, dot3, dot4;
    @FXML private Label step2Label, step3Label, step4Label;
    @FXML private Label otpLabel;
    @FXML private Label tripPickup;
    @FXML private Label tripDrop;
    @FXML private Label tripFare;
    @FXML private Button cancelBtn;
    @FXML private Label cancelHint;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverDAO  driverDAO  = new DriverDAO();
    private final BookingService bookingService = new BookingService();

    private Timeline pollTimeline;
    private Timeline cancelFeeTimeline;
    private int secondsSinceAccepted = 0;
    private static final int FREE_CANCEL_WINDOW = 60; // 1 min
    private String bookingId;

    @FXML
    public void initialize() {
        bookingId = Session.activeBookingId;
        if (bookingId == null) {
            AlertUtil.showGateError("No active booking. Please start a new booking.");
            NavigationManager.navigateTo("/fxml/booking.fxml");
            return;
        }

        // Static trip info from session
        tripPickup.setText(nvl(Session.pendingPickupAddress, "—"));
        tripDrop.setText(nvl(Session.pendingDropAddress, "—"));
        tripFare.setText("₹" + String.format("%.2f", Session.pendingFareEstimate));
        otpLabel.setText(nvl(Session.activeBookingOtp, "••••"));

        loadDriverInfo();
        startPolling();
        startCancelFeeTimer();
    }

    // -----------------------------------------------------------------------
    // Driver info
    // -----------------------------------------------------------------------

    private void loadDriverInfo() {
        if (Session.activeDriverId == null) return;
        try {
            Driver d = driverDAO.findByUserId(new ObjectId(Session.activeDriverId));
            if (d == null) return;
            driverNameLabel.setText(nvl(d.getFullName(), "Unknown Driver"));
            ratingLabel.setText("⭐ " + String.format("%.1f", d.getRating()));
            ridesLabel.setText(d.getTotalRidesCompleted() + " rides");
            vehicleLabel.setText("Vehicle info pending");
            plateLabel.setText("");

            // ETA from driver position to pickup
            String eta = "—";
            try {
                double distKm = OfflineMapService.distanceKm(
                    d.getCurrentLat(), d.getCurrentLong(),
                    Session.pendingPickupX, Session.pendingPickupY);
                int etaMins = (int) Math.ceil(distKm / (30.0 / 60.0));
                eta = etaMins + " min";
            } catch (Exception ignored) {}
            final String etaFinal = eta;
            Platform.runLater(() -> etaLabel.setText(etaFinal));
        } catch (Exception e) {
            System.err.println("[BookingConfirmedController] loadDriverInfo: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            try {
                Booking b = bookingDAO.findById(new ObjectId(bookingId));
                if (b == null) return;
                Platform.runLater(() -> updateStatusUI(b.getStatusString()));
                if ("ACTIVE".equals(b.getStatusString()) || "IN_PROGRESS".equals(b.getStatusString())) {
                    stopTimers();
                    Platform.runLater(() -> NavigationManager.navigateTo("/fxml/active_ride.fxml"));
                } else if ("CANCELLED".equals(b.getStatusString())) {
                    stopTimers();
                    Platform.runLater(() -> {
                        AlertUtil.showGateError("Your ride was cancelled by the driver.");
                        Session.clearBookingContext();
                        NavigationManager.navigateTo("/fxml/booking.fxml");
                    });
                }
            } catch (Exception ex) {
                System.err.println("[BookingConfirmedController] poll: " + ex.getMessage());
            }
        }));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void updateStatusUI(String status) {
        if (status == null) return;
        switch (status) {
            case "ACCEPTED" -> {
                activate(dot2, step2Label); }
            case "CONFIRMED" -> {
                activate(dot2, step2Label); }
            case "DRIVER_ARRIVED" -> {
                activate(dot2, step2Label); activate(dot3, step3Label); }
            case "ACTIVE", "IN_PROGRESS" -> {
                activate(dot2, step2Label); activate(dot3, step3Label); activate(dot4, step4Label); }
        }
    }

    private void activate(Label dot, Label label) {
        dot.setStyle("-fx-text-fill: #7c3aed; -fx-font-size: 10px;");
        label.setStyle("-fx-text-fill: #f0f0f5; -fx-font-size: 14px;");
    }

    // -----------------------------------------------------------------------
    // Free / fee cancel timer
    // -----------------------------------------------------------------------

    private void startCancelFeeTimer() {
        cancelFeeTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsSinceAccepted++;
            int remaining = FREE_CANCEL_WINDOW - secondsSinceAccepted;
            Platform.runLater(() -> {
                if (remaining > 0) {
                    cancelHint.setText("Free cancellation for " + (remaining / 60) + "m " + (remaining % 60) + "s");
                } else {
                    cancelHint.setText("Cancellation closes after 1 minute");
                    cancelHint.setStyle("-fx-text-fill: #f72585; -fx-font-size: 12px;");
                }
            });
        }));
        cancelFeeTimeline.setCycleCount(Timeline.INDEFINITE);
        cancelFeeTimeline.play();
    }

    // -----------------------------------------------------------------------
    // Cancel action
    // -----------------------------------------------------------------------

    @FXML
    private void onCancelRide() {
        if (secondsSinceAccepted >= FREE_CANCEL_WINDOW) {
            AlertUtil.showGateError("This booking can only be cancelled within 1 minute before the trip starts.");
            return;
        }
        boolean proceed = AlertUtil.showConfirmation("Cancel Ride",
            "Are you sure you want to cancel this ride? You are still within the 1-minute cancellation window.");
        if (!proceed) return;

        stopTimers();
        try {
            boolean ok = SessionManager.getInstance().getCurrentUser() != null
                && bookingService.cancelBookingByUser(new ObjectId(bookingId), SessionManager.getInstance().getCurrentUser().getId());
            if (!ok) {
                AlertUtil.showGateError("This booking can only be cancelled within 1 minute before the trip starts.");
                startPolling();
                startCancelFeeTimer();
                return;
            }
        } catch (Exception ignored) {
            startPolling();
            startCancelFeeTimer();
            return;
        }

        Session.clearBookingContext();
        NavigationManager.navigateTo("/fxml/booking.fxml");
    }

    private void stopTimers() {
        if (pollTimeline != null) pollTimeline.stop();
        if (cancelFeeTimeline != null) cancelFeeTimeline.stop();
    }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
