package com.rento.controllers;

import com.rento.dao.BookingDAO;

import com.rento.models.Booking;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.utils.Session;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RidesHistoryController — user ride history with filter and summary stats.
 */
public class RidesHistoryController {

    @FXML private Label tripCountLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField searchField;
    @FXML private TableView<Booking> ridesTable;
    @FXML private TableColumn<Booking, String> colDate, colPickup, colDrop, colCategory, colFare, colStatus, colRating;
    @FXML private VBox detailPanel;
    @FXML private Label detailPickup, detailDrop, detailFare, detailStatus, detailDriver;
    @FXML private Label totalSpentLabel, completedLabel, avgRatingLabel;

    private final BookingDAO bookingDAO = new BookingDAO();
    private List<Booking> allBookings;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy, hh:mm a");

    @FXML
    public void initialize() {
        statusFilter.getItems().addAll("All", "COMPLETED", "CANCELLED", "PENDING", "ACTIVE");
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilter());
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        setupTableColumns();
        loadBookings();
        setupRowClickDetail();
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(d -> {
            var b = d.getValue();
            String dt = b.getCreatedAt() != null ? sdf.format(b.getCreatedAt()) : "—";
            return new javafx.beans.property.SimpleStringProperty(dt);
        });
        colPickup.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(nvl(d.getValue().getPickupAddress(), "—")));
        colDrop.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(nvl(d.getValue().getDropAddress(), "—")));
        colCategory.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(nvl(d.getValue().getVehicleCategory(), "—")));
        colFare.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty("₹" + String.format("%.0f", d.getValue().getFinalFare())));
        colStatus.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(nvl(d.getValue().getStatusString(), "—")));
        colRating.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue().getUserRatingGiven() > 0
                ? "⭐ " + d.getValue().getUserRatingGiven() : "—"));
    }

    private void loadBookings() {
        String userId = Session.getCurrentUserId();
        if (userId == null && SessionManager.getInstance().getCurrentUser() != null) {
            userId = SessionManager.getInstance().getCurrentUser().getId().toHexString();
        }
        if (userId == null) return;
        allBookings = bookingDAO.findByUser(new org.bson.types.ObjectId(userId));
        applyFilter();
        updateStats();
    }

    private void applyFilter() {
        if (allBookings == null) return;
        String status = statusFilter.getValue();
        String query  = searchField.getText().toLowerCase();

        List<Booking> filtered = allBookings.stream()
            .filter(b -> "All".equals(status) || status.equals(b.getStatusString()))
            .filter(b -> query.isEmpty()
                || nvl(b.getPickupAddress(), "").toLowerCase().contains(query)
                || nvl(b.getDropAddress(), "").toLowerCase().contains(query))
            .collect(Collectors.toList());

        ridesTable.setItems(FXCollections.observableArrayList(filtered));
        tripCountLabel.setText(filtered.size() + " trip" + (filtered.size() != 1 ? "s" : ""));
    }

    private void updateStats() {
        if (allBookings == null) return;
        double totalSpent = allBookings.stream()
            .filter(b -> "COMPLETED".equals(b.getStatusString()))
            .mapToDouble(Booking::getFinalFare).sum();
        long completed = allBookings.stream().filter(b -> "COMPLETED".equals(b.getStatusString())).count();
        double avgRating = allBookings.stream()
            .filter(b -> b.getUserRatingGiven() > 0)
            .mapToInt(Booking::getUserRatingGiven).average().orElse(5.0);

        totalSpentLabel.setText("₹" + String.format("%.0f", totalSpent));
        completedLabel.setText(String.valueOf(completed));
        avgRatingLabel.setText(String.format("%.1f ★", avgRating));
    }

    private void setupRowClickDetail() {
        ridesTable.getSelectionModel().selectedItemProperty().addListener((obs, o, booking) -> {
            if (booking == null) { detailPanel.setVisible(false); detailPanel.setManaged(false); return; }
            detailPanel.setVisible(true); detailPanel.setManaged(true);
            detailPickup.setText(nvl(booking.getPickupAddress(), "—"));
            detailDrop.setText(nvl(booking.getDropAddress(), "—"));
            detailFare.setText("₹" + String.format("%.2f", booking.getFinalFare()));
            detailStatus.setText(nvl(booking.getStatusString(), "—"));
            detailDriver.setText(nvl(booking.getAssignedDriverName(), "—"));
        });
    }

    @FXML private void onCloseDetail() {
        detailPanel.setVisible(false); detailPanel.setManaged(false);
        ridesTable.getSelectionModel().clearSelection();
    }

    @FXML private void onRefresh() { loadBookings(); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
