package com.rento.controllers;

import com.rento.dao.PaymentMethodDAO;
import com.rento.models.PaymentMethodProfile;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.bson.types.ObjectId;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * PaymentSetupController — shown immediately after registration.
 *
 * UX rules (same pattern as the rest of the platform):
 *  • Top banner  → "⚠ Fix the highlighted fields to continue."
 *  • Per-field   → specific error label directly below each input
 *  • On type     → red border + field error clear immediately
 *  • CVV         → verified for format only, then discarded (never stored)
 *  • Card number → stored as masked form only (e.g. **** **** **** 4242)
 */
public class PaymentSetupController implements Initializable {

    // ── Method selector ────────────────────────────────────────────────────
    @FXML private ComboBox<String> methodCombo;
    @FXML private Label setupTitleLabel;
    @FXML private Label setupSubtitleLabel;
    @FXML private Label contextBadgeLabel;

    // ── Section containers ─────────────────────────────────────────────────
    @FXML private VBox cardSection;
    @FXML private VBox upiSection;
    @FXML private VBox cashSection;

    // ── Card fields ────────────────────────────────────────────────────────
    @FXML private TextField     holderNameField;
    @FXML private Label         holderNameError;
    @FXML private TextField     cardNumberField;
    @FXML private Label         cardNumberError;
    @FXML private TextField     expiryField;
    @FXML private Label         expiryError;
    @FXML private PasswordField cvvField;
    @FXML private Label         cvvError;
    @FXML private ComboBox<String> cardProviderCombo;
    @FXML private TextField     nicknameField;

    // ── UPI fields ─────────────────────────────────────────────────────────
    @FXML private TextField     upiIdField;
    @FXML private Label         upiError;
    @FXML private TextField     upiHolderField;
    @FXML private Label         upiHolderError;
    @FXML private TextField     upiNicknameField;

    // ── Shared ─────────────────────────────────────────────────────────────
    @FXML private Label         errorLabel;    // top banner — SHORT only
    @FXML private Button        saveBtn;
    @FXML private Button        skipBtn;

    private final PaymentMethodDAO dao = new PaymentMethodDAO();

