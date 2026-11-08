# Resuelve Precios 🛒🇻🇪

**Resuelve Precios** es una aplicación nativa para Android (Kotlin) diseñada para comparar en tiempo real los precios de productos de la canasta básica y despensa en los principales supermercados de Venezuela (**Gama**, **Forum SuperMayorista** y **Central Madeirense**), integrando la tasa oficial del Banco Central de Venezuela (BCV) y optimizando el costo total de la cesta de compras.

---

## 🌟 Características Principales

1. **Catálogo Maestro & Digestión Inteligente (Entity Resolution):**
   - Agrupación algorítmica de productos basada en tokens y similitud fonética/Jaccard indiferente al orden (`ProductEntityResolver`).
   - Normalización estandarizada de marcas, categorías y pesos/volúmenes (ej. *192g*, *1kg*, *1L*).
   - Fusión de ofertas de distintos supermercados bajo una única tarjeta maestra con selector dinámico de comercios y sucursales.

2. **Búsqueda Avanzada & Relevancia Estricta:**
   - Motor de scoring (`RelevanceScorer`) que prioriza coincidencias exactas de tokens y prefijos antes que ordenamiento por precio, eliminando falsos positivos.
   - Expansión semántica para búsquedas cotidianas (ej. "desayuno" $\rightarrow$ Harina P.A.N., café, margarina).
   - Tolerancia a errores ortográficos o fonéticos venezolanos comunes.
   - Autocompletado predictivo con historial de búsquedas recientes.

3. **Escáner Inteligente de Códigos de Barra:**
   - Escaneo de códigos de barra físicos de empaques (EAN-13, EAN-8, UPC) mediante la cámara.
   - Diccionario local instantáneo de productos venezolanos de alta rotación (Mavesa, Harina P.A.N., Arroz Mary, Club Social, Puig, etc.).
   - Fallback en segundo plano con la API de **Open Food Facts** para identificación automática de productos no registrados localmente.

4. **Mi Cesta de Compras Consolidada:**
   - Lista interactiva de compras con control de cantidades e indicador de productos comprados.
   - Evaluación comparativa en tiempo real del costo de la cesta completa en cada supermercado: calcula cuánto costaría la lista en Forum, Gama o Central Madeirense y destaca la alternativa con mayor ahorro.

5. **Conversión Multimoneda en Tiempo Real:**
   - Precios mostrados simultáneamente en Dólares de referencia (USD) y Bolívares (VES).
   - Tasa oficial BCV actualizada dinámicamente con múltiples fuentes de respaldo (Gama, Forum Zupper, DolarApi).

6. **Rendimiento Nativo & Caché Offline:**
   - Patrón **Stale-While-Revalidate** con Room Database y OkHttp para respuestas instantáneas (< 5 ms desde SQLite) y refresco asíncrono en background.
   - Cero dependencias pesadas: contenedor de dependencias manual (`AppContainer`), ViewBinding y optimización con R8 ProGuard (APK ultra liviano ~1.9 MB).

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** Kotlin 2.0+
- **Arquitectura:** MVVM + Clean Architecture / Repository Pattern
- **UI:** Android ViewBinding, Google Material Design 3, Shimmer layouts
- **Asincronía & Reactividad:** Kotlin Coroutines & StateFlow / SharedFlow / Channels
- **Persistencia Local:** Room Database (SQLite) & SharedPreferences
- **Red:** OkHttp 4 con interceptores de reintento, caché y headers
- **Escáner:** JourneyApps ZXing Barcode Scanner
- **Carga de Imágenes:** Coil
- **Pruebas:** JUnit 4 unit tests con cobertura de scrapers, normalización, scoring y entidades

---

## 🚀 Compilación y Ejecución

### Requisitos
- JDK 17
- Android SDK (compileSdk 35, minSdk 24)

### Comandos de Gradle

Para compilar la versión de desarrollo:
```bash
./gradlew assembleDebug
```

Para ejecutar las pruebas unitarias:
```bash
./gradlew testDebugUnitTest
```

Para generar el APK de producción firmado y optimizado con R8:
```bash
./gradlew assembleRelease
```
El APK resultante se genera en `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 Licencia

Desarrollado por [Andres Fermin](https://github.com/Zhenro). Todos los derechos reservados.
