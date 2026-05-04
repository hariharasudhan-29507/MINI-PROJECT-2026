package com.rento.controllers;

import com.rento.models.Booking;
import com.rento.models.Payment;
import com.rento.models.PaymentMethodProfile;
import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.services.BookingService;
import com.rento.services.PaymentMethodService;
import com.rento.services.PaymentService;
import com.rento.utils.DateTimeUtil;
import com.rento.utils.AlertUtil;
import com.rento.utils.OTPGenerator;
import com.rento.utils.ValidationUtil;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the payment page.
 */
public class PaymentController implements Initializable {

    @FXML private ComboBox<String> methodCombo;
    @FXML private Label         accountNumberLabel;
    @FXML private Label         accountNameLabel;
    @FXML private TextField     cardNumberField;
    @FXML private Label         cardNumberError;    // below card number field
    @FXML private TextField     cardNameField;
    @FXML private Label         cardNameError;      // below cardholder name
    @FXML private TextField     expiryField;
    @FXML private Label         expiryError;
    @FXML private PasswordField cvvField;
    @FXML private Label         cvvError;
    @FXML private Label         errorLabel;         // top banner — SHORT only
    @FXML private StackPane     processingPane;
    @FXML private Button        payBtn;

    @FXML private Label summaryVehicle;
    @FXML private Label summaryPickup;
    @FXML private Label summaryDropoff;
    @FXML private Label summaryDays;
    @FXML private Label summarySubtotal;
    @FXML private Label summaryTax;
    @FXML private Label summaryTotal;
    @FXML private VBox otpSection;
    @FXML private Label otpLabel;
    @FXML private Label savedMethodLabel;
    @FXML private Label savedMethodHint;
    @FXML private CheckBox useSavedProfileCheck;

    private static final String STYLE_ERR =
        "-fx-border-color: #f72585; -fx-border-width: 2px; -fx-border-radius: 6px; -fx-background-radius: 6px;";
    private static final String STYLE_OK =
        "-fx-border-color: transparent; -fx-border-width: 1px;";

