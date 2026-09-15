# Sin Novedades

App personal de Android que **tapa el botón Novedades de WhatsApp e impide pulsarlo** mediante una cubierta opaca. No requiere root ni modificar la APK de WhatsApp.

## Descargar e instalar

La APK firmada está en [`dist/sin-novedades-0.3.2.apk`](dist/sin-novedades-0.3.2.apk). Abre ese archivo y usa **Download raw file**.

**Instala esta APK encima de la versión anterior, incluida la 0.3.1.** Mantiene el identificador y la firma. No es necesario desinstalar la versión anterior. La protección táctil requiere Android 13 o posterior y está activada por defecto.

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

### Si no bloquea los deslizamientos o los toques rápidos

La 0.3.0 podía mostrar **Protección táctil no disponible** tanto por capacidades ausentes como por otro servicio que solicitara exploración táctil. Además, reemplazaba ese aviso por **en espera** al salir de WhatsApp. El diagnóstico recibido del Samsung confirma que el controlador no se activó, pero no permite distinguir esas dos causas.

La **0.3.1** muestra el motivo concreto incluso estando en Sin Novedades:

- **Android no ha habilitado…**: desactiva y vuelve a activar el servicio **Sin Novedades** en los ajustes de Accesibilidad de Android. Apagar el interruptor dentro de esta app solo pausa la protección y no vuelve a conectar el servicio.
- **Otro servicio solicita exploración táctil: [nombre]**: identifica el servicio que provoca el conflicto. Tener AppBlock u otra app con permiso de accesibilidad no basta por sí solo para bloquear esta protección. Sin Novedades no modifica los permisos de otras apps.
- **Control táctil solicitado; esperando el primer gesto**: la configuración se ha solicitado, pero aún no se ha recibido entrada. Abre WhatsApp con la barra visible y prueba un deslizamiento horizontal; vuelve aquí y comprueba el resultado guardado.

Si sigue fallando, copia el nuevo diagnóstico. Incluye las capacidades que Android entrega al servicio, las solicitudes táctiles relevantes de otros servicios y su nombre, los eventos táctiles recibidos y el último estado táctil en WhatsApp. Conserva también la última barra reconocida aunque después entres en un chat. Estos datos se guardan solo en el móvil.

Si los deslizamientos ya se bloquean pero las pulsaciones simples no funcionan, instala la **0.3.2**. Utiliza la acción del control que has tocado y conserva un gesto de respaldo para controles que no aceptan esa acción. El diagnóstico cuenta las pulsaciones directas aceptadas, los respaldos solicitados y completados y las cancelaciones con su motivo.

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

### Cambios de la 0.3.1

- Conserva el motivo de la falta de activación y distingue capacidades ausentes, conflictos con otro servicio y configuración pendiente de recibir entrada.
- Comprueba las dos capacidades necesarias: interceptar gestos y transmitir las pulsaciones permitidas. Evita activar la interceptación si Android aún no permite transmitir esas pulsaciones.
- Corrige un falso conflicto: en servicios con target SDK 18+, una solicitud de exploración sin la capacidad correspondiente es ignorada por Android y ya no impide activar Sin Novedades. Para servicios antiguos se conserva la comprobación prudente porque pueden tener una autorización anterior.
- Identifica el servicio propio usando también su componente declarado y muestra el nombre de los otros servicios que solicitan exploración táctil.
- Guarda por separado el estado táctil y la última barra reconocida; no se pierden al entrar en un chat o copiar el diagnóstico desde esta app.
- Explica cómo volver a conectar el servicio tras una actualización. No puede concederse por sí misma capacidades que Android no haya habilitado.

### Cambios de la 0.3.2

- Ejecuta la pulsación simple o larga mediante la acción del control que está bajo el dedo. Comprueba que sigue visible, habilitado, en la misma ventana y con los mismos límites antes de actuar. No utiliza el foco de accesibilidad para elegir el destino.
- Excluye Novedades y cualquier contenedor pulsable que abarque esa pestaña. Selecciona el descendiente pulsable más concreto; si hay controles superpuestos en ramas distintas, deja la selección al método de respaldo por coordenadas.
- El gesto de respaldo espera a que Android cierre la interacción física y comprueba de nuevo la pantalla antes de enviarse. La implementación de AOSP solo consulta la región de paso al comenzar una interacción desde el estado libre: reenviar inmediatamente al recibir `ACTION_UP` podía dejar el nuevo toque dentro de la interacción anterior.
- Utiliza `ViewConfiguration.getTapTimeout()` para la duración del toque de respaldo; la versión anterior enviaba una pulsación de 1 ms. Conserva la región de paso de un solo píxel en el punto permitido, sin abrir el resto de la pantalla.
- Cancela un respaldo pendiente si empieza otra interacción, se abandona WhatsApp o cambia la pantalla. Una respuesta tardía de un gesto anterior no puede finalizar uno nuevo.
- Consume cada liberación del dedo una sola vez y descarta referencias de interacciones terminadas o delegadas, para que un evento tardío no vuelva a ejecutar una pulsación anterior.
- Añade diagnóstico específico de pulsaciones, separado de los contadores de bloqueos.