    private static final String STYLE_ERR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_OK  =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    // ──────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        methodCombo.setItems(FXCollections.observableArrayList(
            "Credit / Debit Card", "UPI"));
        methodCombo.getSelectionModel().selectFirst();
        methodCombo.setOnAction(e -> { switchSection(); clearAll(); });

        cardProviderCombo.setItems(FXCollections.observableArrayList(
            "Visa", "Mastercard", "RuPay", "American Express", "Other"));
        cardProviderCombo.getSelectionModel().selectFirst();

        switchSection();
        attachClearListeners();
        configureForContext();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Section switching
    // ──────────────────────────────────────────────────────────────────────
    private void switchSection() {
        String method = methodCombo.getValue();
        boolean isCard = "Credit / Debit Card".equals(method);
        boolean isUpi  = "UPI".equals(method);
        show(cardSection, isCard);
        show(upiSection,  isUpi);
        show(cashSection, false);
    }

    private static void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Save
    // ──────────────────────────────────────────────────────────────────────
    @FXML
    private void onSave() {
        clearAll();
        String method = methodCombo.getValue();
        boolean anyError = false;

        // ── Card validation ───────────────────────────────────────────────
        if ("Credit / Debit Card".equals(method)) {

            // Cardholder name — letters and spaces only
            String nameErr = validateCardHolderName(holderNameField.getText().trim());
            if (nameErr != null) { markField(holderNameField, holderNameError, nameErr); anyError = true; }

            // Card number — 16 digits, Luhn
            String cardErr = ValidationUtil.validateCardNumber(
                cardNumberField.getText().trim().replaceAll("[\\s\\-]", ""));
            if (cardErr != null) { markField(cardNumberField, cardNumberError, cardErr); anyError = true; }

            // Expiry — MM/YY, not expired
            String expErr = ValidationUtil.validateExpiryDate(expiryField.getText().trim());
            if (expErr != null) { markField(expiryField, expiryError, expErr); anyError = true; }

            // CVV — 3 digits (verified only, never stored)
            String cvvErr = ValidationUtil.validateCVV(cvvField.getText().trim());
            if (cvvErr != null) { markField(cvvField, cvvError, cvvErr); anyError = true; }

            if (anyError) { showBanner("⚠  Fix the highlighted fields to continue."); return; }

            // Build profile — mask card number, discard CVV
            String rawCard = cardNumberField.getText().trim().replaceAll("[\\s\\-]", "");
            PaymentMethodProfile profile = new PaymentMethodProfile();
            profile.setMethodType(PaymentMethodProfile.MethodType.CREDIT_CARD);
            profile.setHolderName(holderNameField.getText().trim());
            profile.setMaskedReference(ValidationUtil.maskCardNumber(rawCard));
            profile.setProviderName(cardProviderCombo.getValue());
            profile.setNickname(nicknameField.getText().trim().isEmpty()
                ? cardProviderCombo.getValue() + " Card"
                : nicknameField.getText().trim());
            profile.setProfileName(profile.getNickname());
            profile.setPreferred(true);
            saveAndNavigate(profile);

        // ── UPI validation ────────────────────────────────────────────────
        } else if ("UPI".equals(method)) {

            String upiErr = ValidationUtil.validateUpiId(upiIdField.getText().trim());
            if (upiErr != null) { markField(upiIdField, upiError, upiErr); anyError = true; }

            String holderErr = validateCardHolderName(upiHolderField.getText().trim());
            if (holderErr != null) { markField(upiHolderField, upiHolderError,
                holderErr.replace("Cardholder", "Account holder")); anyError = true; }

            if (anyError) { showBanner("⚠  Fix the highlighted fields to continue."); return; }

            PaymentMethodProfile profile = new PaymentMethodProfile();
            profile.setMethodType(PaymentMethodProfile.MethodType.UPI);
            profile.setHolderName(upiHolderField.getText().trim());
            profile.setMaskedReference(ValidationUtil.maskUpiId(upiIdField.getText().trim()));
            profile.setNickname(upiNicknameField.getText().trim().isEmpty()
                ? "UPI — " + upiIdField.getText().trim()
                : upiNicknameField.getText().trim());
            profile.setProfileName(profile.getNickname());
            profile.setPreferred(true);
            saveAndNavigate(profile);

        // ── Cash on delivery — nothing to validate ─────────────────────────
        } else {
            PaymentMethodProfile profile = new PaymentMethodProfile();
            profile.setMethodType(PaymentMethodProfile.MethodType.CASH_ON_DELIVERY);
            profile.setNickname("Cash on Delivery");
            profile.setProfileName("Cash on Delivery");
            profile.setHolderName("");
            profile.setMaskedReference("Cash");
            profile.setPreferred(true);
            saveAndNavigate(profile);
        }
    }

    private void saveAndNavigate(PaymentMethodProfile profile) {
        // Attach user ID from post-registration session token
        String pendingId = Session.pendingRegisteredUserId;
        if (pendingId != null && !pendingId.isBlank()) {
            try { profile.setUserId(new ObjectId(pendingId)); }
            catch (Exception ignored) {}
        } else if (SessionManager.getInstance().getCurrentUser() != null) {
            profile.setUserId(SessionManager.getInstance().getCurrentUser().getId());
        }

        if (profile.getUserId() != null && profile.isPreferred()) {
            for (PaymentMethodProfile existing : dao.findByUser(profile.getUserId())) {
                if (existing.isPreferred()) {
                    existing.setPreferred(false);
                    dao.update(existing);
                }
            }
        }

        boolean saved = dao.insert(profile);
        Session.pendingRegisteredUserId = null;   // consumed

        if (saved) {
            showSuccessAndContinue("Payment method saved! You can manage it from your profile.");
        } else {
            // Non-blocking — still let the user proceed
            showSuccessAndContinue("Account ready! (Payment profile could not be saved — add it later from your profile.)");
        }
    }

    private void showSuccessAndContinue(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("All Set!");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
        String nextPage = resolveReturnPage();
        clearPendingContext();
        NavigationManager.navigateTo(nextPage);
    }

    @FXML
    private void onSkip() {
        if (isPaymentRequiredContext()) {
            NavigationManager.navigateTo(resolveReturnPage());
            return;
        }
        clearPendingContext();
        NavigationManager.navigateTo("/fxml/login.fxml");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cardholder name validation (letters, spaces, hyphens — no numbers/symbols)
    // ──────────────────────────────────────────────────────────────────────
    private static String validateCardHolderName(String name) {
        if (name == null || name.isBlank())
            return "Cardholder name is required";
        if (name.length() < 2)
            return "Cardholder name is too short — enter at least 2 characters";
        if (name.length() > 50)
            return "Cardholder name must not exceed 50 characters";
        if (name.matches(".*[0-9].*"))
            return "Cardholder name cannot contain numbers — letters and spaces only";
        if (!name.matches("[a-zA-Z]+([ \\-][a-zA-Z]+)*"))
            return "Cardholder name cannot contain special characters — letters and spaces only";
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Live clear listeners
    // ──────────────────────────────────────────────────────────────────────
    private void attachClearListeners() {
        attach(holderNameField, holderNameError);
        attach(cardNumberField, cardNumberError);
        attach(expiryField,     expiryError);
        cvvField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(cvvField); clearFE(cvvError); }
        });
        attach(upiIdField,    upiError);
        attach(upiHolderField, upiHolderError);
    }

    private void attach(TextField field, Label lbl) {
        field.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(field); clearFE(lbl); }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Field mark / clear helpers
    // ──────────────────────────────────────────────────────────────────────
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

    private void clearH(Control c)    { c.setStyle(STYLE_OK); Tooltip.install(c, null); }
    private void clearFE(Label lbl)   { if (lbl == null) return; lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false); }

    private void showBanner(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearAll() {
        // Banner
        errorLabel.setText(""); errorLabel.setVisible(false); errorLabel.setManaged(false);
        // Card
        clearH(holderNameField); clearFE(holderNameError);
        clearH(cardNumberField); clearFE(cardNumberError);
        clearH(expiryField);     clearFE(expiryError);
        clearH(cvvField);        clearFE(cvvError);
        // UPI
        clearH(upiIdField);     clearFE(upiError);
        clearH(upiHolderField); clearFE(upiHolderError);
    }

    private void configureForContext() {
        String source = Session.pendingPaymentSetupSource;
        if ("BOOKING".equals(source)) {
            setupTitleLabel.setText("Set Up Payment Before Booking");
            setupSubtitleLabel.setText("Save one payment profile now. Your booking checkout will reuse it inside the payment flow.");
            contextBadgeLabel.setText("Booking Flow");
            skipBtn.setVisible(false);
            skipBtn.setManaged(false);
        } else if ("RENTAL".equals(source)) {
            setupTitleLabel.setText("Set Up Payment Before Renting");
            setupSubtitleLabel.setText("Rental requests now depend on a saved payment profile so the marketplace and payment records stay in sync.");
            contextBadgeLabel.setText("Rental Flow");
            skipBtn.setVisible(false);
            skipBtn.setManaged(false);
        } else {
            setupTitleLabel.setText("Finish Setting Up");
            setupSubtitleLabel.setText("Add a payment method now for faster booking and rental checkout, or skip and do it when needed.");
            contextBadgeLabel.setText("New Account");
            skipBtn.setVisible(true);
            skipBtn.setManaged(true);
        }
    }

    private boolean isPaymentRequiredContext() {
        return "BOOKING".equals(Session.pendingPaymentSetupSource)
            || "RENTAL".equals(Session.pendingPaymentSetupSource);
    }

    private String resolveReturnPage() {
        String page = Session.pendingPaymentSetupReturnPage;
        return page != null && !page.isBlank() ? page : "/fxml/login.fxml";
    }

    private void clearPendingContext() {
        Session.pendingRegisteredUserId = null;
        Session.pendingPaymentSetupSource = null;
        Session.pendingPaymentSetupReturnPage = null;
    }
}
