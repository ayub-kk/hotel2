# Hotel Booking System

---

## 1) Технологический стек

- **Язык**: Java **21+**
- **Сборка**: **Maven** (multi-module / parent `pom.xml`)
- **Фреймворк**: **Spring Boot 3.5.8**
- **Spring Cloud**: **2025.0.0** (Eureka + Gateway)
- **База данных**: **H2 (in-memory)** — отдельная БД в каждом сервисе (учебный режим)
- **API-документация**: **OpenAPI/Swagger (springdoc-openapi)**
- **Тестирование**:
  - **JUnit 5 + Spring Boot Test** (интеграционные тесты)
  - **Postman** (E2E коллекция + environment + автотесты/assert’ы)
- **IDE**: **IntelliJ IDEA**

---

## 2) Архитектура и роли сервисов

Проект реализован как микросервисная система с единой точкой входа через `api-gateway`, сервисным discovery через `discovery-server (Eureka)` и согласованием бронирования через сагу в `booking-service` с компенсацией в `hotel-service`.

## 3) Быстрый запуск

Открыть 4 терминала в корне проекта и запустить:

1) Eureka:
```bash
mvn -pl discovery-server spring-boot:run
```

2) Hotel Service:
```bash
mvn -pl hotel-service spring-boot:run
```

3) Booking Service:
```bash
mvn -pl booking-service spring-boot:run
```

4) API Gateway:
```bash
mvn -pl api-gateway spring-boot:run
```

Порты по умолчанию:
- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- Booking Service: `http://localhost:8081`
- Hotel Service: `http://localhost:8082`

![Eureka Status](docs/screenshots/eureka-page-ok.png)

---

## 4) Предзаполнение данных

После старта сервисов автоматически создаются тестовые данные:

### Пользователи (booking-service)
- `admin@local / admin123` (роль **ADMIN**)
- `user@local / user123` (роль **USER**)

### Отели и комнаты (hotel-service)
- 3 отеля и 9 комнат (номера 101/102/201 …)

---

### 5) Как запустить

Запуск всех тестов из корня репозитория:

```bash
mvn test
```

Запуск тестов конкретного сервиса:

```bash
mvn -pl booking-service -DskipTests=false test
mvn -pl hotel-service -DskipTests=false test
```

Запуск одного тестового класса:

```bash
mvn -pl booking-service -Dtest=BookingSagaIT test
mvn -pl hotel-service -Dtest=RoomConfirmConcurrencyIT test
```