    private Booking currentBooking;
    private final PaymentService paymentService = new PaymentService();
    private final BookingService bookingService = new BookingService();
    private final PaymentMethodService paymentMethodService = new PaymentMethodService();
    private PaymentMethodProfile preferredPaymentProfile;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        methodCombo.setItems(FXCollections.observableArrayList(
            "Credit Card", "UPI", "Cash on Delivery"));
        methodCombo.setValue("Credit Card");
        methodCombo.valueProperty().addListener((obs, o, n) -> { updatePaymentFields(); clearAll(); });
        if (useSavedProfileCheck != null) {
            useSavedProfileCheck.selectedProperty().addListener((obs, o, n) -> { updatePaymentFields(); clearAll(); });
        }
        updatePaymentFields();
        // Clear border + field error on type
        attachClear(cardNumberField, cardNumberError);
        attachClear(cardNameField,   cardNameError);
        attachClear(expiryField,     expiryError);
        cvvField.textProperty().addListener((obs, o, n) -> { if (!n.equals(o)) { clearH(cvvField); clearFE(cvvError); } });
        loadSavedPaymentProfile();
    }

    public void setBooking(Booking booking) {
        this.currentBooking = booking;
        if (booking != null) {
            summaryVehicle.setText(booking.getVehicleName() != null ? booking.getVehicleName() : "Vehicle");
            summaryPickup.setText((booking.getPickupLocation() != null ? booking.getPickupLocation() : "") +
                " • " + DateTimeUtil.formatDateTime(booking.getPickupDateTime()));
            summaryDropoff.setText((booking.getDropoffLocation() != null ? booking.getDropoffLocation() : "") +
                " • " + DateTimeUtil.formatDateTime(booking.getReturnDateTime()));
            summaryDays.setText(booking.getRentalDurationLabel());

            double subtotal = booking.getTotalCost() - booking.getTaxAmount();
            summarySubtotal.setText(ValidationUtil.formatCurrency(subtotal));
            summaryTax.setText(ValidationUtil.formatCurrency(booking.getTaxAmount()));
            summaryTotal.setText(ValidationUtil.formatCurrency(booking.getTotalCost()));
        }
    }

    @FXML
    private void onPay() {
        clearAll();
        boolean anyError = false;

        if (!isUsingSavedProfile() && requiresCardDetails()) {
            String cardNum = cardNumberField.getText().replaceAll("[\\s\\-]", "");
            String cardErr = ValidationUtil.validateCardNumber(cardNum);
            if (cardErr != null) { markField(cardNumberField, cardNumberError, cardErr); anyError = true; }

            if (!ValidationUtil.isNotEmpty(cardNameField.getText()))
                { markField(cardNameField, cardNameError, "Cardholder name is required"); anyError = true; }

            String expErr = ValidationUtil.validateExpiryDate(expiryField.getText());
            if (expErr != null) { markField(expiryField, expiryError, expErr); anyError = true; }

            String cvvErr = ValidationUtil.validateCVV(cvvField.getText());
            if (cvvErr != null) { markField(cvvField, cvvError, cvvErr); anyError = true; }

        } else if (!isUsingSavedProfile() && requiresUpiDetails()) {
            String upiVal = cardNumberField.getText() != null ? cardNumberField.getText().trim() : "";
            String upiErr = ValidationUtil.validateUpiId(upiVal);
            if (upiErr != null) { markField(cardNumberField, cardNumberError, upiErr); anyError = true; }

            if (!ValidationUtil.isNotEmpty(cardNameField.getText()))
                { markField(cardNameField, cardNameError, "Account holder name is required"); anyError = true; }

        } else {
            if (currentBooking == null) {
                showBanner("⚠  Booking details are missing. Please go back and try again.");
                return;
            }
        }

        if (anyError) {
            showBanner("⚠  Fix the highlighted fields to continue.");
            return;
        }

        payBtn.setDisable(true);
        processingPane.setVisible(true);
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(e -> completePayment());
        delay.play();
    }

    private void completePayment() {
        processingPane.setVisible(false);
        payBtn.setDisable(false);

        try {
            Payment.PaymentMethod method = methodFromSelection();
            String accountReference = cardNumberField.getText();
            String accountHolder = cardNameField.getText();
            if (!requiresCardDetails()) {
                accountHolder = SessionManager.getInstance().getCurrentUserName();
            }

            Payment payment = isUsingSavedProfile()
                ? paymentService.processSavedBookingPayment(currentBooking, preferredPaymentProfile)
                : paymentService.processPayment(
                    currentBooking != null ? currentBooking.getId() : null,
                    SessionManager.getInstance().getCurrentUser() != null ? SessionManager.getInstance().getCurrentUser().getId() : null,
                    currentBooking != null ? currentBooking.getTotalCost() - currentBooking.getTaxAmount() : 0,
                    currentBooking != null ? currentBooking.getTaxAmount() : 0,
                    currentBooking != null ? currentBooking.getTotalCost() : 0,
                    method,
                    accountReference,
                    accountHolder,
                    expiryField.getText(),
                    cvvField.getText()
                );
            if (payment == null) {
                payment = createSimulatedPayment(
                    isUsingSavedProfile() ? paymentMethodService.toPaymentMethod(preferredPaymentProfile) : method,
                    isUsingSavedProfile() ? preferredPaymentProfile.getMaskedReference() : accountReference,
                    isUsingSavedProfile() ? preferredPaymentProfile.getHolderName() : accountHolder
                );
            }

            // Ensure booking is properly persisted with all payment details
            if (currentBooking != null && currentBooking.getId() != null) {
                bookingService.notifyBookingPaymentSuccess(currentBooking, payment);
            }
        } catch (Exception ex) {
            System.out.println("[Payment] Processing in demo mode: " + ex.getMessage());
            if (currentBooking != null) {
                bookingService.notifyBookingPaymentSuccess(currentBooking,
                    createSimulatedPayment(methodFromSelection(), cardNumberField.getText(), cardNameField.getText()));
            }
        }

        // Display OTP to customer
        String otp = currentBooking != null && currentBooking.getOtp() != null ?
            currentBooking.getOtp() : OTPGenerator.generateOTP();
        otpLabel.setText(otp);
        otpSection.setVisible(true);

        // Disable pay button
        payBtn.setText("✓ Payment Successful");
        payBtn.getStyleClass().clear();
        payBtn.getStyleClass().add("btn-accent");
        payBtn.setDisable(true);

        if (methodFromSelection() == Payment.PaymentMethod.CASH_ON_DELIVERY) {
            AlertUtil.showSuccess("Cash on delivery selected.\n\nYour OTP: " + otp + "\n\nShare this with your driver at pickup. The driver will verify payment and unlock the receipt.");
        } else {
            AlertUtil.showSuccess("Payment successful!\n\nYour OTP: " + otp + "\n\nShare this with your driver at pickup.\nReceipt will be generated after ride confirmation.");
        }
    }

    @FXML private void onBack() { NavigationManager.goBack(); }
    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }

    private boolean requiresCardDetails() {
        return "Credit Card".equals(methodCombo.getValue());
    }

    private boolean requiresUpiDetails() {
        return "UPI".equals(methodCombo.getValue());
    }

    private Payment.PaymentMethod methodFromSelection() {
        return switch (methodCombo.getValue()) {
            case "UPI" -> Payment.PaymentMethod.UPI;
            case "Cash on Delivery" -> Payment.PaymentMethod.CASH_ON_DELIVERY;
            default -> Payment.PaymentMethod.CREDIT_CARD;
        };
    }

    private void updatePaymentFields() {
        if (isUsingSavedProfile()) {
            methodCombo.setValue(labelFromProfile(preferredPaymentProfile));
            methodCombo.setDisable(true);
            cardNumberField.setText(preferredPaymentProfile.getMaskedReference());
            cardNameField.setText(preferredPaymentProfile.getHolderName());
            cardNumberField.setDisable(true);
            cardNameField.setDisable(true);
            expiryField.clear();
            cvvField.clear();
            expiryField.setDisable(true);
            cvvField.setDisable(true);
            accountNumberLabel.setText(preferredPaymentProfile.getMethodType() == PaymentMethodProfile.MethodType.UPI
                ? "Saved UPI ID" : preferredPaymentProfile.getMethodType() == PaymentMethodProfile.MethodType.CASH_ON_DELIVERY
                ? "Collection Mode" : "Saved Card");
            accountNameLabel.setText("Saved Holder");
            if (savedMethodLabel != null) {
                savedMethodLabel.setText("Using saved payment profile: " + preferredPaymentProfile.getProfileName());
                savedMethodLabel.setVisible(true);
            }
            if (savedMethodHint != null) {
                savedMethodHint.setText("This offline build reuses your masked reference and holder name inside checkout.");
                savedMethodHint.setVisible(true);
            }
            return;
        }

        methodCombo.setDisable(false);
        if (savedMethodLabel != null) {
            savedMethodLabel.setText(preferredPaymentProfile != null
                ? "Saved profile available: " + preferredPaymentProfile.getProfileName()
                : "");
            savedMethodLabel.setVisible(preferredPaymentProfile != null);
        }
        if (savedMethodHint != null) {
            savedMethodHint.setText(preferredPaymentProfile != null
                ? "Uncheck the saved profile option to use a new card, UPI, or cash-on-delivery for this payment."
                : "");
            savedMethodHint.setVisible(preferredPaymentProfile != null);
        }
        boolean showCardFields = requiresCardDetails();
        boolean showUpiFields = requiresUpiDetails();
        cardNumberField.setDisable(false);
        cardNameField.setDisable("Cash on Delivery".equals(methodCombo.getValue()));
        expiryField.setDisable(!showCardFields);
        cvvField.setDisable(!showCardFields);

        if (!showCardFields) {
            expiryField.clear();
            cvvField.clear();
        }

        switch (methodCombo.getValue()) {
            case "UPI":
                accountNumberLabel.setText("UPI ID");
                accountNameLabel.setText("Account Holder");
                cardNumberField.setPromptText("name@bank");
                cardNameField.setPromptText("UPI account holder");
                break;
            case "Cash on Delivery":
                accountNumberLabel.setText("Collection Mode");
                accountNameLabel.setText("Verifier");
                cardNumberField.setPromptText("Cash will be collected by the driver");
                cardNumberField.setText("Cash on delivery");
                cardNumberField.setDisable(true);
                cardNameField.setPromptText("Verified during delivery");
                cardNameField.clear();
                break;
            default:
                accountNumberLabel.setText("Card Number");
                accountNameLabel.setText("Cardholder Name");
                cardNumberField.setPromptText("1234 5678 9012 3456");
                cardNameField.setPromptText("Name on card");
                if (!showUpiFields) {
                    cardNumberField.clear();
                    cardNameField.clear();
                }
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Error display helpers
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

    private void clearH(Control c)    { c.setStyle(STYLE_OK); Tooltip.install(c, null); }
    private void clearFE(Label lbl)   { if (lbl == null) return; lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false); }

    private void attachClear(TextField f, Label lbl) {
        f.textProperty().addListener((obs, o, n) -> { if (!n.equals(o)) { clearH(f); clearFE(lbl); } });
    }

    private void showBanner(String msg) {
        if (errorLabel == null) return;
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #f72585; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearAll() {
        if (errorLabel != null) { errorLabel.setText(""); errorLabel.setVisible(false); errorLabel.setManaged(false); }
        clearH(cardNumberField); clearFE(cardNumberError);
        clearH(cardNameField);   clearFE(cardNameError);
        clearH(expiryField);     clearFE(expiryError);
        clearH(cvvField);        clearFE(cvvError);
    }

    private Payment createSimulatedPayment(Payment.PaymentMethod method, String accountReference, String accountHolder) {
        Payment payment = new Payment();
        payment.setBookingId(currentBooking != null ? currentBooking.getId() : null);
        payment.setUserId(SessionManager.getInstance().getCurrentUser() != null
            ? SessionManager.getInstance().getCurrentUser().getId() : null);
        payment.setAmount(currentBooking != null ? currentBooking.getTotalCost() - currentBooking.getTaxAmount() : 0);
        payment.setTaxAmount(currentBooking != null ? currentBooking.getTaxAmount() : 0);
        payment.setTotalAmount(currentBooking != null ? currentBooking.getTotalCost() : 0);
        payment.setPaymentMethod(method);
        payment.setPaymentLabel(method.name());
        if (method == Payment.PaymentMethod.CREDIT_CARD) {
            payment.setCardNumber(ValidationUtil.maskCardNumber(accountReference));
        } else if (method == Payment.PaymentMethod.UPI) {
            payment.setUpiId(ValidationUtil.maskUpiId(accountReference));
        }
        payment.setCardHolderName(accountHolder);
        payment.setStatus(method == Payment.PaymentMethod.CASH_ON_DELIVERY
            ? Payment.PaymentStatus.PENDING_CASH_CONFIRMATION
            : Payment.PaymentStatus.COMPLETED);
        payment.setTransactionRef(OTPGenerator.generateTransactionRef());
        return payment;
    }

    private void loadSavedPaymentProfile() {
        if (SessionManager.getInstance().getCurrentUser() == null) {
            preferredPaymentProfile = null;
            if (useSavedProfileCheck != null) {
                useSavedProfileCheck.setVisible(false);
                useSavedProfileCheck.setManaged(false);
            }
            return;
        }
        preferredPaymentProfile = paymentMethodService.getPreferredPaymentMethod(
            SessionManager.getInstance().getCurrentUser().getId()
        );
        if (useSavedProfileCheck != null) {
            boolean hasSavedProfile = preferredPaymentProfile != null;
            useSavedProfileCheck.setVisible(hasSavedProfile);
            useSavedProfileCheck.setManaged(hasSavedProfile);
            useSavedProfileCheck.setSelected(hasSavedProfile);
        }
        updatePaymentFields();
    }

    private boolean isUsingSavedProfile() {
        return preferredPaymentProfile != null
            && useSavedProfileCheck != null
            && useSavedProfileCheck.isSelected();
    }

    private String labelFromProfile(PaymentMethodProfile profile) {
        return switch (profile.getMethodType()) {
            case UPI -> "UPI";
            case CASH_ON_DELIVERY -> "Cash on Delivery";
            default -> "Credit Card";
        };
    }
}
