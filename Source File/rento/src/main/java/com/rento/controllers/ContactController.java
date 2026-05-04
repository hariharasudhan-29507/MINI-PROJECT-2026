package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import javafx.fxml.FXML;
/**
 * Controller for the Contact page.
 */
public class ContactController {

    @FXML private void onNavHome() { NavigationManager.navigateTo("/fxml/landing.fxml"); }
    @FXML private void onNavAbout() { NavigationManager.navigateTo("/fxml/about.fxml"); }
    @FXML private void onNavBook() {
        if (SessionManager.getInstance().isGuest()) NavigationManager.navigateTo("/fxml/login.fxml");
        else if (SessionManager.getInstance().getCurrentRole() == com.rento.models.User.Role.USER) NavigationManager.navigateTo("/fxml/booking.fxml");
        else NavigationManager.navigateTo(resolveRoleDashboard());
    }
    @FXML private void onNavRent() {
        if (SessionManager.getInstance().isGuest()) NavigationManager.navigateTo("/fxml/login.fxml");
        else if (SessionManager.getInstance().getCurrentRole() == com.rento.models.User.Role.USER) NavigationManager.navigateTo("/fxml/rent.fxml");
        else NavigationManager.navigateTo(resolveRoleDashboard());
    }
    @FXML private void onNavProfile() {
        if (SessionManager.getInstance().isGuest()) NavigationManager.navigateTo("/fxml/login.fxml");
        else NavigationManager.navigateTo("/fxml/profile.fxml");
    }

    private String resolveRoleDashboard() {
        return switch (SessionManager.getInstance().getCurrentRole()) {
            case ADMIN -> "/fxml/admin_dashboard.fxml";
            case DRIVER -> "/fxml/driver_dashboard.fxml";
            case SUPPLIER -> "/fxml/supplier_dashboard.fxml";
            default -> "/fxml/profile.fxml";
        };
    }
}

