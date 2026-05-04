package com.rento.controllers;

import com.rento.dao.BookingDAO;

import com.rento.models.Booking;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.util.Duration;

/**
 * BookingSearchController — "Finding your driver" screen.
 *
 * Polls booking.status every 3 seconds for 90 seconds.
 * ACCEPTED → navigate to booking_confirmed.fxml
 * NO_DRIVER / timeout → show gate error + return to booking
 * CANCELLED → navigate back
 */
public class BookingSearchController {

    @FXML private ProgressIndicator searchSpinner;
    @FXML private Label statusLabel;
    @FXML private Label pickupLabel;
    @FXML private Label dropLabel;
    @FXML private Label fareLabel;
    @FXML private Label categoryLabel;
    @FXML private Label surgeLabel;
    @FXML private ProgressBar timeoutBar;
    @FXML private Label timerLabel;
    @FXML private Label walletHint;
    @FXML private Button cancelBtn;

    private static final int TIMEOUT_SECONDS = 90;
    private static final int POLL_INTERVAL_SECONDS = 3;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final BookingService bookingService = new BookingService();


    private Timeline pollTimeline;
    private Timeline countdownTimeline;
    private int elapsedSeconds = 0;
    private String bookingId;

    @FXML
    public void initialize() {
        // Populate UI from Session context
        pickupLabel.setText(nvl(Session.pendingPickupAddress, "—"));
        dropLabel.setText(nvl(Session.pendingDropAddress, "—"));
        fareLabel.setText("₹" + String.format("%.2f", Session.pendingFareEstimate));
        categoryLabel.setText(nvl(Session.pendingVehicleCategory, "—"));
        surgeLabel.setText("—");
        walletHint.setText("Wallet: " + ValidationUtil.formatCurrency(Session.getWalletBalance()));
        bookingId = Session.activeBookingId;

        if (bookingId == null) {
            AlertUtil.showGateError("No active booking found. Please start a new booking.");
            NavigationManager.navigateTo("/fxml/booking.fxml");
            return;
        }

        startCountdown();
        startPolling();
    }

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(
            Duration.seconds(POLL_INTERVAL_SECONDS),
            event -> checkBookingStatus()
        ));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void checkBookingStatus() {
        try {
            Booking booking = bookingDAO.findById(new org.bson.types.ObjectId(bookingId));
            if (booking == null) {
                handleTimeout();
                return;
            }
            String status = booking.getStatusString();
            switch (status != null ? status : "") {
                case "ACCEPTED", "CONFIRMED" -> {
                    stopTimers();
                    statusLabel.setText("Driver found! Redirecting...");
                    Platform.runLater(() ->
                        NavigationManager.navigateTo("/fxml/booking_confirmed.fxml")
                    );
                }
                case "CANCELLED" -> {
                    stopTimers();
                    AlertUtil.showGateError("Your booking was cancelled. Please try again.");
                    Session.clearBookingContext();
                    Platform.runLater(() ->
                        NavigationManager.navigateTo("/fxml/booking.fxml")
                    );
                }
                case "NO_DRIVER" -> {
                    stopTimers();
                    AlertUtil.showGateError(
                        "No drivers are available near your pickup location right now.\n"
                        + "Please try again in a few minutes or choose a different pickup point."
                    );
                    Session.clearBookingContext();
                    Platform.runLater(() ->
                        NavigationManager.navigateTo("/fxml/booking.fxml")
                    );
                }
                default -> updateStatusText(booking.getStatusString());
            }
        } catch (Exception e) {
            System.err.println("[BookingSearchController] Poll error: " + e.getMessage());
        }
    }

    private void updateStatusText(String status) {
        Platform.runLater(() -> statusLabel.setText(switch (status != null ? status : "") {
            case "PENDING"       -> "Searching nearby drivers...";
            case "DISPATCHING"   -> "Notifying available drivers...";
            default              -> "Looking for your driver...";
        }));
    }

    // -----------------------------------------------------------------------
    // Countdown
    // -----------------------------------------------------------------------

    private void startCountdown() {
        countdownTimeline = new Timeline(new KeyFrame(
            Duration.seconds(1),
            event -> {
                elapsedSeconds++;
                int remaining = TIMEOUT_SECONDS - elapsedSeconds;
                double progress = (double) remaining / TIMEOUT_SECONDS;
                Platform.runLater(() -> {
                    timerLabel.setText(remaining + "s");
                    timeoutBar.setProgress(progress);
                });
                if (elapsedSeconds >= TIMEOUT_SECONDS) {
                    handleTimeout();
                }
            }
        ));
        countdownTimeline.setCycleCount(TIMEOUT_SECONDS);
        countdownTimeline.play();
    }

    private void handleTimeout() {
        stopTimers();
        Platform.runLater(() -> {
            AlertUtil.showGateError(
                "Search timed out after 90 seconds.\n"
                + "No drivers accepted your request. Please try again."
            );
            // Cancel the booking in DB
            try {
                Booking booking = bookingDAO.findById(new org.bson.types.ObjectId(bookingId));
                if (booking != null) {
                    booking.setStatus("CANCELLED");
                    bookingDAO.updateBooking(booking);
                }
            } catch (Exception ignored) {}
            Session.clearBookingContext();
            NavigationManager.navigateTo("/fxml/booking.fxml");
        });
    }

    private void stopTimers() {
        if (pollTimeline != null) pollTimeline.stop();
        if (countdownTimeline != null) countdownTimeline.stop();
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    @FXML
    private void onCancel() {
        boolean confirmed = AlertUtil.showConfirmation(
            "Cancel Search",
            "Are you sure you want to cancel the driver search?"
        );
        if (!confirmed) return;
        try {
            if (SessionManager.getInstance().getCurrentUser() != null) {
                boolean cancelled = bookingService.cancelBookingByUser(
                    new org.bson.types.ObjectId(bookingId),
                    SessionManager.getInstance().getCurrentUser().getId()
                );
                if (!cancelled) {
                    AlertUtil.showGateError("Booking can only be cancelled within 1 minute of placing it.");
                    return;
                }
            }
        } catch (Exception ignored) {}
        stopTimers();
        Session.clearBookingContext();
        NavigationManager.navigateTo("/fxml/booking.fxml");
    }

    private String nvl(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
