# Rento — Vehicle Rental & Booking System

Rento is a Java desktop application that delivers end-to-end vehicle rental and booking management. It supports the full lifecycle of a ride — from vehicle discovery and OTP-secured pickup through payment, receipt generation, and supplier fleet management — across four distinct role-based experiences: **Customer**, **Driver**, **Supplier**, and **Admin**.

---

## Key Features

| Capability | Details |
|---|---|
| **Authentication** | Registration and login with CAPTCHA challenge and BCrypt password hashing |
| **Role-Based Access** | Four roles — USER, DRIVER, SUPPLIER, ADMIN — each routed to a dedicated dashboard |
| **Vehicle Booking** | Browse, select, and book vehicles with dynamic pricing (weekend surcharge, multi-day discount, 18% tax, 25% deposit) |
| **Rental Marketplace** | Supplier-listed vehicles available for self-drive rentals; full approval and OTP-activation workflow |
| **Payment Simulation** | Credit/debit card (Luhn-validated), UPI, net banking, and wallet payment methods |
| **OTP Ride Security** | A 6-digit OTP ties payment confirmation to physical trip start; verified by the driver at pickup |
| **Receipt & Export** | Automated PDF and TXT receipt generation; admin-level full-platform data export |
| **Demo Data Seeding** | First-launch automatic seeding of demo users and vehicle fleet into MongoDB |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 (FXML + CSS) |
| Database | MongoDB (`rento_db` on `localhost:27017`) |
| DB Driver | MongoDB Java Driver Sync 5.2.1 |
| Password Security | jBCrypt 0.4 |
| PDF Generation | iText PDF 5.5.13.3 |
| JSON Handling | Gson 2.10.1 |
| Extended UI Controls | ControlsFX 11.1.2 |
| Logging | SLF4J 2.0.13 (simple backend) |

---

## Repository Layout

```text
Rento/
├── README.md               ← This file
├── WIKI.md                 ← Architecture, test cases, and module reference
├── SECURITY.md             ← Security policy
├── Documentation/          ← SRS and project specification PDFs
├── UML diagrams/           ← Mermaid class, sequence, and flow diagrams
└── Source File/            ← All application source code and bundled dependencies
    ├── jars/               ← Third-party JARs (no build tool required)
    └── rento/src/main/
        ├── java/com/rento/ ← Java source packages
        └── resources/      ← FXML layouts, CSS, fonts, images
```

---

## Sub-Repository Guide

### `Documentation/`

Contains the formal project documents produced during the specification phase.

| File | Contents |
|---|---|
| `SRS VRBS.pdf` | Software Requirements Specification — functional and non-functional requirements, use-case descriptions, and constraints for the Vehicle Rental & Booking System |
| `SPECIFICATION VRBS.pdf` | ER diagram and detailed project specification, covering data models, relationships, and system-wide design decisions |

These documents are the authoritative reference for understanding *why* certain design choices were made in the source code.

---

### `UML diagrams/`

Contains a single `README.md` that renders four Mermaid diagrams inline on GitHub:

| Diagram | What it shows |
|---|---|
| **Class Diagram** | Full domain model — `User`, `Vehicle`, `Booking`, `Rental`, `Payment`, their attributes, enumerations (`Role`, `VehicleStatus`, `BookingStatus`, etc.), and inter-entity relationships |
| **Booking Sequence Diagram** | Message flow between Customer, Driver, `BookingService`, `VehicleDAO`, `PaymentService`, and `NotificationService` across the full booking lifecycle |
| **Rental Sequence Diagram** | Message flow for the supplier-driven rental path — request, approve, OTP confirm, activate, and complete |
| **Application Flow Diagram** | Top-level flowchart of the entire application from launch through each role's workflow |

These diagrams are kept in sync with the source code and serve as the primary onboarding reference for new contributors.

---

### `WIKI.md`

Comprehensive reference document covering:

- **Architecture overview** — the five-layer model (Presentation, Service, DAO, Domain, Infrastructure)
- **Component details** — controller responsibilities, DAO-to-collection mapping, and service ownership
- **Business rules** — pricing formula, OTP flows, driver commission (15%), rental overdue penalty (25% per day), approval workflow
- **Valid and invalid test scenarios** — 38 documented scenarios covering authentication, booking, payment, and rental flows
- **Build and run notes** — dependency management approach and manual execution instructions

---

### `Source File/`

The root of all application code and runtime dependencies.

#### `jars/`

