package com.rento.controllers;

import com.rento.dao.UserDAO;
import com.rento.models.User;
import com.rento.navigation.NavigationManager;
import com.rento.services.AuthService;
import com.rento.utils.CaptchaGenerator;
import com.rento.utils.Session;
import com.rento.utils.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * RegisterController — Form UX:
 *   • Top label → short: "⚠ Fix the highlighted fields to continue."
 *   • Each invalid field → red border + small specific error label beneath it
 *   • As user types into any red field → border clears + its error label clears
 */
public class RegisterController implements Initializable {

    // --- Fields -----------------------------------------------------------
    @FXML private TextField     nameField;
    @FXML private Label         nameError;          // small label below nameField
    @FXML private TextField     emailField;
    @FXML private Label         emailError;
    @FXML private TextField     phoneField;
    @FXML private Label         phoneError;
    @FXML private TextField     ageField;
    @FXML private Label         ageError;
    @FXML private PasswordField passwordField;
    @FXML private Label         passwordStrength;
    @FXML private Label         passwordError;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         confirmError;
    @FXML private ComboBox<String> roleCombo;
    @FXML private TextField     captchaAnswer;
    @FXML private Label         captchaError;
    @FXML private Label         captchaQuestion;

    /** Top-level banner — SHORT generic text only. */
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();
    private String[] currentCaptcha;

