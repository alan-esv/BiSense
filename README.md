# Sistema IoT de Monitoreo Energético Bidireccional

BiSense es una solución integral de hardware y software diseñada para viviendas con sistemas de paneles solares. Permite a los usuarios monitorear en tiempo real su consumo eléctrico frente a la inyección de energía excedente a la red, facilitando la toma de decisiones para maximizar la eficiencia energética.

## 📑 Documentación Técnica
Este repositorio está respaldado por un reporte técnico académico sobre el diseño e implementación del prototipo del medidor bidireccional. 
👉 **[Leer el Reporte Técnico Completo (PDF) aquí](./ruta-a-tu-reporte.pdf)**

## ✨ Características Principales

* **Sincronización IoT Estricta:** El hardware procesa lecturas automáticas cada 15 minutos y diagnostica la salud de la conexión inalámbrica en intervalos de 30 segundos.
* **Diagnóstico en Pantalla:** Interfaz tipo semáforo que alerta visualmente al usuario si el hardware pierde conexión (ej. más de 5 minutos en silencio).
* **Análisis de Datos Dinámico:** Gráficas interactivas con filtros de visualización (Consumo, Inyección o Combinado) y segmentación temporal (día, semana, mes, año).
* **Motor de Reportes Financieros:** Generación de documentos PDF nativos desde la app que incluyen un algoritmo de estimación de facturación basado en la Tarifa Doméstica Tipo 1 de la CDMX.

## 🛠️ Arquitectura y Stack Tecnológico

* **Aplicación Móvil:** Kotlin, Android Studio.
* **Hardware Sensorial:** Microcontrolador ESP32, Sensores de Voltaje y Corriente.
* **Base de Datos & Cloud:** Google Firebase (Realtime Database / Firestore).
* **Diseño UI:** Figma.

## 📸 Evidencia del Sistema

### 1. Hardware (Prototipo ESP32)
*(Inserta aquí una o dos fotos de tu medidor físico conectado, mostrando el circuito o la placa).*

### 2. Aplicación Móvil
*(Inserta aquí capturas de pantalla de tu app: 1. El Dashboard principal con el diagnóstico, 2. La vista de Gráficas, 3. Un ejemplo del PDF generado).*

## 🚀 Estructura del Repositorio

* `/app-android`: Contiene el código fuente en Kotlin de la aplicación móvil.
* `/hardware`: Contiene los scripts de lectura y transmisión de datos hacia Firebase.
* `/docs`: Contiene el manual de usuario, diagramas de circuito y el artículo técnico.

