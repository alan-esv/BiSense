# Sistema IoT de Monitoreo Energético Bidireccional

BiSense es una solución integral de hardware y software diseñada para viviendas con sistemas de paneles solares. Permite a los usuarios monitorear en tiempo real su consumo eléctrico frente a la inyección de energía excedente a la red, facilitando la toma de decisiones para maximizar la eficiencia energética.

## ✨ Características Principales

* **Sincronización IoT:** El hardware procesa lecturas automáticas cada 15 minutos y diagnostica la salud de la conexión inalámbrica en intervalos de 30 segundos.
* **Diagnóstico en Pantalla:** Interfaz tipo semáforo que alerta visualmente al usuario si el hardware pierde conexión (ej. más de 5 minutos en silencio).
* **Análisis de Datos Dinámico:** Gráficas interactivas con filtros de visualización (Consumo, Inyección o Combinado) y segmentación temporal (día, semana, mes, año).
* **Motor de Reportes Financieros:** Generación de documentos PDF nativos desde la app que incluyen un algoritmo de estimación de facturación basado en la Tarifa Doméstica Tipo 1 de la CDMX.

## 🛠️ Arquitectura y Stack Tecnológico

* **Aplicación Móvil:** Kotlin, Android Studio.
* **Hardware Sensorial:** Microcontrolador ESP32, Sensores de Voltaje y Corriente.
* **Base de Datos & Cloud:** Google Firebase.
* **Diseño UI:** Figma.
