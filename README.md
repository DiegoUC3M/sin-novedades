# Sin Novedades

App personal de Android que **tapa el botón Novedades de WhatsApp e impide pulsarlo** mediante una cubierta opaca. No requiere root ni modificar la APK de WhatsApp.

## Descargar e instalar

La APK firmada está en [`dist/sin-novedades-0.2.0.apk`](dist/sin-novedades-0.2.0.apk). Si el proyecto se publica en GitHub, abre ese archivo y usa **Download raw file**.

**Si ya instalaste la 0.1.0, instala esta APK encima.** Mantiene el identificador y la firma de la app anterior. No es necesario desinstalarla para actualizar.

1. Descarga e instala la APK. Android puede pedirte autorizar instalaciones desde la app con la que la abras.
2. Abre **Sin Novedades** y pulsa **Activar accesibilidad**.
3. En **Ajustes → Accesibilidad → Aplicaciones o servicios instalados**, activa **Sin Novedades**.
4. Si Android muestra **Ajustes restringidos**, ve a **Ajustes → Aplicaciones → Sin Novedades → ⋮ → Permitir ajustes restringidos**, y vuelve al paso 3. La ubicación puede variar en Samsung.
5. Abre WhatsApp en Chats. El botón Novedades debería quedar cubierto y no responder a los toques.
6. Si se distingue el rectángulo, entra en **Elegir color** y ajusta el fondo a tu tema de WhatsApp. También admite un color hexadecimal personalizado.

Para desactivarla, apaga **Cubrir y bloquear el botón**, desactiva su servicio en Accesibilidad o desinstala la app.

## Si tiene permiso pero no aparece la cubierta

Abre WhatsApp con la barra inferior visible durante unos segundos. Vuelve a Sin Novedades y consulta **Última comprobación en WhatsApp**. Pulsa **Copiar diagnóstico** y pega el resultado en la conversación para investigar el caso concreto.

La versión 0.2.0 distingue entre permiso concedido y servicio conectado. El resultado de WhatsApp se conserva cuando vuelves a la app, de modo que puede indicar si falta la barra, está visible el editor o el teclado, hay otra cubierta activa o Android rechazó la ventana. El diagnóstico contiene únicamente datos técnicos, tipos de pestaña reconocidos y coordenadas; no mensajes, nombres de contactos, descripciones originales ni capturas.

### Cambios de la 0.2.0

- Solicita también los contenedores que Android considera no importantes para accesibilidad. La versión anterior necesitaba uno de esos contenedores para reconocer la barra, pero no pedía acceso a ellos.
- Recorre los descendientes de contenedores invisibles y reconoce botones que exponen una acción de selección o semántica de pestaña.
- Admite una fila completa de pestañas con semántica independiente aunque el árbol accesible no exponga una barra común. Sigue exigiendo Chats, Novedades y otra pestaña, con límites válidos y sin solapamiento sobre Novedades.
- Los eventos continuos ya no aplazan una inspección pendiente. Se reintenta mientras WhatsApp está delante, aunque todavía no se haya colocado la cubierta.
- Muestra el resultado real de la última inspección y registra las excepciones por su tipo, sin guardar el contenido de la pantalla.

## Qué hace

- Localiza una barra inferior con Chats, Novedades y otra pestaña reconocida.
- Usa los límites reales del botón pulsable. No calcula su posición con coordenadas fijas ni amplía la cubierta sobre botones sin etiqueta.
- Coloca una ventana de accesibilidad opaca que absorbe los toques dentro de ese rectángulo; el resto de la pantalla permanece utilizable.
- Retira la cubierta al dejar de detectar la barra, al entrar en un chat con editor, al aparecer el teclado, al cambiar de aplicación o al bloquear el móvil.
- Permite pausar el bloqueo y cambiar el color de la cubierta.

## Alcance de esta versión

**Versión 0.2.0:** la compilación, la firma y las pruebas automáticas de detección están verificadas. **Todavía no se ha probado en el móvil ni en la versión concreta de WhatsApp del usuario.** El diagnóstico está pensado para identificar diferencias de esa interfaz.

- Android 8.0 o posterior; compilada con SDK 36 y orientada a Android 16.
- Reconoce etiquetas en español e inglés. Se admite WhatsApp y se intenta reconocer la barra de WhatsApp Business.
- Cubre el botón: no borra la pestaña, no reorganiza la barra y no vuelve a Chats automáticamente.
- No impide entrar mediante enlaces directos, otros servicios de accesibilidad u otros accesos que WhatsApp pueda incorporar.
- La detección es reactiva: puede haber un breve intervalo antes de que aparezca la cubierta al abrir o cambiar una pantalla.
- Si la estructura no se reconoce con suficiente confianza, no pone ninguna cubierta. Una actualización de WhatsApp puede exigir adaptar el detector.
- No actúa sobre barras laterales de tabletas ni pantallas externas.

