# Rento — Vehicle Rental & Booking System

Rento is a Java desktop application for end-to-end vehicle rental and booking management.  
It supports customer booking/rental flows and role-based operations for Admin, Supplier, and Driver users.

## What this project does

- User registration and login with secure password hashing
- Vehicle browsing, rental, and booking workflows
- Payment flow with receipt generation (PDF/TXT)
- Role-based dashboards:
  - **Admin**: platform-level operations and exports
  - **Supplier**: fleet and rental management
  - **Driver**: assigned booking management
- Demo data seeding for first-time local setup

## Tech Stack

- **Language:** Java
- **UI Framework:** JavaFX (FXML + CSS)
- **Database:** MongoDB
- **Database Driver:** MongoDB Java Driver (sync)
- **Security:** jBCrypt (password hashing), session handling
- **Reporting/Exports:** iText PDF, Gson, file-based exports
- **UI Add-ons:** ControlsFX
- **Logging:** SLF4J (simple backend)

## Project Structure

```text
Rento/
├── README.md
├── Documentation/
│   ├── SRS VRBS.pdf
│   └── SPECIFICATION VRBS.pdf
├── Source File/
│   ├── jars/                      # Third-party dependencies and JavaFX SDK
│   └── rento/
│       └── src/main/
│           ├── java/com/rento/
│           │   ├── app/           # App entry point
│           │   ├── controllers/   # JavaFX controllers
│           │   ├── services/      # Business logic
│           │   ├── dao/           # Data access (MongoDB)
│           │   ├── models/        # Domain models
│           │   ├── utils/         # Utilities and DB connection
│           │   ├── security/      # Auth/session helpers
│           │   └── navigation/    # Scene/navigation manager
│           └── resources/
│               ├── fxml/          # UI layouts
│               ├── css/           # Styling
│               └── images/        # App assets
└── UML diagrams/
    └── README.md
```

## Source File

The `Source File/` directory contains all source code, dependencies, and resources for the application.

### `jars/`

Holds every third-party JAR the project depends on — no build tool (Maven/Gradle) is used, so all dependencies are bundled here and added to the IDE classpath manually.

| JAR | Purpose |
|-----|---------|
| `javafx-sdk-21.0.10/` + individual JavaFX JARs | UI framework (controls, FXML, graphics, media, web) |
| `mongodb-driver-sync-5.2.1.jar`, `mongodb-driver-core-5.2.1.jar`, `bson-5.2.1.jar` | MongoDB Java driver for database access |
| `jbcrypt-0.4.jar` | Password hashing (bcrypt) |
| `itextpdf-5.5.13.3.jar` | PDF receipt generation |
| `controlsfx-11.1.2.jar` | Extended JavaFX UI controls |
| `gson-2.10.1.jar` | JSON serialisation/deserialisation |
| `slf4j-api-2.0.13.jar`, `slf4j-simple-2.0.13.jar` | Logging façade and simple backend |

---

### `rento/src/main/java/com/rento/`

All Java source packages live here.

#### `app/`
Contains `RentoApplication.java` — the JavaFX `Application` subclass that serves as the single entry point. It bootstraps the database, seeds demo data on first launch, and loads the initial scene.

#### `controllers/`
One JavaFX controller per screen. Each controller handles user interactions for its corresponding FXML view, delegates business logic to the `services` layer, and uses `NavigationManager` to switch scenes. Key controllers:

| Controller | Responsibility |
|---|---|
| `LoginController` / `RegisterController` | Authentication and new-account creation |
| `LandingController` | Home/browse screen for customers |
| `RentController` / `BookingController` | Vehicle selection, rental and booking flows |
| `PaymentController` / `PaymentSetupController` | Payment processing and saved-method management |
| `AdminDashboardController` | Platform-level admin operations and exports |
| `SupplierDashboardController` | Fleet and rental management for suppliers |
| `DriverDashboardController` | Assigned-booking management for drivers |
| `ProfileController` | User profile viewing and editing |

#### `services/`
Business-logic layer; called by controllers and isolated from the database. Each service class owns one domain area:

| Service | Responsibility |
|---|---|
| `AuthService` | Login, registration, password validation |
| `BookingService` | Creating, updating, and cancelling bookings |
| `RentalService` | Rental lifecycle management |
| `PaymentService` | Payment processing and history |
| `PaymentMethodService` | Saving and retrieving payment methods |
| `ReceiptService` | Generating PDF and text receipts |
| `DemoDataService` | Seeding demo users and vehicles on first launch |
| `AdminExportService` | CSV/data export for admins |
| `NotificationService` | In-app notification helpers |
| `SystemCollectionBootstrapService` | Ensuring required MongoDB collections exist |

#### `dao/`
Data-access objects (DAOs) that interact directly with MongoDB. Each DAO wraps CRUD and query operations for one collection:

| DAO | Collection |
|---|---|
| `UserDAO` | `users` |
| `VehicleDAO` | `vehicles` |
| `BookingDAO` | `bookings` |
| `RentalDAO` | `rentals` |
| `PaymentDAO` | `payments` |
| `PaymentMethodDAO` | `payment_methods` |

