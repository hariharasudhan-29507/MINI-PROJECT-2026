package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.services.LedgerPaymentService;
import com.rento.utils.AlertUtil;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import com.rento.dao.UserDAO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.bson.types.ObjectId;

/**
 * WalletTopUpController — Form UX:
 *   • Top banner → "⚠ Fix the highlighted fields to continue."
 *   • Each invalid field → red border + specific error label beneath it
 *   • On type → border + field error clears immediately
 */
public class WalletTopUpController {

    @FXML private Label         balanceLabel;

    @FXML private TextField     amountField;
    @FXML private Label         amountError;          // below amountField

    @FXML private ComboBox<String> methodCombo;

    @FXML private VBox          cardFieldsBox;
    @FXML private TextField     cardNumberField;
    @FXML private Label         cardNumberError;      // below cardNumberField
    @FXML private TextField     expiryField;
    @FXML private Label         expiryError;
    @FXML private TextField     cvvField;
    @FXML private Label         cvvError;
    @FXML private TextField     cardHolderField;
    @FXML private Label         cardHolderError;

    @FXML private VBox          upiFieldBox;
    @FXML private TextField     upiField;
    @FXML private Label         upiError;

    @FXML private Label         formErrorLabel;       // top banner — SHORT only
    @FXML private VBox          processingBox;
    @FXML private ProgressBar   processingBar;
    @FXML private Button        topUpBtn;

    private final LedgerPaymentService ledger = new LedgerPaymentService();

    private static final String STYLE_ERR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_OK  =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    @FXML
    public void initialize() {
        balanceLabel.setText("₹" + String.format("%.2f", Session.getWalletBalance()));
        methodCombo.getItems().addAll("Rento Wallet (Simulated)", "Credit / Debit Card", "UPI");
        methodCombo.getSelectionModel().selectFirst();
        methodCombo.setOnAction(e -> onMethodChanged());

        // Clear-on-type for all fields
        attachClear(amountField,     amountError);
        attachClear(cardNumberField, cardNumberError);
        attachClear(expiryField,     expiryError);
        attachClear(cvvField,        cvvError);
        attachClear(cardHolderField, cardHolderError);
        attachClear(upiField,        upiError);
    }

    private void onMethodChanged() {
        String method = methodCombo.getValue();
        boolean isCard = "Credit / Debit Card".equals(method);
        boolean isUpi  = "UPI".equals(method);
        cardFieldsBox.setVisible(isCard); cardFieldsBox.setManaged(isCard);
        upiFieldBox.setVisible(isUpi);    upiFieldBox.setManaged(isUpi);
        clearAll();
    }

    @FXML private void onQuick200()  { amountField.setText("200");  clearH(amountField); clearFieldErr(amountError); }
    @FXML private void onQuick500()  { amountField.setText("500");  clearH(amountField); clearFieldErr(amountError); }
    @FXML private void onQuick1000() { amountField.setText("1000"); clearH(amountField); clearFieldErr(amountError); }
    @FXML private void onQuick2000() { amountField.setText("2000"); clearH(amountField); clearFieldErr(amountError); }

    @FXML
    private void onTopUp() {
        clearAll();
        boolean anyError = false;
        String method = methodCombo.getValue();

        // Amount
        String amtErr = ValidationUtil.validateTopUpAmount(amountField.getText().trim());
        if (amtErr != null) { markField(amountField, amountError, amtErr); anyError = true; }

        // Card fields
        if ("Credit / Debit Card".equals(method)) {
            String cardErr = ValidationUtil.validateCardNumber(
                cardNumberField.getText().trim().replaceAll("\\s", ""));
            if (cardErr != null) { markField(cardNumberField, cardNumberError, cardErr); anyError = true; }

            String expErr = ValidationUtil.validateExpiryDate(expiryField.getText().trim());
            if (expErr != null) { markField(expiryField, expiryError, expErr); anyError = true; }

            String cvvErr = ValidationUtil.validateCVV(cvvField.getText().trim());
            if (cvvErr != null) { markField(cvvField, cvvError, cvvErr); anyError = true; }

            String nameErr = ValidationUtil.validateName(cardHolderField.getText().trim());
            if (nameErr != null) { markField(cardHolderField, cardHolderError, nameErr); anyError = true; }
        }

        // UPI
        if ("UPI".equals(method)) {
            String upiErr = ValidationUtil.validateUpiId(upiField.getText().trim());
            if (upiErr != null) { markField(upiField, upiError, upiErr); anyError = true; }
        }

        if (anyError) {
            showBanner("⚠  Fix the highlighted fields to continue.");
            return;
        }

        // All valid — process
        setProcessing(true);
        String userId = Session.getCurrentUserId();
        if (userId == null) {
            AlertUtil.showGateError("Session expired. Please log in again.");
            setProcessing(false); return;
        }

        double amount = Double.parseDouble(amountField.getText().trim());
        String error  = ledger.topUpWallet(new ObjectId(userId), amount);
        setProcessing(false);

        if (error != null) {
            markField(amountField, amountError, error);
            showBanner("⚠  Fix the highlighted fields to continue.");
        } else {
            UserDAO userDAO = new UserDAO();
            try {
                var user = userDAO.findById(new ObjectId(userId));
                if (user != null) Session.currentUser = user;
            } catch (Exception ignored) {}
            AlertUtil.showSuccess("₹" + String.format("%.0f", amount) + " added to your wallet!");
            NavigationManager.goBack();
        }
    }

    // -----------------------------------------------------------------------
    // Field mark / clear helpers
    // -----------------------------------------------------------------------

    private void markField(Control field, Label lbl, String message) {
        field.setStyle(STYLE_ERR);
        if (lbl != null) {
            lbl.setText(message);
            lbl.setStyle("-fx-text-fill: #f72585; -fx-font-size: 11px;");
            lbl.setVisible(true);
            lbl.setManaged(true);
        }
        Tooltip tip = new Tooltip(message);
        tip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(field, tip);
    }

    private void clearH(Control c)       { c.setStyle(STYLE_OK); Tooltip.install(c, null); }
    private void clearFieldErr(Label lbl) { if (lbl == null) return; lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false); }

    private void attachClear(TextField field, Label lbl) {
        field.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(field); clearFieldErr(lbl); }
        });
    }

    private void showBanner(String msg) {
        if (formErrorLabel == null) return;
        formErrorLabel.setText(msg);
        formErrorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void clearAll() {
        if (formErrorLabel != null) { formErrorLabel.setText(""); formErrorLabel.setVisible(false); formErrorLabel.setManaged(false); }
        clearH(amountField);     clearFieldErr(amountError);
        clearH(cardNumberField); clearFieldErr(cardNumberError);
        clearH(expiryField);     clearFieldErr(expiryError);
        clearH(cvvField);        clearFieldErr(cvvError);
        clearH(cardHolderField); clearFieldErr(cardHolderError);
        clearH(upiField);        clearFieldErr(upiError);
    }

    private void setProcessing(boolean on) {
        if (processingBox != null) { processingBox.setVisible(on); processingBox.setManaged(on); }
        topUpBtn.setDisable(on);
    }

    @FXML private void onCancel() { NavigationManager.goBack(); }
}
