# Rento — Source File

This directory contains the complete source code and bundled dependencies for the **Rento** Vehicle Rental & Booking System — a Java desktop application built with JavaFX and MongoDB.

---

## Directory Structure

```text
Source File/
├── jars/                          # All bundled third-party JARs (no build tool required)
│   ├── javafx-sdk-21.0.10/        # JavaFX SDK (platform-specific native libs)
│   ├── mongodb-driver-sync-5.2.1.jar
│   ├── mongodb-driver-core-5.2.1.jar
│   ├── bson-5.2.1.jar
│   ├── jbcrypt-0.4.jar
│   ├── itextpdf-5.5.13.3.jar
│   ├── controlsfx-11.1.2.jar
│   ├── gson-2.10.1.jar
│   ├── slf4j-api-2.0.13.jar
│   └── slf4j-simple-2.0.13.jar
└── rento/
    └── src/main/
        ├── java/com/rento/
        │   ├── app/               # Application entry point (RentoApplication.java)
        │   ├── controllers/       # JavaFX FXML controllers (one per screen)
        │   ├── dao/               # Data access objects — MongoDB CRUD operations
        │   ├── models/            # Domain model classes (User, Vehicle, Booking, …)
        │   ├── navigation/        # Scene/navigation manager (NavigationManager.java)
        │   ├── security/          # Password hashing (jBCrypt) & session management
        │   ├── services/          # Business-logic services (Auth, Booking, Payment, …)
        │   └── utils/             # Shared utilities (DB connection, alerts, validation, …)
        └── resources/
            ├── css/               # Global JavaFX stylesheet (global.css)
            ├── fonts/             # Custom font assets
            ├── fxml/              # FXML layout files (one per screen)
            └── images/            # Application image assets
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 (FXML + CSS) |
| Database | MongoDB 7 (local, `rento_db`) |
| DB Driver | MongoDB Java Driver Sync 5.2.1 |
| Security | jBCrypt 0.4 (password hashing) |
| PDF/Reporting | iText PDF 5.5.13.3 |
| JSON | Gson 2.10.1 |
| UI Extras | ControlsFX 11.1.2 |
| Logging | SLF4J 2.0.13 (simple backend) |

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | **21** (LTS) |
| MongoDB Community Server | **7.x** (running on `localhost:27017`) |
| IDE | IntelliJ IDEA or Eclipse with JavaFX support |

---

## Setup Instructions

### 1. Clone the repository

```bash
git clone https://github.com/hariharasudhan-29507/Rento.git
cd Rento
```

### 2. Open the project in your IDE

Set the **source root** to:

```
Source File/rento/src/main/java
```

Set the **resources root** to:

```
Source File/rento/src/main/resources
```

### 3. Add JARs to the project classpath

Add **every JAR** inside `Source File/jars/` to the project libraries/classpath, including the JavaFX JARs from:

```
Source File/jars/javafx-sdk-21.0.10/lib/
```

> **Note:** No Maven or Gradle configuration is used. Dependencies are managed manually via the `jars/` folder.

### 4. Configure JavaFX VM options

Add the following VM arguments when creating your IDE run configuration (replace the path with the absolute path on your machine):

```
--module-path "path/to/Source File/jars/javafx-sdk-21.0.10/lib" \
--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.swing,javafx.web,javafx.media
```

### 5. Start MongoDB

Make sure MongoDB is running locally before launching the app:

```bash
# macOS / Linux
mongod --dbpath /data/db

# Windows (run as Administrator)
net start MongoDB
```

- Connection string: `mongodb://localhost:27017`
- Database name: `rento_db`

### 6. Run the application

Run the main class:

```
com.rento.app.RentoApplication
```

On first launch, the app automatically seeds demo users and vehicles into `rento_db`.

---

## Demo Accounts (auto-seeded)

| Role | Email | Password |
|---|---|---|
| Customer | `user@rento.local` | `Rento@123` |
| Driver | `driver@rento.local` | `Rento@123` |
| Supplier | `supplier@rento.local` | `Rento@123` |
| Admin | `admin@rento.local` | `Rento@123` |

---

## Key Packages

| Package | Responsibility |
|---|---|
| `com.rento.app` | JavaFX `Application` entry point, stage setup |
| `com.rento.controllers` | One controller per FXML screen; handles UI events |
| `com.rento.dao` | MongoDB read/write operations for each collection |
| `com.rento.models` | Plain Java domain objects (User, Vehicle, Booking, Rental, Payment) |
| `com.rento.services` | Business logic: auth, booking, payment, receipt, export |
| `com.rento.navigation` | Centralised scene-switching via `NavigationManager` |
| `com.rento.security` | `PasswordHasher` (jBCrypt) and `SessionManager` |
| `com.rento.utils` | `MongoDBConnection`, `AlertUtil`, `ValidationUtil`, `OTPGenerator`, `DateTimeUtil`, `CaptchaGenerator` |

---

## Author

**Hariharasudhan A**  
*Love to unveil something new*  
Sophomore, Mepco Schlenk Engineering College, Sivakasi  
📧 [sudanayyappan_bcs28@mepcoeng.ac.in](mailto:sudanayyappan_bcs28@mepcoeng.ac.in)

---

## Related Documentation

- 📄 [Software Requirements Specification (SRS)](../Documentation/SRS%20VRBS.pdf)
- 📄 [Project Specification / ER Diagram](../Documentation/SPECIFICATION%20VRBS.pdf)
- 📐 [UML Diagrams](../UML%20diagrams/README.md)
- 📖 [Project Wiki](https://github.com/hariharasudhan-29507/Rento/wiki)
- 📘 [Root README](../README.md)
