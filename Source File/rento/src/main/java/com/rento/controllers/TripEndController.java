package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.services.LedgerPaymentService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import com.rento.dao.BookingDAO;
import com.rento.dao.ReviewDAO;
import com.rento.dao.DriverDAO;
import com.rento.models.Booking;
import com.rento.models.Driver;
import com.rento.models.Review;
import com.rento.services.FareCalculator;
import com.rento.services.OfflineMapService;
import com.rento.utils.PaymentResult;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;

/**
 * TripEndController — fare receipt, wallet/cash payment, and review submission.
 */
public class TripEndController {

    @FXML private Label baseFareLabel;
    @FXML private Label distLabel;
    @FXML private Label distChargeLabel;
    @FXML private Label timeLabel;
    @FXML private Label timeChargeLabel;
    @FXML private Label surgeLabel;
    @FXML private Label surgeChargeLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label gstLabel;
    @FXML private HBox  discountRow;
    @FXML private Label discountLabel;
    @FXML private Label totalLabel;
    @FXML private Label walletBalanceLabel;
    @FXML private Button payWalletBtn;
    @FXML private Button payCashBtn;
    @FXML private Label paymentErrorLabel;

    @FXML private Button star1, star2, star3, star4, star5;
    @FXML private Label ratingLabel;
    @FXML private FlowPane tagContainer;
    @FXML private TextArea commentField;
    @FXML private Button submitReviewBtn;
    @FXML private Label reviewStatusLabel;
    @FXML private Label driverNameLabel;
    @FXML private Label driverRatingLabel;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverDAO driverDAO = new DriverDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final LedgerPaymentService ledger = new LedgerPaymentService();
    private final FareCalculator fareCalc = new FareCalculator();

    private FareCalculator.FareBreakdown breakdown;
    private int selectedStars = 0;
    private boolean paymentDone = false;

    @FXML
    public void initialize() {
        loadFareBreakdown();
        loadDriverInfo();
        buildReviewTags();
        walletBalanceLabel.setText("₹" + fmt(Session.getWalletBalance()));
    }

    private void loadFareBreakdown() {
        String bookingId = Session.activeBookingId;
        if (bookingId == null) { AlertUtil.showGateError("No active booking."); return; }
        try {
            Booking booking = bookingDAO.findById(new ObjectId(bookingId));
            if (booking == null) { AlertUtil.showGateError("Booking not found."); return; }

            double pickupLat = booking.getPickupLat() != 0 || booking.getPickupLng() != 0 ? booking.getPickupLat() : Session.pendingPickupX;
            double pickupLng = booking.getPickupLat() != 0 || booking.getPickupLng() != 0 ? booking.getPickupLng() : Session.pendingPickupY;
            double dropLat = booking.getDropLat() != 0 || booking.getDropLng() != 0 ? booking.getDropLat() : Session.pendingDropX;
            double dropLng = booking.getDropLat() != 0 || booking.getDropLng() != 0 ? booking.getDropLng() : Session.pendingDropY;
            double distUnits = booking.getRouteDistanceKm() > 0
                ? booking.getRouteDistanceKm()
                : OfflineMapService.distanceKm(pickupLat, pickupLng, dropLat, dropLng);
            double surge = Math.max(1.0, Session.activeSurgeMultiplier);

            breakdown = fareCalc.calculateFare("Chennai",
                nvl(Session.pendingVehicleCategory, "MINI"),
                distUnits, booking.getDurationMinutes(), surge, 0);

            if (breakdown == null) {
                AlertUtil.showGateError("Fare config not found for this vehicle category.");
                payWalletBtn.setDisable(true); payCashBtn.setDisable(true); return;
            }

            baseFareLabel.setText("₹" + fmt(breakdown.baseFare));
            distLabel.setText("Distance (" + String.format("%.1f", breakdown.distanceKm) + " km)");
            distChargeLabel.setText("₹" + fmt(breakdown.distanceCharge));
            timeLabel.setText("Time (" + breakdown.timeMinutes + " min)");
            timeChargeLabel.setText("₹" + fmt(breakdown.timeCharge));
            surgeLabel.setText("Surge (" + breakdown.surgeMultiplier + "×)");
            surgeChargeLabel.setText("×" + breakdown.surgeMultiplier);
            subtotalLabel.setText("₹" + fmt(breakdown.subtotal));
            gstLabel.setText("₹" + fmt(breakdown.taxAmount));
            totalLabel.setText("₹" + fmt(breakdown.finalFare));
            if (breakdown.discountAmount > 0) {
                discountRow.setVisible(true); discountRow.setManaged(true);
                discountLabel.setText("-₹" + fmt(breakdown.discountAmount));
            }
            Session.pendingFareEstimate = breakdown.finalFare;
        } catch (Exception e) {
            System.err.println("[TripEndController] " + e.getMessage());
        }
    }