## Qué hace

- Localiza una barra inferior con Chats, Novedades y otra pestaña reconocida.
- Usa los límites reales del botón pulsable. No calcula su posición con coordenadas fijas ni amplía la cubierta sobre botones sin etiqueta.
- Coloca una ventana de accesibilidad opaca que absorbe los toques dentro de ese rectángulo. Con la protección táctil reforzada, también filtra los gestos de la pantalla de navegación.
- Retira la cubierta al dejar de detectar la barra, al entrar en un chat con editor, al aparecer el teclado, al cambiar de aplicación o al bloquear el móvil.
- Permite pausar el bloqueo y cambiar el color de la cubierta.

## Alcance de esta versión

El usuario confirmó que la cubierta de la **0.2.0** funciona en su WhatsApp y notificó dos vías de entrada: pulsar al volver de un chat antes de aparecer el rectángulo y deslizar entre pestañas. Tras la **0.3.1**, ha confirmado que se bloquean los deslizamientos, pero fallan las pulsaciones simples permitidas. La **0.3.2** cambia cómo se ejecutan esas pulsaciones y corrige el momento y la duración del respaldo. **Su funcionamiento en el Samsung del usuario sigue pendiente de comprobar.**

- Android 8.0 o posterior para la cubierta; Android 13 o posterior para el controlador táctil. Compilada con SDK 36 y orientada a Android 16.
- Reconoce etiquetas en español e inglés. Se admite WhatsApp y se intenta reconocer la barra de WhatsApp Business.
- Cubre el botón: no borra la pestaña, no reorganiza la barra y no vuelve a Chats automáticamente.
- No impide entrar mediante enlaces directos, otros servicios de accesibilidad u otros accesos que WhatsApp pueda incorporar.
- La cubierta visual sigue siendo reactiva. En Android 13+, la protección táctil comprueba la ventana al comenzar y terminar un toque para evitar depender de esa demora de dibujo. El inicio del servicio, la primera entrada desde otra app y una interfaz que aún no exponga su navegación a accesibilidad siguen dependiendo de los avisos de Android; no es un bloqueo inviolable.
- En la pantalla con la barra, la protección reforzada bloquea los deslizamientos horizontales y el multitáctil. Las pulsaciones simples y largas se ejecutan al levantar el dedo. Los controles que necesitan el gesto de respaldo tienen una espera adicional hasta que Android cierra la interacción física. El desplazamiento vertical empieza al superar el umbral táctil. Si estos cambios de interacción no te convienen, puedes apagar solo **Protección táctil reforzada** y conservar la cubierta.
- No activa su controlador si otro servicio solicita exploración táctil y tiene la capacidad correspondiente, para no disputar el control con un lector de pantalla. También se comprueban las solicitudes de servicios antiguos que pueden tener autorización previa. El diagnóstico identifica el servicio y la causa concreta.
- Si la estructura no se reconoce con suficiente confianza, no pone ninguna cubierta. Una actualización de WhatsApp puede exigir adaptar el detector.
- No actúa sobre barras laterales de tabletas ni pantallas externas.

## Privacidad y permisos

Android concede al servicio de Accesibilidad la capacidad de acceder al contenido visible. La app solo busca etiquetas y geometría de navegación en WhatsApp, y utiliza eventos de cambio de ventana para retirar la cubierta al salir. Guarda localmente un diagnóstico técnico de WhatsApp y del control táctil, incluidos los nombres y componentes de otros servicios que solicitan exploración táctil; solo se copia al portapapeles cuando pulsas el botón correspondiente. No guarda ni registra mensajes, no realiza capturas, no tiene permiso de Internet y no incluye analítica ni bibliotecas de terceros.

