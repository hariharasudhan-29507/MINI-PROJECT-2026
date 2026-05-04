package com.rento.app;

import com.rento.navigation.NavigationManager;
import com.rento.utils.MongoDBConnection;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Rento - Vehicle Rental & Booking System
 * Main Application Entry Point
 */
public class RentoApplication extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        try {
            // Initialize MongoDB connection
            MongoDBConnection.getInstance();

            // Root container
            StackPane root = new StackPane();
            root.setId("app-root");

            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/global.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("Rento — Vehicle Rental & Booking System");
            stage.setMinWidth(1024);
            stage.setMinHeight(700);

            // Try to load app icon
            try {
                stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/rento_icon.png")));
            } catch (Exception ignored) {}

            // Initialize navigation and load landing page
            NavigationManager.initialize(root, scene);
            NavigationManager.navigateTo("/fxml/landing.fxml");

            stage.show();

        } catch (Exception e) {
            System.err.println("Failed to start Rento application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        MongoDBConnection.close();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

