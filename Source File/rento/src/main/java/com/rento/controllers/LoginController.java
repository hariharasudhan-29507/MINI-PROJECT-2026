package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.AuthService;
import com.rento.utils.CaptchaGenerator;
import com.rento.utils.ValidationUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * LoginController — Form UX:
 *   • Top label → "⚠ Fix the highlighted fields to continue."
 *   • Each invalid field → red border + small specific error label beneath it
 *   • As user types → border + field error clears immediately
 */
public class LoginController implements Initializable {

    @FXML private TextField     emailField;
    @FXML private Label         emailError;       // small label below email
    @FXML private PasswordField passwordField;
    @FXML private Label         passwordError;    // small label below password
    @FXML private TextField     captchaAnswer;
    @FXML private Label         captchaError;     // small label below captcha
    @FXML private Label         captchaQuestion;
    @FXML private Label         errorLabel;       // top banner — SHORT only

    private final AuthService authService = new AuthService();
    private String[] currentCaptcha;

    private static final String STYLE_ERR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_OK  =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        generateCaptcha();
        emailField.textProperty().addListener((obs, o, n)    -> { if (!n.equals(o)) { clearH(emailField);    clearFieldErr(emailError);    } });
        passwordField.textProperty().addListener((obs, o, n) -> { if (!n.equals(o)) { clearH(passwordField); clearFieldErr(passwordError); } });
        captchaAnswer.textProperty().addListener((obs, o, n) -> { if (!n.equals(o)) { clearH(captchaAnswer); clearFieldErr(captchaError); } });
    }

    @FXML
    private void onLogin() {
        clearAll();

        boolean anyError = false;

        // Email
        String emailErr = ValidationUtil.validateEmail(emailField.getText().trim());
        if (emailErr != null) { markField(emailField, emailError, emailErr); anyError = true; }

        // Password
        if (!ValidationUtil.isNotEmpty(passwordField.getText())) {
            markField(passwordField, passwordError, "Password is required"); anyError = true;
        }

        // CAPTCHA
        String captchaIn = captchaAnswer.getText().trim();
        if (captchaIn.isEmpty() || !captchaIn.equals(currentCaptcha[1])) {
            markField(captchaAnswer, captchaError, "Incorrect answer — try again");
            anyError = true;
        }

        if (anyError) {
            showBanner("⚠  Fix the highlighted fields to sign in.");
            if (captchaIn.isEmpty() || !captchaIn.equals(currentCaptcha[1])) {
                generateCaptcha(); captchaAnswer.clear();
            }
            return;
        }

        String error = authService.login(emailField.getText().trim(), passwordField.getText());
        if (error != null) {
            // Route to the relevant field
            if (error.toLowerCase().contains("email") || error.toLowerCase().contains("account") || error.toLowerCase().contains("no account")) {
                markField(emailField, emailError, error);
            } else if (error.toLowerCase().contains("password") || error.toLowerCase().contains("incorrect")) {
                markField(passwordField, passwordError, error);
            } else {
                markField(emailField, emailError, error);
            }
            showBanner("⚠  Fix the highlighted fields to sign in.");
            generateCaptcha(); captchaAnswer.clear();
        } else {
            switch (SessionManager.getInstance().getCurrentRole()) {
                case DRIVER:   NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");   break;
                case SUPPLIER: NavigationManager.navigateTo("/fxml/supplier_dashboard.fxml"); break;
                case ADMIN:    NavigationManager.navigateTo("/fxml/admin_dashboard.fxml");    break;
                default:       NavigationManager.navigateTo("/fxml/booking.fxml");            break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
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

    private void clearH(Control c)          { c.setStyle(STYLE_OK); Tooltip.install(c, null); }
    private void clearFieldErr(Label lbl)    { if (lbl == null) return; lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false); }

    private void showBanner(String msg) {
        if (errorLabel == null) return;
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearAll() {
        if (errorLabel != null) { errorLabel.setText(""); errorLabel.setVisible(false); errorLabel.setManaged(false); }
        clearH(emailField);    clearFieldErr(emailError);
        clearH(passwordField); clearFieldErr(passwordError);
        clearH(captchaAnswer); clearFieldErr(captchaError);
    }

    // -----------------------------------------------------------------------
    private void generateCaptcha() {
        currentCaptcha = CaptchaGenerator.generateMathCaptcha();
        captchaQuestion.setText(currentCaptcha[0]);
    }

    @FXML private void onRefreshCaptcha() { generateCaptcha(); captchaAnswer.clear(); clearFieldErr(captchaError); clearH(captchaAnswer); }
    @FXML private void onGoToRegister()   { NavigationManager.navigateTo("/fxml/register.fxml"); }
    @FXML private void onBack()           { NavigationManager.navigateTo("/fxml/landing.fxml"); }
}