## Privacidad y permisos

Android concede al servicio de Accesibilidad la capacidad de acceder al contenido visible. La app solo busca etiquetas y geometría de navegación en WhatsApp, y utiliza eventos de cambio de ventana para retirar la cubierta al salir. Guarda localmente un diagnóstico técnico de la última comprobación de WhatsApp; solo se copia al portapapeles cuando pulsas el botón correspondiente. No guarda ni registra mensajes, no realiza capturas, no tiene permiso de Internet y no incluye analítica ni bibliotecas de terceros.

No utiliza `performAction`, `dispatchGesture` ni `performGlobalAction`: no pulsa botones ni cambia de pestaña por el usuario. El servicio está protegido por el permiso de sistema `BIND_ACCESSIBILITY_SERVICE` y solo se activa desde Ajustes.

## Compilar

### Método verificado: Android SDK y Python

Requisitos: **JDK 17**, **Python 3**, **Android SDK Platform 36** y **Build-Tools 36.0.0**. El SDK se puede instalar con Android Studio.

```bash
python3 scripts/build.py --sdk /ruta/al/Android/Sdk
```

En Windows:

```powershell
python scripts/build.py --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

El script compila sin Gradle ni dependencias de Python, ejecuta las pruebas, convierte las clases a DEX, alinea la APK, la firma y verifica la firma. El resultado y su SHA-256 quedan en `dist/`.

Pruebas sin instalar el SDK:

```bash
python3 scripts/build.py --test-only
```

### Android Studio

El proyecto también incluye configuración para **Android Gradle Plugin 8.13.0**, **Gradle 8.13** y JDK 17. Abre la carpeta del proyecto y configura esa distribución de Gradle; no se incluye el wrapper. Este camino no se ha ejecutado en el entorno de entrega.

```bash
gradle :app:assembleDebug
```

La APK de depuración de Gradle utiliza una firma diferente a la APK entregada. Para mantener actualizaciones compatibles, utiliza `scripts/build.py` con el almacén de firma original.

## Firma y futuras actualizaciones

La APK se firma con una clave privada propia; no es la firma de WhatsApp. Su certificado SHA-256 es:

```text
0a08bf1a725df8c37eb185757130c8b1c0d21af0dae73bc7f289e357990bad54
```

En la primera compilación, el script crea `.signing/sin-novedades.p12` y `.signing/password.txt`. **No se deben subir a GitHub.** El archivo privado de firma entregado por separado permite conservar la misma identidad en futuras APK. Extrae su carpeta `.signing` dentro del proyecto antes de recompilar una actualización. Sin ese almacén, se generará una firma distinta y Android no aceptará la APK como actualización de la instalación existente.

## Comprobaciones realizadas

Las 25 comprobaciones de `tests/TabDetectorTest.java` cubren el tamaño exacto del botón, etiquetas en español e inglés, mensajes con nombres parecidos, etiquetas duplicadas, barras incompletas, editores de chat, controles fuera de la barra, alta densidad, orientación horizontal, pestañas vecinas sin etiqueta, contenedores omitidos y botones que se solapan. No sustituyen a probar la APK en el móvil.

En un emulador AOSP con Android 16 se han comprobado la instalación de la APK firmada, la sustitución de una instalación con la misma firma, la conexión del servicio y la presentación de la pantalla principal. La prueba de bloqueo de toques no se pudo completar: el emulador sin aceleración mostraba repetidamente avisos de sistema sin respuesta (`Process system` y `System UI`) que impedían enfocar la barra de prueba. Por tanto, la interacción táctil sigue pendiente de validación en un entorno Android estable.

`tests/build_fixture.py` construye una barra sintética para verificar el comportamiento de la APK de producción en un emulador vacío. La fixture utiliza deliberadamente el paquete `com.whatsapp`: **no debe instalarse en un móvil con WhatsApp** y no implementa sus funciones. Su APK de prueba no se incluye en la distribución.

Para comprobar el comportamiento en el dispositivo: verifica que el botón quede oculto y sus toques no hagan nada; abre un chat y comprueba que puedes escribir; prueba las otras pestañas; gira el móvil; sal de WhatsApp; y pausa la protección desde esta app.

## Código

- `MainActivity.java`: activación, pausa, apariencia y ayuda.
- `CoverService.java`: inspección limitada de la ventana, ciclo de vida y cubierta que consume los toques.
- `ServiceStatus.java`: estado del servicio y último diagnóstico técnico local.
- `TabDetector.java`: reglas de reconocimiento y geometría independientes de Android.
- `scripts/build.py`: compilación y firma reproducibles con herramientas del SDK.

Referencias de plataforma: [servicios de accesibilidad](https://developer.android.com/guide/topics/ui/accessibility/service), [contenedores de accesibilidad](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS), [ventanas de accesibilidad](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).
