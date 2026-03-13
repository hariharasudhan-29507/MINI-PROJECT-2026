# Vehicle Rental and Booking System (VRBS)

A standalone desktop application for managing vehicle rentals and cab bookings, developed as a Mini Project II (23CCS452) at Mepco Schlenk Engineering College.

## Overview

VRBS enables users to rent vehicles (E-Bikes, E-Cars, Normal Bikes, Cars) on an hourly basis and book cab services with automatic driver allocation. Administrators have full control over inventory, suppliers, drivers, and system reports.

**Tech Stack:** Java Swing · Oracle 21c / MongoDB · JDBC (ojdbc11)

## Features

**User**
- Secure login with hashed password authentication (account locks after 3 failed attempts)
- Browse and rent available vehicles by type with hourly pricing
- Multi-vehicle rental cart (up to 5 vehicles per session)
- Cab booking with nearest-driver auto-assignment using Euclidean distance
- View rental and booking history with date, vehicle type, and payment filters
- PDF receipt generation per transaction

**Admin**
- Full CRUD on rentals, bookings, users, drivers, and suppliers
- Vehicle service status management (marks vehicle as unavailable during service)
- Revenue and utilization reports exportable as PDF/Excel
- Admin action audit log (read-only)
- User account unlock

## Pricing Rules

- Minimum rental: 1 hour · Maximum: 24 hours per session
- Peak hour surcharge (1.5× base rate): 7–10 AM and 5–9 PM
- Late return penalty: ₹50/hour after a 1-hour grace period
- Cancellation: full refund within 15 minutes, 50% fee thereafter

## Database Schema

The system uses Oracle 21c with the following core entities:

`user` · `vehicle` · `supplier` · `driver` · `rental` · `rental_cart_item` · `booking` · `payment` · `vehicle_service_record` · `rental_history` · `admin_action_log` · `session`

### ER Diagram

[View ER Diagram](./ER%20schema%20VRBS.png)

### Use Case Diagram

[View Use Case Diagram](./USE%20CASE%20DIAGRAM%20VRBS.png)

## System Constraints

- Single desktop deployment — simultaneous multi-device access not supported
- Oracle 21c exclusive — not compatible with MySQL or PostgreSQL without full DAO rewrite
- One active session per user at a time; auto-terminates after 15 minutes of inactivity
- Vehicles under service cannot be rented or booked

## Documents

| Document | Description |
|----------|-------------|
| [SRS VRBS.pdf](./SRS%20VRBS.pdf) | Software Requirements Specification |
| [SPECIFICATION VRBS.pdf](./SPECIFICATION%20VRBS.pdf) | ER Specification |

## References

- IEEE Std 830-1998: Software Requirements Specifications
- Oracle 21c JDBC Documentation
- Java Swing Developer Guide

## Author

**Hariharasudhan**
Sophomore (CSE) — Mepco Schlenk Engineering College, Sivakasi, Tamil Nadu

Reach me: [sudanayyappan_bcs28@mepcoeng.ac.in](mailto:sudanayyappan_bcs28@mepcoeng.ac.in)

## Team Members

| Name | Status |
|------|--------|
| Hariharasudhan | Author |
| Hari Prasad V | Will be added |
| Muhammed Yousuf M | Will be added |

---

*Mini Project II (23CCS452) — Mepco Schlenk Engineering College, Sivakasi*
