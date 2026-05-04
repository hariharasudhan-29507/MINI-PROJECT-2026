package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.utils.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Dedicated post-registration payment decision page.
 */
public class PaymentSetupChoiceController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML
    private void initialize() {
        titleLabel.setText("Account Created");
        subtitleLabel.setText(
            "Your account is ready. Set up your payment profile now for faster booking and renting, or skip and add it when you need it."
        );
    }

    @FXML
    private void onSetUpNow() {
        Session.pendingPaymentSetupSource = "POST_REGISTER";
        Session.pendingPaymentSetupReturnPage = "/fxml/login.fxml";
        NavigationManager.navigateTo("/fxml/payment_setup.fxml");
    }

    @FXML
    private void onSetUpLater() {
        Session.pendingRegisteredUserId = null;
        Session.pendingPaymentSetupSource = null;
        Session.pendingPaymentSetupReturnPage = null;
        NavigationManager.navigateTo("/fxml/login.fxml");
    }
}