En Android 13+, utiliza `TouchInteractionController` para consumir o delegar un gesto físico. Desde la 0.3.2, usa `performAction(ACTION_CLICK)` o `ACTION_LONG_CLICK` exclusivamente para ejecutar la pulsación permitida que el usuario acaba de hacer sobre ese mismo control. No usa el foco de accesibilidad como destino. Usa `dispatchGesture` como respaldo, en el punto tocado y tras comprobar que la ventana sigue siendo la misma. No utiliza `performGlobalAction`, no elige otra pestaña ni vuelve a Chats automáticamente. El servicio está protegido por `BIND_ACCESSIBILITY_SERVICE` y solo se activa desde Ajustes.

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

Las 37 comprobaciones de `tests/TouchPolicyTest.java` cubren el toque bloqueado aunque aún no haya cubierta, ambos sentidos del deslizamiento, movimientos lentos y diagonales, desplazamiento vertical, pulsaciones permitidas, multitáctil, cancelación, una pestaña que aparece entre apoyar y levantar el dedo y la liberación duplicada de un mismo toque. La APK compilada se verifica con `apksigner` y `zipalign`, incluida su compatibilidad de firma con la 0.2.0.

La **0.3.1** añadió 20 comprobaciones de `tests/TouchAvailabilityTest.java`: conexiones con las capacidades antiguas, falta de transmisión de pulsaciones, permisos completos, solicitudes de otros servicios sin capacidad, conflictos reales con nombre, servicios antiguos, exclusión del propio servicio y conservación del aviso al salir de WhatsApp. En esa versión pasaron las **80 comprobaciones**, junto con la compilación con SDK 36, la firma original y la alineación de la APK. No se repitió la prueba nativa para la 0.3.1.

La **0.3.2** añade 20 comprobaciones de `tests/TapTargetTest.java` y dos de liberaciones duplicadas. Pasan **102 comprobaciones**. Se verifica la selección del control bajo el dedo, sus límites, los descendientes pulsables, los solapamientos ambiguos, las coordenadas fraccionarias y la exclusión de Novedades y de su contenedor común.

En Android 16 se han probado la acción directa y el respaldo mediante entradas físicas del kernel. La fixture cuenta por separado los intentos de `ACTION_CLICK` y las pulsaciones que recibe. Su botón Llamadas rechaza deliberadamente `ACTION_CLICK`: esto obliga a comprobar el respaldo de la APK de producción. Para las pulsaciones rápidas se envían los informes de apoyo y liberación juntos, evitando que el coste de lanzar comandos en el emulador convierta el ensayo en una pulsación larga.

| Comprobación de la 0.3.2 | Resultado observado |
| --- | --- |
| Pulsación simple en Chats y Comunidades | Cada control recibe su acción directa y una sola pulsación |
| Pulsación simple en el contenido | Se ejecuta una sola vez |
| Control que rechaza la acción directa | El gesto de respaldo completa una sola pulsación en Llamadas |
| Abrir un chat y volver mediante su botón | Se entra en el chat y se vuelve a navegación |
| Pulsar Novedades y después Comunidades al volver | Novedades no recibe ninguna pulsación; Comunidades recibe una |
| Deslizar a izquierda y derecha | No aumenta el contador de deslizamientos horizontales de la fixture |
| Desplazarse verticalmente | La fixture recibe el desplazamiento |

Estas pruebas no confirman todavía el resultado en el Samsung ni permiten medir su latencia.

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
- `TouchAvailability.java`: requisitos de activación y causas concretas de indisponibilidad, independientes de la app que esté delante.
- `UserTap.java`: selección del control tocado y ejecución de su acción tras comprobar la ventana y sus límites.
- `TapTarget.java`: reglas de geometría y jerarquía que excluyen Novedades y controles ambiguos.
- `scripts/build.py`: compilación y firma reproducibles con herramientas del SDK.

Referencias de plataforma: [servicios de accesibilidad](https://developer.android.com/guide/topics/ui/accessibility/service), [contenedores de accesibilidad](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS), [ventanas de accesibilidad](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [firma de aplicaciones](https://developer.android.com/studio/publish/app-signing).

Control táctil: [TouchInteractionController](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController), [dispatchGesture](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,android.accessibilityservice.AccessibilityService.GestureResultCallback,android.os.Handler)) y [zonas de paso directo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#setTouchExplorationPassthroughRegion(int,android.graphics.Region)).

Requisitos de activación: [capacidades del servicio](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#getCapabilities()) y [reglas de solicitud de exploración táctil](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_TOUCH_EXPLORATION_MODE).

Pulsaciones: [acciones de un nodo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#performAction(int)) y [ejemplo oficial de respaldo y duración del toque](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,android.accessibilityservice.AccessibilityService.GestureResultCallback,android.os.Handler)).