#### `models/`
Plain Java classes that map to MongoDB documents: `User`, `Vehicle`, `Booking`, `Rental`, `Payment`, `PaymentMethodProfile`. These are passed between layers and serialised/deserialised by the DAOs.

#### `utils/`
Shared helper classes used across the application:

| Utility | Purpose |
|---|---|
| `MongoDBConnection` | Singleton that holds the `MongoClient` / `MongoDatabase` reference |
| `ValidationUtil` | Common input-validation rules (email, phone, etc.) |
| `DateTimeUtil` | Date formatting and parsing helpers |
| `AlertUtil` | JavaFX `Alert` dialog builder shortcuts |
| `CaptchaGenerator` | Simple text CAPTCHA for login |
| `OTPGenerator` | One-time password generation |

#### `security/`
Security primitives used by `AuthService` and `controllers`:

| Class | Purpose |
|---|---|
| `PasswordHasher` | Wraps jBCrypt to hash and verify passwords |
| `SessionManager` | Stores and clears the currently logged-in user for the lifetime of the JVM session |

#### `navigation/`
`NavigationManager.java` — a singleton that manages the JavaFX `Stage` and handles all scene transitions. Controllers call it to navigate between screens without needing a reference to the `Stage` directly.

---

### `rento/src/main/resources/`

Static assets loaded at runtime.

#### `fxml/`
One `.fxml` file per screen, defining the UI layout declaratively. Each file is paired with a controller class of the same name (e.g. `login.fxml` ↔ `LoginController`).

#### `css/`
Contains `global.css`, a single stylesheet applied application-wide to give all screens a consistent look and feel.

#### `images/`
Placeholder directory for image assets (icons, vehicle photos, logos) used by the UI.

#### `fonts/`
Placeholder directory for custom font files loaded by the CSS or controllers.

---

## Application Workflow

```
User launches app
        │
        ▼
RentoApplication.java (entry point)
  ├─ Connects to MongoDB via MongoDBConnection
  ├─ Runs SystemCollectionBootstrapService  → ensures collections exist
  ├─ Runs DemoDataService                  → seeds demo accounts & vehicles if DB is empty
  └─ Loads landing/login scene via NavigationManager
        │
        ▼
LoginController / RegisterController
  └─ AuthService → UserDAO → MongoDB
        │  (session stored in SessionManager)
        ▼
Role-based dashboard
  ├─ Customer  → LandingController → RentController / BookingController
  │                  └─ RentalService / BookingService → RentalDAO / BookingDAO
  │                       └─ PaymentController → PaymentService → PaymentDAO
  │                            └─ ReceiptService → PDF/TXT receipt
  │
  ├─ Supplier  → SupplierDashboardController
  │                  └─ RentalService / VehicleDAO → fleet & rental management
  │
  ├─ Driver    → DriverDashboardController
  │                  └─ BookingService / BookingDAO → assigned bookings & trip lifecycle
  │
  └─ Admin     → AdminDashboardController
                    └─ AdminExportService → CSV exports & platform operations
```

Every screen transition goes through `NavigationManager`. Each controller delegates business operations to the `services` layer, which in turn calls the appropriate `dao` class. `models` objects carry data between all layers. `security` classes protect authentication, and `utils` provide cross-cutting helpers throughout.

---

## Setup Guide (Complete)

### 1) Prerequisites

- JDK **21** (required)
- MongoDB Community Server (running locally on default port `27017`)
- IDE recommended: IntelliJ IDEA / Eclipse with JavaFX support

### 2) Clone and open the project

```bash
git clone https://github.com/hariharasudhan-29507/Rento.git
cd Rento
```

Open the source root:

- `Source File/rento/src/main/java`
- `Source File/rento/src/main/resources`

### 3) Configure dependencies

This project keeps JARs locally inside:

- `Source File/jars/`

Add all required JARs from that folder to your IDE project libraries, including:

- JavaFX JARs
- MongoDB driver JARs
- `jbcrypt-0.4.jar`
- `itextpdf-5.5.13.3.jar`
- `controlsfx-11.1.2.jar`
- `gson-2.10.1.jar`
- `slf4j-api` + `slf4j-simple`

### 4) Start MongoDB

Make sure MongoDB is running locally:

- Connection string used by the app: `mongodb://localhost:27017`
- Database name: `rento_db`

### 5) Run the application

Run the main class:

- `com.rento.app.RentoApplication`

On first launch, the app seeds demo users and vehicles if collections are empty.

## Demo Accounts (seeded automatically)

- `user@rento.local`
- `driver@rento.local`
- `supplier@rento.local`
- `admin@rento.local`

Default password for seeded users: `Rento@123`

## Wiki

- Project Wiki: https://github.com/hariharasudhan-29507/Rento/wiki

## Documentation

- [Software Requirements Specification](./Documentation/SRS%20VRBS.pdf)
- [ER/Project Specification](./Documentation/SPECIFICATION%20VRBS.pdf)
- [UML Diagrams Folder](./UML%20diagrams/README.md)

## Team

| Name | Email |
|------|-------|
| Hariharasudhan A | sudanayyappan_bcs28@mepcoeng.ac.in |
| Hari Prasad V | santhiselvan74_bcs28@mepcoeng.ac.in |
| Muhammed Yousuf M | yousufilyas86bcs28@mepcoeng.ac.in |
