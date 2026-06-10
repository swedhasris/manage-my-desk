# Ticklora - ITSM Ticketing System

A comprehensive IT Service Management (ITSM) ticketing system built with React, TypeScript, Spring Boot, Node.js microservices, and MySQL.

## 🚀 Target Architecture & Features

- **Target Distribution:**
  - **Frontend (35%):** React 18 + TypeScript + Vite. Styling with Tailwind CSS and Lucide Icons.
  - **Backend (50%):** Spring Boot (Java 17) + Node.js/TypeScript microservices.
  - **Database (15%):** MySQL.
- **Complete Ticket Management** - Create, assign, track, and resolve tickets.
- **User Management** - Role-based access control with user creation.
- **Group Management** - Team and group organization.
- **AI-Powered Assistance** - Kiru AI chatbot and ticket classification via Google Gemini.
- **SLA Management** - Service Level Agreement monitoring and escalation.
- **Knowledge Base & Service Catalog** - IT service catalog and self-service knowledge base.
- **Timesheet System** - Time tracking and approval workflow.
- **Change & Problem Management** - ITIL-compliant controls and root cause analysis (RCA).

---

## 🛠️ Tech Stack

- **Frontend:** React 18 + TypeScript + Vite + Tailwind CSS + Recharts
- **Backend:** 
  - **Spring Boot 3.4.2** (Java 17, Spring Data JPA, Hibernate)
  - **Node.js + Express + TypeScript** (microservices architecture)
- **Database:** MySQL 8+ (SQLite for local cached tables)
- **AI Integration:** Google Gemini API
- **Email Integrations:** Microsoft Graph API + SMTP (M365)

---

## 📦 Installation & Setup

### Prerequisites
- Node.js 18+
- Java 17+ (JDK)
- Maven 3.8+
- MySQL Server 8+

### Database Configuration
1. Create a MySQL database (e.g. `connectit_db_ticket`).
2. Run `mysql-schema.sql` to initialize the database tables, relationships, and index configurations:
   ```bash
   mysql -u root -p connectit_db_ticket < mysql-schema.sql
   ```

### Configuration
Create a `.env` file in the root and in the subdirectories:
```env
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3307
MYSQL_USER=root
MYSQL_PASSWORD=
MYSQL_DATABASE=connectit_db_ticket
GEMINI_API_KEY=your_gemini_api_key_here
```

### Starting the Microservices Stack
Navigate to the `microservices` folder and run the start script:
```powershell
cd microservices
.\start-all.ps1
```
This script starts:
- **Core Service** (Port 3001)
- **Integration Service** (Port 3002)
- **Activity Service** (Port 3003)
- **Main React Frontend & Gateway** (Port 3000)

---

## 🏗️ Project Structure
```
Nexus_Project_Ticket/
├── src/                      # React frontend components and pages
├── microservices/            # Backend services
│   ├── core-service-springboot/ # Java Spring Boot backend service
│   ├── gateway/              # Reverse proxy gateway
│   ├── core-service/         # Node.js core service
│   ├── activity-service/     # AI activity tracker
│   └── integration-service/  # Email and Gemini AI integration
├── mysql-schema.sql          # MySQL database schema setup
├── server.ts                 # Express gateway / monolith backup
└── package.json              # Main Node.js configuration
```
