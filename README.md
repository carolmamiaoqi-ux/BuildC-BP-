# TruePulse Capstone

TruePulse is a health data collection and visualization platform focused on sit-stand protocols and breathing exercises. This repository contains the core application and data collection tools.

## 📁 Project Structure

- `truepulse-health/`: The main React + TypeScript web application built with Vite and Tailwind CSS.
- `requirements.txt`: Environment and core dependency specifications.
- `pregnancy_icon.svg` & `postpartum_icon.svg`: Asset files for the application.

## 🚀 Getting Started

To set up the project locally, follow these steps:

### 1. Prerequisites

Ensure you have the environment requirements met as listed in [requirements.txt](./requirements.txt):
- **Node.js**: v18.0.0 or higher
- **npm**: v9.0.0 or higher

### 2. Installation

Navigate to the application directory and install dependencies:

```bash
cd truepulse-health
npm install
```

### 3. Development

Start the development server:

```bash
npm run dev
```

The application will be available at `http://localhost:5173`.

## 🛠️ Requirements & Dependencies

The [requirements.txt](./requirements.txt) file provides a consolidated view of the necessary environment and core libraries. While the project uses `npm` for package management via `package.json`, the `requirements.txt` serves as a high-level reference for the required setup.

## ✨ Features

- **Sit-Stand Protocol**: Guided transitions with visual progress.
- **Breathing Exercises**: HRV-sync visualizations.
- **Profile Management**: Personalized health data tracking.
- **Motion System**: Smooth, aesthetic transitions and animations.
