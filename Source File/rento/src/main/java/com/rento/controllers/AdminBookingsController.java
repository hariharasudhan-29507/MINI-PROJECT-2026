package com.rento.controllers;

import com.rento.dao.BookingDAO;
import com.rento.dao.PaymentDAO;
import com.rento.models.Admin;
import com.rento.models.Booking;
import com.rento.models.Payment;
import com.rento.navigation.NavigationManager;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminBookingsController — admin view of all bookings with filters and cash confirmation.
 *
 * GATE: admin must have SUPER or FINANCE role to confirm cash payments.
 */
public class AdminBookingsController {

    @FXML private ComboBox<String> statusFilter, categoryFilter;
    @FXML private DatePicker dateFromPicker, dateToPicker;
    @FXML private TextField searchField;
    @FXML private Label bookingCountLabel;
    @FXML private Label pendingBadge, activeBadge, completedBadge, cancelledBadge;
    @FXML private TableView<Booking> bookingsTable;
    @FXML private TableColumn<Booking, String> colId, colUser, colDriver, colPickup, colDrop, colFare, colStatus, colDate;
    @FXML private VBox detailPanel;
    @FXML private Label detailId, detailUser, detailDriver, detailPickup, detailDrop, detailFare, detailStatus, detailPayment;
    @FXML private HBox cashVerifyBox;
    @FXML private Label cashVerifyError;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy, hh:mm a");
    private List<Booking> allBookings;
    private Booking selectedBooking;

