package com.rento.controllers;

import com.rento.navigation.NavigationManager;
import com.rento.security.SessionManager;
import com.rento.models.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the landing page.
 */
public class LandingController implements Initializable {

    @FXML private Button profileBtn;
    @FXML private Button bookNavButton;
    @FXML private Button rentNavButton;
    @FXML private Button currentDriveNavButton;
    @FXML private Button userBoardNavButton;
    @FXML private Button driverBoardNavButton;
    @FXML private Button supplierBoardNavButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        updateProfileButton();
        updateNavigationByRole();
    }

    private void updateProfileButton() {
        if (SessionManager.getInstance().isLoggedIn()) {
            profileBtn.setText("⬤ " + SessionManager.getInstance().getCurrentUserName());
        } else {
            profileBtn.setText("⬤ Sign In");
        }
    }

    private void updateNavigationByRole() {
        boolean isAdmin = SessionManager.getInstance().getCurrentRole() == User.Role.ADMIN;
        boolean isDriver = SessionManager.getInstance().getCurrentRole() == User.Role.DRIVER;
        bookNavButton.setVisible(!isAdmin && !isDriver);
        bookNavButton.setManaged(!isAdmin && !isDriver);
        rentNavButton.setVisible(!isAdmin && !isDriver);
        rentNavButton.setManaged(!isAdmin && !isDriver);
        currentDriveNavButton.setVisible(isDriver);
        currentDriveNavButton.setManaged(isDriver);
        driverBoardNavButton.setText(isDriver ? "Dashboard" : "Driver Dashboard");
        driverBoardNavButton.setVisible(isAdmin || isDriver);
        driverBoardNavButton.setManaged(isAdmin || isDriver);
        userBoardNavButton.setVisible(isAdmin);
        userBoardNavButton.setManaged(isAdmin);
        supplierBoardNavButton.setVisible(isAdmin);
        supplierBoardNavButton.setManaged(isAdmin);
    }

    @FXML
    private void onNavHome() {
        NavigationManager.navigateTo("/fxml/landing.fxml");
    }

    @FXML
    private void onNavAbout() {
        NavigationManager.navigateTo("/fxml/about.fxml");
    }

    @FXML
    private void onNavBook() {
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml");
        } else if (SessionManager.getInstance().getCurrentRole() == com.rento.models.User.Role.USER) {
            NavigationManager.navigateTo("/fxml/booking.fxml");
        } else {
            NavigationManager.navigateTo(resolveRoleDashboard());
        }
    }

    @FXML
    private void onNavRent() {
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml");
        } else if (SessionManager.getInstance().getCurrentRole() == com.rento.models.User.Role.USER) {
            NavigationManager.navigateTo("/fxml/rent.fxml");
        } else {
            NavigationManager.navigateTo(resolveRoleDashboard());
        }
    }

    @FXML
    private void onNavContact() {
        NavigationManager.navigateTo("/fxml/contact.fxml");
    }

    @FXML
    private void onNavAdminUserBoard() {
        NavigationManager.navigateTo("/fxml/admin_dashboard.fxml", controller -> {
            if (controller instanceof AdminDashboardController adminDashboardController) {
                adminDashboardController.openBoard("USER");
            }
        });
    }

    @FXML
    private void onNavAdminDriverBoard() {
        if (SessionManager.getInstance().getCurrentRole() == User.Role.DRIVER) {
            NavigationManager.navigateTo("/fxml/driver_dashboard.fxml");
            return;
        }
        NavigationManager.navigateTo("/fxml/admin_dashboard.fxml", controller -> {
            if (controller instanceof AdminDashboardController adminDashboardController) {
                adminDashboardController.openBoard("DRIVER");
            }
        });
    }

    @FXML
    private void onNavDriverCurrentDrive() {
        NavigationManager.navigateTo("/fxml/driver_dashboard.fxml", controller -> {
            if (controller instanceof DriverDashboardController driverDashboardController) {
                driverDashboardController.onShowCurrentDrive();
            }
        });
    }

    @FXML
    private void onNavAdminSupplierBoard() {
        NavigationManager.navigateTo("/fxml/admin_dashboard.fxml", controller -> {
            if (controller instanceof AdminDashboardController adminDashboardController) {
                adminDashboardController.openBoard("SUPPLIER");
            }
        });
    }

    @FXML
    private void onNavProfile() {
        if (SessionManager.getInstance().isGuest()) {
            NavigationManager.navigateTo("/fxml/login.fxml");
        } else {
            NavigationManager.navigateTo("/fxml/profile.fxml");
        }
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
