# ClothStore — Production-Ready Clothing E-Commerce

A full-stack clothing store application with **Angular 17 + Bootstrap 5** frontend and **Spring Boot 3** backend, using **H2 in-memory database** (easily switchable to PostgreSQL).

---

## Features

### Customer
- Browse products with search, category filters, and pagination
- Beautiful product cards with sale badges and stock alerts
- Shopping cart (persisted in localStorage)
- Place orders with shipping address
- View order history
- Request returns for delivered orders
- Register / Login with JWT authentication
- JWT access + refresh token rotation (automatic silent refresh)
- Access token blacklisting on logout (immediate invalidation via jti)

### Admin
- Dashboard with sales, orders, stock, returns stats
- Low-stock alerts
- Recent orders overview
- Full product CRUD (create, edit, deactivate)
- Manage order statuses (Pending → Confirmed → Shipped → Delivered / Cancelled)
- Approve / reject / complete return requests
- Stock is automatically restored on cancel or approved return

---

## Tech Stack

| Layer     | Technology                          |
|-----------|-------------------------------------|
| Frontend  | Angular 17 (standalone), Bootstrap 5, Bootstrap Icons |
| Backend   | Spring Boot 3.2, Spring Security, JWT, JPA |
| Database  | H2 (in-memory) — ready for PostgreSQL |
| Auth      | JWT access tokens + rotating refresh tokens |

## Used Tools

| Layer    | Technology                       |
|----------|----------------------------------|
| Frontend | https://dash.cloudflare.com/     |
| Backend  | https://render.com/              |
| Database | https://console.neon.tech/       |
| Images   | https://console.cloudinary.com/  |
---


## Quick Start

### Prerequisites
- **Java 17+**
- **Maven 3.8+**
- **Node.js 18+** and npm

### 1. Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs at **http://localhost:8080**

- H2 Console: http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:mem:clothstore` · User: `sa` · Password: *(empty)*

### 2. Start Frontend

```bash
cd frontend
npm install
npm start
```

Frontend runs at **http://localhost:4200**

---



---

## Switching to PostgreSQL

1. Uncomment the PostgreSQL dependency in `backend/pom.xml`
2. In `backend/src/main/resources/application.yml`:
   - Comment out the H2 datasource block
   - Uncomment the PostgreSQL datasource block
   - Update username/password
   - Change dialect to `org.hibernate.dialect.PostgreSQLDialect`
3. Create database: `CREATE DATABASE clothstore;`
4. Restart the backend

The JPA entities and repositories need **no code changes**.

---

## API Overview

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | — | Register customer (returns access + refresh tokens) |
| POST | `/api/auth/login` | — | Login (returns access + refresh tokens) |
| POST | `/api/auth/refresh` | — | Rotate refresh token, get new access token |
| POST | `/api/auth/logout` | — | Blacklist access token + revoke refresh token |
| POST | `/api/auth/logout-all` | Access token | Revoke all sessions + blacklist current access token |
| GET | `/api/products` | — | List products (paginated, filterable) |
| GET | `/api/products/{id}` | — | Product detail |
| GET | `/api/categories` | — | List categories |
| POST | `/api/orders` | Customer | Place order |
| GET | `/api/orders/my` | Customer | My orders |
| POST | `/api/returns` | Customer | Request return |
| GET | `/api/admin/dashboard` | Admin | Dashboard stats |
| CRUD | `/api/admin/products` | Admin | Manage products |
| PATCH | `/api/orders/{id}/status` | Admin | Update order status |
| PATCH | `/api/returns/{id}/status` | Admin | Update return status |

---

## Project Structure

```
cloth-store/
├── backend/                 # Spring Boot
│   ├── src/main/java/com/clothstore/
│   │   ├── config/          # Security, DataInitializer
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── entity/          # JPA entities
│   │   ├── repository/      # Spring Data repos
│   │   ├── security/        # JWT filter & util
│   │   ├── service/         # Business logic
│   │   └── exception/       # Global exception handler
│   └── src/main/resources/
│       └── application.yml
└── frontend/                # Angular 17
    └── src/app/
        ├── core/            # Services, guards, interceptors, models
        ├── features/        # Home, Products, Cart, Auth, Orders, Admin
        └── shared/
```

---

## Sample Data

On first startup the backend seeds:
- Admin + Customer accounts
- 4 categories (Men, Women, Kids, Accessories)
- 12 sample products with Unsplash images

---

## License

MIT — free to use and modify.
