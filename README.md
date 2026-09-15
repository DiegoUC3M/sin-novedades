# Sin Novedades

App personal de Android que **tapa el botón Novedades de WhatsApp e impide pulsarlo** mediante una cubierta opaca. No requiere root ni modificar la APK de WhatsApp.

## Descargar e instalar

La APK firmada está en [`dist/sin-novedades-0.3.0.apk`](dist/sin-novedades-0.3.0.apk). Abre ese archivo y usa **Download raw file**.

**Instala esta APK encima de la 0.1.0 o 0.2.0.** Mantiene el identificador y la firma. No es necesario desinstalar la versión anterior. La protección táctil nueva requiere Android 13 o posterior y está activada por defecto.

1. Descarga e instala la APK. Android puede pedirte autorizar instalaciones desde la app con la que la abras.
2. Abre **Sin Novedades** y pulsa **Activar accesibilidad**.
3. En **Ajustes → Accesibilidad → Aplicaciones o servicios instalados**, activa **Sin Novedades**.
4. Si Android muestra **Ajustes restringidos**, ve a **Ajustes → Aplicaciones → Sin Novedades → ⋮ → Permitir ajustes restringidos**, y vuelve al paso 3. La ubicación puede variar en Samsung.
5. Abre WhatsApp en Chats. El botón Novedades debería quedar cubierto y no responder a los toques.
6. Si se distingue el rectángulo, entra en **Elegir color** y ajusta el fondo a tu tema de WhatsApp. También admite un color hexadecimal personalizado.

En Android 13+, deja activada **Protección táctil reforzada**. Para cambiar de sección, pulsa Chats, Comunidades o Llamadas: los deslizamientos horizontales se bloquean en la pantalla con la barra. Dentro de un chat, los gestos conservan su manejo habitual. Si Android no conecta las nuevas capacidades tras la actualización, desactiva y vuelve a activar el servicio de Sin Novedades en Accesibilidad.

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

### Cambios de la 0.3.0

- Elimina la espera de 45 ms tras los eventos de contenido y prioriza la barra durante la inspección. El reintento mientras WhatsApp está delante pasa de 500 a 180 ms.
- En Android 13+, mantiene un controlador táctil activo también dentro de los chats. Obtiene una instantánea nueva al apoyar el dedo, con la caché de nodos desactivada: el bloqueo ya no depende de que el rectángulo haya terminado de dibujarse.
- Consume los gestos horizontales en la pantalla de navegación antes de enviarlos a WhatsApp. La regla utiliza el desplazamiento acumulado desde el inicio, no la velocidad de cada evento.
- Delega los desplazamientos verticales a Android y transmite los toques permitidos en el punto que el usuario acaba de pulsar. Comprueba otra vez la pestaña y la ventana al levantar el dedo antes de transmitir un toque.
- Conserva zonas de paso directo para los gestos del sistema y deja de pedir control táctil al salir de WhatsApp o pausar la protección.
- Añade un interruptor independiente y contadores de toques y deslizamientos bloqueados en el diagnóstico.

## Qué hace

- Localiza una barra inferior con Chats, Novedades y otra pestaña reconocida.
- Usa los límites reales del botón pulsable. No calcula su posición con coordenadas fijas ni amplía la cubierta sobre botones sin etiqueta.
- Coloca una ventana de accesibilidad opaca que absorbe los toques dentro de ese rectángulo. Con la protección táctil reforzada, también filtra los gestos de la pantalla de navegación.
- Retira la cubierta al dejar de detectar la barra, al entrar en un chat con editor, al aparecer el teclado, al cambiar de aplicación o al bloquear el móvil.
- Permite pausar el bloqueo y cambiar el color de la cubierta.

## Alcance de esta versión

El usuario confirmó que la cubierta de la **0.2.0** funciona en su WhatsApp y notificó dos vías de entrada: pulsar al volver de un chat antes de aparecer el rectángulo y deslizar entre pestañas. La **0.3.0** incorpora protección táctil para esos casos. Su comportamiento en el móvil del usuario todavía debe comprobarse.

- Android 8.0 o posterior para la cubierta; Android 13 o posterior para el controlador táctil. Compilada con SDK 36 y orientada a Android 16.
- Reconoce etiquetas en español e inglés. Se admite WhatsApp y se intenta reconocer la barra de WhatsApp Business.
- Cubre el botón: no borra la pestaña, no reorganiza la barra y no vuelve a Chats automáticamente.
- No impide entrar mediante enlaces directos, otros servicios de accesibilidad u otros accesos que WhatsApp pueda incorporar.
- La cubierta visual sigue siendo reactiva. En Android 13+, la protección táctil comprueba la ventana al comenzar y terminar un toque para evitar depender de esa demora de dibujo. El inicio del servicio, la primera entrada desde otra app y una interfaz que aún no exponga su navegación a accesibilidad siguen dependiendo de los avisos de Android; no es un bloqueo inviolable.
- En la pantalla con la barra, la protección reforzada bloquea los deslizamientos horizontales y el multitáctil. Los toques se transmiten al levantar el dedo; las pulsaciones largas se reproducen después de soltarlo. El desplazamiento vertical empieza al superar el umbral táctil. Si estos cambios de interacción no te convienen, puedes apagar solo **Protección táctil reforzada** y conservar la cubierta.
- No activa su controlador si otro servicio pide exploración táctil, para no disputar el control con un lector de pantalla. El diagnóstico indica si la protección táctil está disponible.
- Si la estructura no se reconoce con suficiente confianza, no pone ninguna cubierta. Una actualización de WhatsApp puede exigir adaptar el detector.
- No actúa sobre barras laterales de tabletas ni pantallas externas.