    @FXML
    public void initialize() {
        statusFilter.getItems().addAll("All", "PENDING", "ACCEPTED", "ACTIVE", "COMPLETED", "CANCELLED", "FLAGGED");
        categoryFilter.getItems().addAll("All", "MINI", "PRIME", "SUV", "LUXURY");
        statusFilter.getSelectionModel().selectFirst();
        categoryFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilter());
        categoryFilter.setOnAction(e -> applyFilter());
        dateFromPicker.setOnAction(e -> applyFilter());
        dateToPicker.setOnAction(e -> applyFilter());
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        setupTableColumns();
        loadBookings();
        setupRowClick();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getId() != null ? d.getValue().getId().toHexString().substring(0, 12) + "…" : "—"));
        colUser.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getUserName(), "—")));
        colDriver.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getAssignedDriverName(), "—")));
        colPickup.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getPickupAddress(), "—")));
        colDrop.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getDropAddress(), "—")));
        colFare.setCellValueFactory(d -> new SimpleStringProperty("₹" + String.format("%.0f", d.getValue().getFinalFare())));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStatusString(), "—")));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getCreatedAt() != null ? sdf.format(d.getValue().getCreatedAt()) : "—"));
    }

    private void loadBookings() {
        allBookings = bookingDAO.findAll();
        applyFilter();
        updateBadges();
    }

    private void applyFilter() {
        if (allBookings == null) return;
        String status   = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query    = searchField.getText().toLowerCase();
        LocalDate from  = dateFromPicker.getValue();
        LocalDate to    = dateToPicker.getValue();

        List<Booking> filtered = allBookings.stream()
            .filter(b -> "All".equals(status)   || status.equals(b.getStatusString()))
            .filter(b -> "All".equals(category) || category.equals(b.getVehicleCategory()))
            .filter(b -> {
                if (from == null && to == null) return true;
                if (b.getCreatedAt() == null) return false;
                LocalDate d = b.getCreatedAt().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
            })
            .filter(b -> query.isEmpty()
                || nvl(b.getUserName(), "").toLowerCase().contains(query)
                || nvl(b.getAssignedDriverName(), "").toLowerCase().contains(query)
                || nvl(b.getPickupAddress(), "").toLowerCase().contains(query))
            .collect(Collectors.toList());

        bookingsTable.setItems(FXCollections.observableArrayList(filtered));
        bookingCountLabel.setText(filtered.size() + " booking" + (filtered.size() != 1 ? "s" : ""));
    }

    private void updateBadges() {
        if (allBookings == null) return;
        pendingBadge.setText("Pending: " + count("PENDING"));
        activeBadge.setText("Active: " + (count("ACTIVE") + count("ACCEPTED")));
        completedBadge.setText("Completed: " + count("COMPLETED"));
        cancelledBadge.setText("Cancelled: " + count("CANCELLED"));
    }

    private long count(String status) {
        return allBookings.stream().filter(b -> status.equals(b.getStatusString())).count();
    }

    private void setupRowClick() {
        bookingsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, booking) -> {
            if (booking == null) { hideDetail(); return; }
            selectedBooking = booking;
            detailId.setText(booking.getId() != null ? booking.getId().toHexString() : "—");
            detailUser.setText(nvl(booking.getUserName(), "—"));
            detailDriver.setText(nvl(booking.getAssignedDriverName(), "—"));
            detailPickup.setText(nvl(booking.getPickupAddress(), "—"));
            detailDrop.setText(nvl(booking.getDropAddress(), "—"));
            detailFare.setText("₹" + String.format("%.2f", booking.getFinalFare()));
            detailStatus.setText(nvl(booking.getStatusString(), "—"));
            detailPayment.setText(nvl(booking.getPaymentMethod(), "—"));

            // Show cash verify button only for pending cash payments
            boolean cashPending = booking.isCashPaymentPending() && !booking.isPaidVerified();
            cashVerifyBox.setVisible(cashPending); cashVerifyBox.setManaged(cashPending);
            AlertUtil.clearInlineError(cashVerifyError);

            detailPanel.setVisible(true); detailPanel.setManaged(true);
        });
    }

    @FXML
    private void onConfirmCash() {
        if (selectedBooking == null) return;
        AlertUtil.clearInlineError(cashVerifyError);

        // Gate: SUPER or FINANCE role
        Admin admin = Session.currentAdmin;
        if (admin != null) {
            String gate = admin.canVerifyDocuments(); // SUPER gate reused
            if (gate != null) { AlertUtil.showGateError(gate); return; }
        }

        boolean confirmed = AlertUtil.showConfirmation("Confirm Cash",
            "Mark cash payment of ₹" + String.format("%.2f", selectedBooking.getFinalFare())
                + " as received for booking " + selectedBooking.getId().toHexString().substring(0, 8) + "?");
        if (!confirmed) return;

        selectedBooking.setPaidVerified(true);
        selectedBooking.setCashPaymentPending(false);

        // Find and mark the payment record
        try {
            List<Payment> payments = paymentDAO.findAllByBooking(selectedBooking.getId());
            for (Payment p : payments) {
                if (Payment.PaymentMethod.CASH_ON_DELIVERY == p.getPaymentMethod()) {
                    p.setCashVerified(true);
                    p.setCashVerifiedBy(admin != null ? admin.getUsername() : "admin");
                    p.setCashVerifiedAt(new Date());
                    p.setStatus(Payment.PaymentStatus.COMPLETED);
                    paymentDAO.updatePayment(p);
                }
            }
        } catch (Exception ignored) {}

        if (bookingDAO.updateBooking(selectedBooking)) {
            AlertUtil.showSuccess("Cash payment confirmed.");
            loadBookings();
            hideDetail();
        } else {
            AlertUtil.showInlineError(cashVerifyError, "Failed to update booking. Try again.");
        }
    }

    @FXML
    private void onExport() {
        AlertUtil.showInfo("Export", "CSV export will be available in Phase 5 (Admin Completion).");
    }

    @FXML private void onCloseDetail() { hideDetail(); }
    @FXML private void onRefresh() { loadBookings(); }
    @FXML private void onNavDashboard() { NavigationManager.navigateTo("/fxml/admin_dashboard.fxml"); }

    private void hideDetail() {
        detailPanel.setVisible(false); detailPanel.setManaged(false);
        bookingsTable.getSelectionModel().clearSelection();
        selectedBooking = null;
    }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