All third-party dependencies are bundled as JARs — the project does not use Maven or Gradle. These are added to the IDE classpath via the checked-in `.classpath` file.

| JAR | Purpose |
|---|---|
| `javafx-sdk-21.0.10/` + individual JavaFX JARs | UI framework — controls, FXML, graphics, media, and web modules |
| `mongodb-driver-sync-5.2.1.jar`, `mongodb-driver-core-5.2.1.jar`, `bson-5.2.1.jar` | MongoDB Java driver for synchronous database access |
| `jbcrypt-0.4.jar` | BCrypt password hashing |
| `itextpdf-5.5.13.3.jar` | PDF receipt generation |
| `controlsfx-11.1.2.jar` | Extended JavaFX UI controls |
| `gson-2.10.1.jar` | JSON serialisation and deserialisation |
| `slf4j-api-2.0.13.jar`, `slf4j-simple-2.0.13.jar` | Logging façade and simple backend |

#### `rento/src/main/java/com/rento/` — Java Source Packages

| Package | Role in the application |
|---|---|
| `app/` | `RentoApplication.java` — the JavaFX `Application` entry point. Initialises the MongoDB connection, runs the collection bootstrap and demo-data seed, then loads the landing scene. |
| `controllers/` | One controller per screen. Each class handles UI events for its paired FXML view, delegates all business logic to the `services` layer, and calls `NavigationManager` for scene transitions. |
| `services/` | Business-logic layer. Services are the only callers of DAOs and the only layer that enforces domain rules (pricing, OTP generation, approval workflows, receipt formatting). |
| `dao/` | Data-access objects. Each DAO class owns all MongoDB reads and writes for one collection and maps between Java model objects and BSON documents. |
| `models/` | Plain Java domain objects — `User`, `Vehicle`, `Booking`, `Rental`, `Payment`, `PaymentMethodProfile` — passed between all layers. |
| `security/` | `PasswordHasher` (BCrypt wrap) and `SessionManager` (JVM-scoped logged-in user state). |
| `navigation/` | `NavigationManager` singleton — manages the JavaFX `Stage` and all scene transitions so controllers never hold a direct `Stage` reference. |
| `utils/` | Cross-cutting helpers: `MongoDBConnection` (singleton client), `ValidationUtil`, `DateTimeUtil`, `AlertUtil`, `CaptchaGenerator`, `OTPGenerator`. |

**Key controllers:**

| Controller | Responsibility |
|---|---|
| `LoginController` / `RegisterController` | Credential handling, CAPTCHA challenge, role-based post-login routing |
| `LandingController` | Guest/authenticated entry navigation |
| `BookingController` / `BookingDetailController` | Vehicle browse, filter, pricing preview, and booking creation |
| `PaymentController` / `PaymentSetupController` | Payment method validation, booking payment confirmation, OTP display |
| `RentController` | Supplier marketplace listing and renter-side rental request flow |
| `DriverDashboardController` | Accept pending bookings, verify customer OTP, complete trips, view metrics |
| `SupplierDashboardController` | Add/update vehicles, approve or reject rental requests, issue activation OTPs |
| `AdminDashboardController` | Platform metrics, vehicle listing approval, user management, full export |
| `ProfileController` | User profile, wallet balance, notifications, and dashboard routing |

**Services and their domain ownership:**

| Service | Responsibility |
|---|---|
| `AuthService` | Registration, login, logout, wallet initialisation |
| `BookingService` | Booking creation, dynamic pricing, deposit deduction, driver assignment, OTP ride start, completion and commission |
| `RentalService` | Full rental lifecycle: request → approve → OTP confirm → active → completed/overdue with penalty |
| `PaymentService` | Payment method validation and simulated payment persistence |
| `ReceiptService` | PDF and TXT receipt generation for bookings and rentals |
| `NotificationService` | In-app notification persistence and export |
| `AdminExportService` | Full platform snapshot export to file |
| `DemoDataService` | First-launch seeding of users and vehicle fleet |
| `SystemCollectionBootstrapService` | Ensures all required MongoDB collections exist before any DAO runs |

**DAOs and their MongoDB collections:**

| DAO | Collection |
|---|---|
| `UserDAO` | `users` |
| `VehicleDAO` | `vehicles` |
| `BookingDAO` | `bookings` |
| `RentalDAO` | `rentals` |
| `PaymentDAO` | `payments` |
| `PaymentMethodDAO` | `payment_methods` |