## Privacidad y permisos

Android concede al servicio de Accesibilidad la capacidad de acceder al contenido visible. La app solo busca etiquetas y geometría de navegación en WhatsApp, y utiliza eventos de cambio de ventana para retirar la cubierta al salir. Guarda localmente un diagnóstico técnico de la última comprobación de WhatsApp; solo se copia al portapapeles cuando pulsas el botón correspondiente. No guarda ni registra mensajes, no realiza capturas, no tiene permiso de Internet y no incluye analítica ni bibliotecas de terceros.

No utiliza `performAction` ni `performGlobalAction` para navegar. En Android 13+, utiliza `TouchInteractionController` para consumir o delegar un gesto físico. Usa `dispatchGesture` únicamente para transmitir el toque permitido que el usuario acaba de hacer, en el mismo punto y tras comprobar que la ventana sigue siendo la misma. No elige otra pestaña ni vuelve a Chats automáticamente. El servicio está protegido por `BIND_ACCESSIBILITY_SERVICE` y solo se activa desde Ajustes.

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

Las 35 comprobaciones de `tests/TouchPolicyTest.java` cubren el toque bloqueado aunque aún no haya cubierta, ambos sentidos del deslizamiento, movimientos lentos y diagonales, desplazamiento vertical, pulsaciones permitidas, multitáctil, cancelación y una pestaña que aparece entre apoyar y levantar el dedo. La APK compilada se verifica con `apksigner` y `zipalign`, incluida su compatibilidad de firma con la 0.2.0.

En un emulador AOSP con Android 16 se han comprobado la instalación y actualización de la APK firmada y la conexión del servicio. Para la 0.3.0 se ha usado una pantalla sintética con entradas físicas a través del dispositivo de entrada del kernel: la prueba atraviesa el filtro de accesibilidad, en vez de utilizar acciones que pudieran saltárselo. Se comprobó primero que esa pantalla recibía los mismos toques y gestos con el servicio desactivado.

| Comprobación de la 0.3.0 | Resultado observado |
| --- | --- |
| Pulsar Novedades con protección | No aumenta su contador de pulsaciones |
| Deslizar a izquierda y derecha | No aumenta el contador de cambios horizontales |
| Desplazarse verticalmente | La pantalla recibe el desplazamiento |
| Pulsar Comunidades | Recibe una sola pulsación |
| Abrir un chat | Se retira la cubierta y el control táctil permanece activo |
| Volver del chat e intentar pulsar Novedades | Se vuelve a navegación sin una nueva pulsación de Novedades |
| Abrir otra aplicación | Android deja de tener activo el filtro de exploración táctil solicitado por esta app |

La prueba de transmisión de pulsaciones detectó y permitió corregir un redondeo de coordenadas de Android: ahora el toque y su región de paso usan el mismo píxel. El emulador no tiene aceleración y necesitó ampliar los tiempos de espera del sistema para evitar reinicios; **no permite medir con fiabilidad la latencia en un móvil real**. La prueba de regreso utiliza la fixture con avisos de accesibilidad retrasados. Estas comprobaciones no sustituyen a verificar la versión de WhatsApp y el dispositivo del usuario.

`tests/build_fixture.py` construye una barra sintética para verificar el comportamiento de la APK de producción en un emulador vacío. La fixture utiliza deliberadamente el paquete `com.whatsapp`: **no debe instalarse en un móvil con WhatsApp** y no implementa sus funciones. Su APK de prueba no se incluye en la distribución.

Para comprobar el comportamiento en el dispositivo: verifica que el botón quede oculto y sus toques no hagan nada; abre un chat y comprueba que puedes escribir; prueba las otras pestañas; gira el móvil; sal de WhatsApp; y pausa la protección desde esta app.

## Código

- `MainActivity.java`: activación, pausa, apariencia y ayuda.
- `CoverService.java`: inspección limitada de la ventana, ciclo de vida y cubierta que consume los toques.
- `ServiceStatus.java`: estado del servicio y último diagnóstico técnico local.
- `TabDetector.java`: reglas de reconocimiento y geometría independientes de Android.
- `TouchGuard.java`: integración con el controlador táctil de Android 13+, paso directo al sistema y transmisión de los toques permitidos.
- `TouchPolicy.java`: decisiones por interacción física independientes de Android.
- `scripts/build.py`: compilación y firma reproducibles con herramientas del SDK.

Referencias de plataforma: [servicios de accesibilidad](https://developer.android.com/guide/topics/ui/accessibility/service), [contenedores de accesibilidad](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS), [ventanas de accesibilidad](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).

Control táctil: [TouchInteractionController](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController), [dispatchGesture](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,android.accessibilityservice.AccessibilityService.GestureResultCallback,android.os.Handler)) y [zonas de paso directo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#setTouchExplorationPassthroughRegion(int,android.graphics.Region)).
