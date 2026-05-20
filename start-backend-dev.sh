#!/bin/bash
export DB_URL=jdbc:postgresql://localhost:5433/dipdv_dev
export DB_USERNAME=dipdv_app
export DB_PASSWORD=dipdv_local_2025
cd "$(dirname "$0")/backend" && mvn spring-boot:run -Dspring-boot.run.profiles=dev