#### `rento/src/main/resources/` — Static Assets

| Directory | Contents |
|---|---|
| `fxml/` | One `.fxml` layout file per screen, paired by name with its controller (e.g. `login.fxml` ↔ `LoginController`) |
| `css/` | `global.css` — single application-wide stylesheet for consistent visual styling |
| `fonts/` | Custom font files referenced by the CSS |
| `images/` | Icons, vehicle photos, and logo assets used by the UI |

---

## Architecture & Layer Connectivity

Rento is structured as a strict five-layer desktop application. Data flows in one direction — UI events travel inward to the database, and results flow back outward to the screen.

```
┌─────────────────────────────────────────────────────────────────┐
│  Presentation Layer  (JavaFX — FXML + controllers)              │
│  Handles all UI events, renders data, drives navigation         │
└────────────────────────────┬────────────────────────────────────┘
                             │  calls
┌────────────────────────────▼────────────────────────────────────┐
│  Service Layer  (Business Logic)                                │
│  Enforces domain rules, orchestrates multi-step workflows,      │
│  generates OTPs, calculates pricing, triggers notifications     │
└────────────────────────────┬────────────────────────────────────┘
                             │  reads/writes via
┌────────────────────────────▼────────────────────────────────────┐
│  DAO Layer  (Data Access Objects)                               │
│  Translates model objects to/from BSON; owns all MongoDB I/O    │
└────────────────────────────┬────────────────────────────────────┘
                             │  persists to
┌────────────────────────────▼────────────────────────────────────┐
│  MongoDB  (rento_db @ localhost:27017)                          │
│  Collections: users, vehicles, bookings, rentals, payments,     │
│              payment_methods, notifications                     │
└─────────────────────────────────────────────────────────────────┘

Cross-cutting infrastructure (used by all layers above):
  • MongoDBConnection  — singleton MongoClient/MongoDatabase
  • SessionManager     — JVM-scoped logged-in user
  • NavigationManager  — JavaFX Stage and scene transitions
  • utils/security     — validation, hashing, OTP, CAPTCHA, alerts
```

**End-to-end request flow:**

```
User action in UI
      │
      ▼
Controller  ──calls──►  Service  ──calls──►  DAO  ──reads/writes──►  MongoDB
      ▲                    │                                              │
      │                    │  (model objects carried between all layers)  │
      └────────────────────◄──────────────────────────────────────────────
                    result returned to controller for UI update
```

**Application startup sequence:**

```
RentoApplication.start()
  ├─ MongoDBConnection.getInstance()          → open MongoDB client
  ├─ SystemCollectionBootstrapService.run()   → ensure collections exist
  ├─ DemoDataService.seedIfEmpty()            → populate demo users & fleet
  └─ NavigationManager.loadScene("landing")  → display initial screen
```

**Role routing after login:**

```
AuthService.login()  →  SessionManager.setUser()
        │
        ▼
   User.role  ──►  USER      →  LandingController  →  Booking / Rental flows
              ──►  DRIVER    →  DriverDashboardController
              ──►  SUPPLIER  →  SupplierDashboardController
              ──►  ADMIN     →  AdminDashboardController
```

---

## Demo Accounts

The application seeds these accounts automatically on first launch. All share the same default password.

| Role | Email | Password |
|---|---|---|
| Customer | `user@rento.local` | `Rento@123` |
| Driver | `driver@rento.local` | `Rento@123` |
| Supplier | `supplier@rento.local` | `Rento@123` |
| Admin | `admin@rento.local` | `Rento@123` |

---

## Documentation & References

| Resource | Link |
|---|---|
| Software Requirements Specification | [Documentation/SRS VRBS.pdf](./Documentation/SRS%20VRBS.pdf) |
| ER / Project Specification | [Documentation/SPECIFICATION VRBS.pdf](./Documentation/SPECIFICATION%20VRBS.pdf) |
| UML Diagrams | [UML diagrams/README.md](./UML%20diagrams/README.md) |
| Architecture & Test Reference (Wiki) | [WIKI.md](./WIKI.md) |
| GitHub Wiki | https://github.com/hariharasudhan-29507/Rento/wiki |

---

## Team

| Name | Email |
|---|---|
| Hariharasudhan A | sudanayyappan_bcs28@mepcoeng.ac.in |
| Hari Prasad V | santhiselvan74_bcs28@mepcoeng.ac.in |
| Muhammed Yousuf M | yousufilyas86bcs28@mepcoeng.ac.in |