    private static final String STYLE_ERR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_OK  =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    // -----------------------------------------------------------------------
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        roleCombo.setItems(FXCollections.observableArrayList("User", "Driver", "Supplier"));
        roleCombo.setValue("User");
        generateCaptcha();
        passwordField.textProperty().addListener((obs, o, n) -> updatePasswordStrength(n));
        attachClearOnType();
    }

    // -----------------------------------------------------------------------
    // Per-field "clear on type" listeners
    // -----------------------------------------------------------------------
    private void attachClearOnType() {
        nameField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(nameField); clearFieldErr(nameError); }
        });
        emailField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(emailField); clearFieldErr(emailError); }
        });
        // Strip non-digit/+ chars live
        phoneField.textProperty().addListener((obs, o, n) -> {
            String clean = n.replaceAll("[^0-9+]", "");
            if (!clean.equals(n)) { phoneField.setText(clean); return; }
            if (!n.equals(o)) { clearH(phoneField); clearFieldErr(phoneError); }
        });
        // Digits-only for age
        ageField.textProperty().addListener((obs, o, n) -> {
            String clean = n.replaceAll("[^0-9]", "");
            if (!clean.equals(n)) { ageField.setText(clean); return; }
            if (!n.equals(o)) { clearH(ageField); clearFieldErr(ageError); }
        });
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(passwordField); clearFieldErr(passwordError); }
            // Re-check confirm match live
            if (confirmPasswordField.getText() != null && !confirmPasswordField.getText().isEmpty())
                recheckConfirm();
        });
        confirmPasswordField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(confirmPasswordField); recheckConfirm(); }
        });
        captchaAnswer.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(o)) { clearH(captchaAnswer); clearFieldErr(captchaError); }
        });
    }

    private void recheckConfirm() {
        if (confirmPasswordField.getText().equals(passwordField.getText())) {
            clearH(confirmPasswordField);
            clearFieldErr(confirmError);
        }
    }

    // -----------------------------------------------------------------------
    // Submit
    // -----------------------------------------------------------------------
    @FXML
    private void onRegister() {
        clearAll();

        boolean anyError = false;

        // Name
        String nameErr = ValidationUtil.validateName(nameField.getText().trim());
        if (nameErr != null) { markField(nameField, nameError, nameErr); anyError = true; }

        // Email
        String emailErr = ValidationUtil.validateEmail(emailField.getText().trim());
        if (emailErr != null) { markField(emailField, emailError, emailErr); anyError = true; }

        // Phone
        String phoneErr = ValidationUtil.validatePhone(phoneField.getText().trim());
        if (phoneErr != null) { markField(phoneField, phoneError, phoneErr); anyError = true; }

        // Age
        String ageErr = ValidationUtil.validateAge(ageField.getText().trim());
        if (ageErr != null) { markField(ageField, ageError, ageErr); anyError = true; }

        // Password
        String pwErr = ValidationUtil.validatePassword(passwordField.getText());
        if (pwErr != null) { markField(passwordField, passwordError, pwErr); anyError = true; }

        // Confirm password
        if (!passwordField.getText().equals(confirmPasswordField.getText())) {
            markField(confirmPasswordField, confirmError, "Passwords do not match");
            anyError = true;
        }

        // CAPTCHA
        String captchaIn = captchaAnswer.getText().trim();
        if (captchaIn.isEmpty() || !captchaIn.equals(currentCaptcha[1])) {
            markField(captchaAnswer, captchaError, "Incorrect answer — try again");
            anyError = true;
        }

        if (anyError) {
            showBanner("⚠  Fix the highlighted fields to continue.");
            return;
        }

        // All valid — call service
        User.Role role;
        switch (roleCombo.getValue()) {
            case "Driver":   role = User.Role.DRIVER;   break;
            case "Supplier": role = User.Role.SUPPLIER; break;
            default:         role = User.Role.USER;     break;
        }

        String error = authService.register(
            nameField.getText().trim(),
            emailField.getText().trim(),
            phoneField.getText().trim(),
            parseAge(),
            passwordField.getText(),
            confirmPasswordField.getText(),
            role
        );

        if (error != null) {
            if (error.toLowerCase().contains("email")) {
                markField(emailField, emailError, error);
            } else if (error.toLowerCase().contains("name")) {
                markField(nameField, nameError, error);
            } else if (error.toLowerCase().contains("phone")) {
                markField(phoneField, phoneError, error);
            } else if (error.toLowerCase().contains("password")) {
                markField(passwordField, passwordError, error);
            }
            showBanner("⚠  Fix the highlighted fields to continue.");
            generateCaptcha(); captchaAnswer.clear();
        } else {
            // Look up the new user so we can pass their ID to PaymentSetup
            try {
                UserDAO dao = new UserDAO();
                User registered = dao.findByEmail(emailField.getText().trim().toLowerCase());
                if (registered != null && registered.getId() != null)
                    Session.pendingRegisteredUserId = registered.getId().toHexString();
            } catch (Exception ignored) {}

            showPostRegistrationDialog();
        }
    }

    private void showPostRegistrationDialog() {
        Session.pendingPaymentSetupSource = "POST_REGISTER";
        Session.pendingPaymentSetupReturnPage = "/fxml/login.fxml";
        NavigationManager.navigateTo("/fxml/payment_setup_choice.fxml");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Highlight a field with red border and show its specific error beneath it.
     */
    private void markField(Control field, Label errorLabel, String message) {
        field.setStyle(STYLE_ERR);
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-size: 11px;");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
        // Also set tooltip so error is visible even without a label
        Tooltip tip = new Tooltip(message);
        tip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(field, tip);
    }

    private void clearH(Control c) {
        c.setStyle(STYLE_OK);
        Tooltip.install(c, null);
    }

    private void clearFieldErr(Label lbl) {
        if (lbl == null) return;
        lbl.setText("");
        lbl.setVisible(false);
        lbl.setManaged(false);
    }

    /** Top banner: short, generic — just tells user to look at the fields. */
    private void showBanner(String msg) {
        if (errorLabel == null) return;
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearBanner() {
        if (errorLabel == null) return;
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void clearAll() {
        clearBanner();
        clearH(nameField);          clearFieldErr(nameError);
        clearH(emailField);         clearFieldErr(emailError);
        clearH(phoneField);         clearFieldErr(phoneError);
        clearH(ageField);           clearFieldErr(ageError);
        clearH(passwordField);      clearFieldErr(passwordError);
        clearH(confirmPasswordField); clearFieldErr(confirmError);
        clearH(captchaAnswer);      clearFieldErr(captchaError);
    }

    // -----------------------------------------------------------------------
    // Password strength
    // -----------------------------------------------------------------------
    private void updatePasswordStrength(String pw) {
        if (passwordStrength == null) return;
        if (pw == null || pw.isEmpty()) { passwordStrength.setText(""); return; }
        int score = 0;
        if (pw.length() >= 8)  score++;
        if (pw.length() >= 12) score++;
        if (pw.matches(".*[A-Z].*")) score++;
        if (pw.matches(".*[a-z].*")) score++;
        if (pw.matches(".*[0-9].*")) score++;
        if (pw.matches(".*[@$!%*?&#^()\\-_+=<>|~`].*")) score++;
        if (score <= 2) {
            passwordStrength.setText("⬤ Weak — add uppercase, digits & symbols");
            passwordStrength.setStyle("-fx-text-fill: #f72585; -fx-font-size: 11px;");
        } else if (score <= 4) {
            passwordStrength.setText("⬤ Fair — add more complexity");
            passwordStrength.setStyle("-fx-text-fill: #ff9f43; -fx-font-size: 11px;");
        } else if (score == 5) {
            passwordStrength.setText("⬤ Good");
            passwordStrength.setStyle("-fx-text-fill: #a78bfa; -fx-font-size: 11px;");
        } else {
            passwordStrength.setText("⬤ Strong");
            passwordStrength.setStyle("-fx-text-fill: #06d6a0; -fx-font-size: 11px;");
        }
    }

    // -----------------------------------------------------------------------
    // CAPTCHA & navigation
    // -----------------------------------------------------------------------
    private void generateCaptcha() {
        currentCaptcha = CaptchaGenerator.generateMathCaptcha();
        captchaQuestion.setText(currentCaptcha[0]);
    }

    @FXML private void onRefreshCaptcha() { generateCaptcha(); captchaAnswer.clear(); clearFieldErr(captchaError); clearH(captchaAnswer); }
    @FXML private void onGoToLogin()      { NavigationManager.navigateTo("/fxml/login.fxml"); }
    @FXML private void onBack()           { NavigationManager.navigateTo("/fxml/landing.fxml"); }

    private int parseAge() {
        try { return Integer.parseInt(ageField.getText().trim()); } catch (Exception e) { return -1; }
    }
}
