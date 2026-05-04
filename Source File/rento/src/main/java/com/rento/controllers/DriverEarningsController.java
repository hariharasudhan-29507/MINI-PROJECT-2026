package com.rento.controllers;

import com.rento.dao.DriverDAO;
import com.rento.dao.TransactionDAO;
import com.rento.models.Driver;
import com.rento.models.Transaction;
import com.rento.navigation.NavigationManager;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * DriverEarningsController — earnings overview + transaction ledger.
 * Withdraw GATE: pendingEarnings must be > 0, driver must have bank details.
 */
public class DriverEarningsController {

    @FXML private Label driverNameLabel;
    @FXML private Label todayEarning, todayRides;
    @FXML private Label weekEarning;
    @FXML private Label totalEarning, totalRides;
    @FXML private Label pendingEarning;
    @FXML private Label ratingLabel;
    @FXML private Label completedRidesLabel;
    @FXML private Label onlineStatusLabel;
    @FXML private Button withdrawBtn;
    @FXML private TableView<Transaction> txTable;
    @FXML private TableColumn<Transaction, String> colDate, colType, colPurpose, colAmount, colBalance, colStatus;

    private final DriverDAO driverDAO = new DriverDAO();
    private final TransactionDAO txDAO = new TransactionDAO();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy, hh:mm a");

    @FXML
    public void initialize() {
        if (Session.activeDriverObjectId == null) {
            AlertUtil.showGateError("Driver session not found. Please log in.");
            NavigationManager.navigateTo("/fxml/login.fxml");
            return;
        }
        setupTableColumns();
        loadData();
    }

    private void loadData() {
        try {
            Driver driver = driverDAO.findById(Session.activeDriverObjectId);
            if (driver == null) return;

            driverNameLabel.setText("Welcome, " + nvl(driver.getFullName(), "Driver"));
            ratingLabel.setText("⭐ " + String.format("%.1f", driver.getRating()));
            completedRidesLabel.setText(String.valueOf(driver.getTotalRidesCompleted()));
            totalRides.setText(driver.getTotalRidesCompleted() + " total rides");
            totalEarning.setText("₹" + fmt(driver.getTotalEarnings()));
            pendingEarning.setText("₹" + fmt(driver.getPendingEarnings()));
            onlineStatusLabel.setText(driver.isOnline() ? "ONLINE" : "OFFLINE");
            onlineStatusLabel.setStyle(driver.isOnline()
                ? "-fx-text-fill: #06d6a0; -fx-font-weight: 700;"
                : "-fx-text-fill: #f72585; -fx-font-weight: 700;");

            // Transaction history
            String driverIdHex = Session.activeDriverObjectId.toHexString();
            List<Transaction> txList = txDAO.findByActor(driverIdHex);

            // Today / week sums
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            Date todayStart = today.getTime();
            Calendar weekAgo = Calendar.getInstance();
            weekAgo.add(Calendar.DAY_OF_YEAR, -7);
            Date weekStart = weekAgo.getTime();

            double todaySum = txList.stream()
                .filter(t -> t.getTimestamp() != null && t.getTimestamp().after(todayStart))
                .filter(t -> Transaction.TransactionType.CREDIT == t.getType())
                .mapToDouble(Transaction::getAmount).sum();
            double weekSum = txList.stream()
                .filter(t -> t.getTimestamp() != null && t.getTimestamp().after(weekStart))
                .filter(t -> Transaction.TransactionType.CREDIT == t.getType())
                .mapToDouble(Transaction::getAmount).sum();
            long todayCount = txList.stream()
                .filter(t -> t.getTimestamp() != null && t.getTimestamp().after(todayStart))
                .filter(t -> Transaction.TransactionType.CREDIT == t.getType()).count();

            todayEarning.setText("₹" + fmt(todaySum));
            todayRides.setText(todayCount + " rides today");
            weekEarning.setText("₹" + fmt(weekSum));

            txTable.setItems(FXCollections.observableArrayList(txList));
        } catch (Exception e) {
            System.err.println("[DriverEarningsController] " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getTimestamp() != null ? sdf.format(d.getValue().getTimestamp()) : "—"));
        colType.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getType() != null ? d.getValue().getType().name() : "—"));
        colPurpose.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getPurpose() != null ? d.getValue().getPurpose().name() : "—"));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
            (Transaction.TransactionType.CREDIT == d.getValue().getType() ? "+" : "-")
            + "₹" + fmt(d.getValue().getAmount())));
        colBalance.setCellValueFactory(d -> new SimpleStringProperty("₹" + fmt(d.getValue().getNewBalance())));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getStatus() != null ? d.getValue().getStatus().name() : "—"));
    }

    @FXML
    private void onWithdraw() {
        try {
            Driver driver = driverDAO.findById(Session.activeDriverObjectId);
            if (driver == null) return;

            // GATE: must have pending earnings
            String gateError = driver.canWithdraw();
            if (gateError != null) {
                AlertUtil.showGateError(gateError);
                return;
            }

            boolean confirmed = AlertUtil.showConfirmation("Withdraw Earnings",
                "Transfer ₹" + fmt(driver.getPendingEarnings())
                    + " to your bank account (" + nvl(driver.getBankName(), "—") + ")?\n\n"
                    + "Processing time: 1-2 business days.");
            if (!confirmed) return;

            // Write withdrawal transaction
            Transaction withdrawal = Transaction.debit(
                Session.activeDriverObjectId.toHexString(),
                Transaction.ActorType.DRIVER,
                driver.getPendingEarnings(),
                Transaction.Purpose.WITHDRAWAL,
                null,
                driver.getPendingEarnings()
            );
            if (txDAO.insertTransaction(withdrawal)) {
                txDAO.markSuccess(withdrawal.getId());
                driverDAO.clearPendingEarnings(Session.activeDriverObjectId);
                AlertUtil.showSuccess("Withdrawal of ₹" + fmt(driver.getPendingEarnings())
                    + " initiated! Funds arrive in 1-2 business days.");
                loadData();
            } else {
                AlertUtil.showError("Withdrawal Failed", "Could not process withdrawal. Please try again.");
            }
        } catch (Exception e) {
            AlertUtil.showError("Error", e.getMessage());
        }
    }

    @FXML private void onNavDashboard() { NavigationManager.navigateTo("/fxml/driver_dashboard.fxml"); }

    private String fmt(double v) { return String.format("%.2f", v); }
    private String nvl(String s, String d) { return (s != null && !s.isBlank()) ? s : d; }
}
