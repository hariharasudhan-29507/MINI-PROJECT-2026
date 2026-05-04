package com.rento.controllers;

import com.rento.dao.DriverDAO;
import com.rento.models.Admin;
import com.rento.models.Driver;
import com.rento.navigation.NavigationManager;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminVerificationController — approve or reject driver applications.
 *
 * GATE: admin.canVerifyDocuments() must return null before any action.
 * Rejection requires a non-empty reason.
 * SUPER admins can approve; SUPPORT can only view.
 */
public class AdminVerificationController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField searchField;
    @FXML private Label pendingCountLabel, verifiedCountLabel, rejectedCountLabel;
    @FXML private TableView<Driver> driverTable;
    @FXML private TableColumn<Driver, String> colName, colEmail, colPhone, colLicense, colStatus, colJoined;
    @FXML private VBox actionPanel;
    @FXML private Label detailName, detailEmail, detailLicense, detailStatus;
    @FXML private VBox rejectReasonBox;
    @FXML private TextArea rejectReasonField;
    @FXML private Label rejectReasonError;
    @FXML private Label actionErrorLabel;
    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;

    private final DriverDAO driverDAO = new DriverDAO();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
    private List<Driver> allDrivers;
    private Driver selectedDriver;

    @FXML
    public void initialize() {
        // Gate: admin access
        Admin admin = Session.currentAdmin;
        if (admin == null) {
            AlertUtil.showGateError("Admin session expired. Please log in.");
            NavigationManager.navigateTo("/fxml/login.fxml");
            return;
        }
        String gateError = admin.canVerifyDocuments();
        if (gateError != null) {
            AlertUtil.showGateError(gateError);
            approveBtn.setDisable(true);
            rejectBtn.setDisable(true);
        }

        statusFilter.getItems().addAll("All", "PENDING", "VERIFIED", "REJECTED");
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilter());
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        setupTableColumns();
        loadDrivers();
        setupRowClick();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getFullName(), "—")));
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getEmail(), "—")));
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getPhoneNumber(), "—")));
        colLicense.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getLicenseNumber(), "—")));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getVerificationStatusString()));
        colJoined.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getCreatedAt() != null ? sdf.format(d.getValue().getCreatedAt()) : "—"));
    }

    private void loadDrivers() {
        allDrivers = driverDAO.findAll();
        applyFilter();
        updateCounts();
    }

    private void applyFilter() {
        if (allDrivers == null) return;
        String status = statusFilter.getValue();
        String q = searchField.getText().toLowerCase();
        List<Driver> filtered = allDrivers.stream()
            .filter(d -> "All".equals(status) || status.equals(d.getVerificationStatusString()))
            .filter(d -> q.isEmpty()
                || nvl(d.getFullName(), "").toLowerCase().contains(q)
                || nvl(d.getEmail(), "").toLowerCase().contains(q))
            .collect(Collectors.toList());
        driverTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void updateCounts() {
        if (allDrivers == null) return;
        long pending  = allDrivers.stream().filter(d -> "PENDING".equals(d.getVerificationStatusString())).count();
        long verified = allDrivers.stream().filter(d -> "VERIFIED".equals(d.getVerificationStatusString())).count();
        long rejected = allDrivers.stream().filter(d -> "REJECTED".equals(d.getVerificationStatusString())).count();
        pendingCountLabel.setText(pending + " Pending");
        verifiedCountLabel.setText(verified + " Verified");
        rejectedCountLabel.setText(rejected + " Rejected");
    }

    private void setupRowClick() {
        driverTable.getSelectionModel().selectedItemProperty().addListener((obs, o, driver) -> {
            if (driver == null) { hidePanel(); return; }
            selectedDriver = driver;
            detailName.setText(nvl(driver.getFullName(), "—"));
            detailEmail.setText(nvl(driver.getEmail(), "—"));
            detailLicense.setText(nvl(driver.getLicenseNumber(), "—"));
            detailStatus.setText(driver.getVerificationStatusString());
            rejectReasonBox.setVisible(false); rejectReasonBox.setManaged(false);
            rejectReasonField.clear();
            AlertUtil.clearInlineErrors(rejectReasonError, actionErrorLabel);
            actionPanel.setVisible(true); actionPanel.setManaged(true);
        });
    }

    @FXML
    private void onApprove() {
        if (selectedDriver == null) return;
        AlertUtil.clearInlineError(actionErrorLabel);

        Admin admin = Session.currentAdmin;
        if (admin != null) {
            String gate = admin.canVerifyDocuments();
            if (gate != null) { AlertUtil.showGateError(gate); return; }
        }

        if ("VERIFIED".equals(selectedDriver.getVerificationStatusString())) {
            AlertUtil.showInlineError(actionErrorLabel, "Driver is already verified.");
            return;
        }
        boolean confirmed = AlertUtil.showConfirmation("Approve Driver",
            "Approve " + selectedDriver.getFullName() + " as a verified Rento driver?");
        if (!confirmed) return;

        selectedDriver.setVerificationStatus("VERIFIED");
        selectedDriver.setBackgroundCheckDate(new java.util.Date());
        if (driverDAO.updateDriver(selectedDriver)) {
            AlertUtil.showSuccess(selectedDriver.getFullName() + " has been verified.");
            loadDrivers();
            hidePanel();
        } else {
            AlertUtil.showInlineError(actionErrorLabel, "Failed to update driver status. Try again.");
        }
    }

    @FXML
    private void onReject() {
        if (selectedDriver == null) return;
        AlertUtil.clearInlineErrors(rejectReasonError, actionErrorLabel);

        // Show reason box first
        if (!rejectReasonBox.isVisible()) {
            rejectReasonBox.setVisible(true); rejectReasonBox.setManaged(true);
            rejectBtn.setText("✕ Confirm Rejection");
            return;
        }

        // Validate reason
        String reason = rejectReasonField.getText().trim();
        if (reason.isEmpty()) {
            AlertUtil.showInlineError(rejectReasonError, "Rejection reason is required.");
            return;
        }
        if (reason.length() < 10) {
            AlertUtil.showInlineError(rejectReasonError, "Provide a more detailed reason (min 10 chars).");
            return;
        }

        boolean confirmed = AlertUtil.showConfirmation("Reject Application",
            "Reject " + selectedDriver.getFullName() + "'s application?\nReason: " + reason);
        if (!confirmed) return;

        selectedDriver.setVerificationStatus("REJECTED");
        selectedDriver.setHasRejectedDoc(true);
        if (driverDAO.updateDriver(selectedDriver)) {
            AlertUtil.showInfo("Application Rejected",
                selectedDriver.getFullName() + "'s application has been rejected.");
            loadDrivers();
            hidePanel();
        } else {
            AlertUtil.showInlineError(actionErrorLabel, "Failed to update. Try again.");
        }
    }

    @FXML private void onClosePanel() { hidePanel(); }

    private void hidePanel() {
        actionPanel.setVisible(false); actionPanel.setManaged(false);
        rejectReasonBox.setVisible(false); rejectReasonBox.setManaged(false);
        rejectBtn.setText("✕ Reject Application");
        driverTable.getSelectionModel().clearSelection();
        selectedDriver = null;
    }

    @FXML private void onRefresh() { loadDrivers(); }
    @FXML private void onNavDashboard() { NavigationManager.navigateTo("/fxml/admin_dashboard.fxml"); }

    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