    @FXML
    private void onPayWallet() {
        if (breakdown == null) return;
        AlertUtil.clearInlineError(paymentErrorLabel);
        double balance = Session.getWalletBalance();
        if (balance < breakdown.finalFare) {
            if (AlertUtil.showTopUpPrompt(balance, breakdown.finalFare))
                NavigationManager.navigateTo("/fxml/wallet_topup.fxml");
            return;
        }
        payWalletBtn.setDisable(true); payCashBtn.setDisable(true);
        payWalletBtn.setText("Processing...");
        try {
            PaymentResult r = ledger.processRideFare(
                Session.activeBookingId,
                new ObjectId(Session.getCurrentUserId()),
                Session.activeDriverId != null ? new ObjectId(Session.activeDriverId) : null,
                breakdown.finalFare);
            handleResult(r, false);
        } catch (Exception e) {
            AlertUtil.showError("Payment Error", e.getMessage());
            payWalletBtn.setDisable(false); payCashBtn.setDisable(false);
            payWalletBtn.setText("💳 Pay via Wallet");
        }
    }

    @FXML
    private void onPayCash() {
        if (breakdown == null) return;
        if (!AlertUtil.showConfirmation("Pay Cash",
            "Confirm ₹" + fmt(breakdown.finalFare) + " cash payment to driver?")) return;
        payWalletBtn.setDisable(true); payCashBtn.setDisable(true);
        try {
            PaymentResult r = ledger.processCashRide(
                Session.activeBookingId,
                new ObjectId(Session.getCurrentUserId()),
                Session.activeDriverId != null ? new ObjectId(Session.activeDriverId) : null,
                breakdown.finalFare);
            handleResult(r, true);
        } catch (Exception e) {
            AlertUtil.showError("Payment Error", e.getMessage());
            payWalletBtn.setDisable(false); payCashBtn.setDisable(false);
        }
    }

    private void handleResult(PaymentResult r, boolean isCash) {
        AlertUtil.showPaymentResult(r);
        if (r.isSuccess()) {
            paymentDone = true;
            submitReviewBtn.setDisable(selectedStars == 0);
            payWalletBtn.setText(isCash ? "💵 Cash Recorded" : "✓ Paid");
            Session.clearBookingContext();
        } else {
            payWalletBtn.setDisable(false); payCashBtn.setDisable(false);
            payWalletBtn.setText("💳 Pay via Wallet");
            AlertUtil.showInlineError(paymentErrorLabel, r.getMessage());
        }
    }

    @FXML private void onStar1() { setStars(1); }
    @FXML private void onStar2() { setStars(2); }
    @FXML private void onStar3() { setStars(3); }
    @FXML private void onStar4() { setStars(4); }
    @FXML private void onStar5() { setStars(5); }

    private void setStars(int n) {
        selectedStars = n;
        Button[] s = {star1, star2, star3, star4, star5};
        for (int i = 0; i < 5; i++)
            s[i].setStyle("-fx-font-size:28px;-fx-background-color:transparent;-fx-text-fill:"
                + (i < n ? "#ff9f43" : "#2a2a50") + ";");
        ratingLabel.setText(n + " star" + (n > 1 ? "s" : ""));
        if (paymentDone) submitReviewBtn.setDisable(false);
    }

    private void buildReviewTags() {
        for (String tag : Review.USER_REVIEW_TAGS) {
            ToggleButton tb = new ToggleButton(tag);
            String base = "-fx-background-radius:20px;-fx-padding:6 14;-fx-cursor:hand;-fx-font-size:12px;";
            tb.setStyle(base + "-fx-background-color:rgba(124,58,237,0.1);-fx-text-fill:#a78bfa;");
            tb.selectedProperty().addListener((obs, o, v) -> tb.setStyle(base + (v
                ? "-fx-background-color:rgba(124,58,237,0.4);-fx-text-fill:white;"
                : "-fx-background-color:rgba(124,58,237,0.1);-fx-text-fill:#a78bfa;")));
            tagContainer.getChildren().add(tb);
        }
    }

    @FXML
    private void onSubmitReview() {
        if (selectedStars == 0) { AlertUtil.showInlineError(reviewStatusLabel, "Please select a rating."); return; }
        if (!paymentDone) { AlertUtil.showInlineError(reviewStatusLabel, "Complete payment first."); return; }
        List<String> tags = new ArrayList<>();
        for (javafx.scene.Node n : tagContainer.getChildren())
            if (n instanceof ToggleButton tb && tb.isSelected()) tags.add(tb.getText());
        Review review = Review.userReview(
            nvl(Session.getCurrentUserId(), ""),
            nvl(Session.activeDriverId, ""),
            nvl(Session.activeBookingId, ""),
            selectedStars, commentField.getText(), tags);
        String reviewError = reviewDAO.insertReview(review);
        if (reviewError == null) {
            if (Session.activeDriverId != null)
                try { driverDAO.updateRating(new ObjectId(Session.activeDriverId),
                    reviewDAO.calculateAverageRating(Session.activeDriverId)); } catch (Exception ignored) {}
            AlertUtil.showSuccess("Review submitted! Thank you.");
            Session.clearBookingContext();
            NavigationManager.navigateTo("/fxml/landing.fxml");
        } else {
            AlertUtil.showInlineError(reviewStatusLabel, reviewError != null ? reviewError : "Failed to submit review.");
        }
    }

    private void loadDriverInfo() {
        if (Session.activeDriverId == null) return;
        try {
            Driver d = driverDAO.findByUserId(new ObjectId(Session.activeDriverId));
            if (d != null) {
                driverNameLabel.setText(nvl(d.getFullName(), "—"));
                driverRatingLabel.setText("⭐ " + String.format("%.1f", d.getRating()));
            }
        } catch (Exception ignored) {}
    }

    private String fmt(double v) { return String.format("%.2f", v); }
    private String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
}
