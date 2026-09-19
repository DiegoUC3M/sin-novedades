# Sin Novedades

App personal para Android que tapa el botón **Novedades** de WhatsApp y oculta su contenido cuando detecta esa pestaña seleccionada. También tapa la lista de **Actualizaciones ocultas**. No requiere root ni modificar WhatsApp.

## Descargar e instalar

Descarga la [APK firmada 0.4.1](https://github.com/DiegoUC3M/sin-novedades/raw/refs/heads/main/dist/sin-novedades-0.4.1.apk).

**Instálala encima de la versión anterior.** Conserva el identificador y la firma; no hace falta desinstalar. Desde la 0.4.0 se ha eliminado el filtro táctil de la serie 0.3.x y su interruptor. Al conectarse, también restablece las opciones dinámicas del servicio para retirar la solicitud antigua de exploración táctil.

Para una instalación nueva:

1. Instala la APK y abre **Sin Novedades**.
2. Pulsa **Activar accesibilidad** y activa el servicio en Ajustes → Accesibilidad → Aplicaciones o servicios instalados.
3. Si Android muestra **Ajustes restringidos**, permite los ajustes restringidos desde Ajustes → Aplicaciones → Sin Novedades → ⋮ y vuelve a Accesibilidad. La ubicación varía según el móvil.
4. Abre WhatsApp. El botón Novedades queda cubierto al reconocerse la barra inferior.
5. Si llegas a Novedades deslizando o pulsando antes de que aparezca la cubierta, su contenido se tapa en negro al detectarse la selección. **Pulsa otra pestaña de la barra inferior para salir.** En Actualizaciones ocultas se cubre la lista y se conserva la cabecera con su flecha de volver.

El interruptor **Ocultar Novedades** pausa ambas cubiertas. **Elegir color** cambia solo la cubierta pequeña del botón para igualarla al fondo de WhatsApp; la pantalla de contenido permanece negra.

## Cambios de la 0.4.1

Esta actualización responde a la demora al entrar en Novedades y añade la pantalla independiente **Actualizaciones ocultas**.

- Los avisos de selección, cambio de ventana, pulsación, desplazamiento de un ViewPager y cambio de descripción de estado se atienden sin la espera general de 80 ms. Los cambios ordinarios de contenido y los desplazamientos de listas siguen agrupándose para evitar inspecciones continuas.
- Tras reconocer la barra, se conservan referencias solo a sus etiquetas. En las comprobaciones siguientes se refrescan las etiquetas y sus contenedores, se vuelve a validar la fila completa y se comprueba la ventana y sus límites. Los contenedores compartidos se refrescan una vez por comprobación.
- Si una referencia deja de ser válida, cambia la etiqueta o desaparece la barra, se descarta la caché y se busca de nuevo. La búsqueda se concentra en los bordes superior e inferior: omite los descendientes de filas visibles situadas completamente en la zona central, conservando el recorrido de contenedores invisibles.
- El reintento cuando WhatsApp está delante pasa de 1000 a **250 ms**. Eso acorta la espera si falta un aviso de Android; no es una garantía de tiempo de dibujo ni implica que todo el proceso sea cuatro veces más rápido.
- **Actualizaciones ocultas** se reconoce por el título exacto, una cabecera con geometría válida y un botón para volver. Se tapa la lista, dejando libre la cabecera y la zona de navegación del sistema. Un editor de chat visible impide activar esta cubierta.
- El diagnóstico diferencia la actualización de pestañas de la búsqueda por los bordes y muestra la duración de la última inspección de WhatsApp.

La entrada táctil sigue siendo directa: no se recupera el controlador de la serie 0.3.x ni se cambia de pestaña automáticamente.

## Cambio de la 0.4.0 (versión anterior)

Las versiones anteriores intentaban impedir la entrada a Novedades interceptando los gestos. En el móvil del usuario, ese método alteraba las pulsaciones e introducía retardo. Esta versión elimina ese controlador, las pulsaciones por accesibilidad y los gestos sintéticos.

- WhatsApp recibe directamente los toques, pulsaciones largas y deslizamientos fuera de los dos rectángulos cubiertos. No hay una inspección de pantalla que deba terminar para entregar cada toque.
- Se conserva la cubierta del botón Novedades usando sus límites reales.
- Una segunda ventana negra cubre todo el contenido por encima de la barra cuando Novedades aparece seleccionada. Absorbe los toques en el contenido oculto y deja libres las otras pestañas y la navegación del sistema.
- La selección se obtiene de los estados `selected`, `checked`, del elemento de colección o de metadatos explícitos de selección en español/inglés. El foco de accesibilidad no se interpreta como selección.
- Se comprueba que las etiquetas pertenecen a una barra válida con Chats, Novedades y otra pestaña. Las selecciones contradictorias o desconocidas no activan la pantalla negra.
- La inspección responde a eventos de selección, clic, contenido y ventana. Los eventos continuos se agrupan con un intervalo mínimo de 80 ms; el reintento en WhatsApp pasa de 180 a 1000 ms. Android 13+ vuelve a utilizar la caché, con una renovación periódica.
- La pantalla negra se retira al seleccionar otra pestaña. Ambas cubiertas se retiran al entrar en un chat con editor, aparecer el teclado, salir de WhatsApp o bloquear el móvil. No se cambia de pestaña automáticamente.
- El diagnóstico conserva las etiquetas canónicas, límites y estados de selección. Los informes del controlador antiguo se descartan al actualizar.

## Alcance

- Android 8.0 o posterior; compilada con SDK 36 y orientada a Android 16.
- Reconoce etiquetas en español e inglés. Admite WhatsApp e intenta reconocer la barra de WhatsApp Business.
- **La pantalla negra es reactiva:** el contenido puede verse brevemente antes de que Android notifique el cambio. No garantiza ocultarlo desde el primer fotograma ni impedir la pulsación inicial antes de colocar la cubierta pequeña.
- Si WhatsApp no expone qué pestaña está seleccionada, solo se cubre el botón. La última comprobación indica expresamente ese caso.
- Cubre la pestaña Novedades y la lista Actualizaciones ocultas. Los visores individuales de estados y otras pantallas de canales quedan fuera de este detector; no cambia la interfaz interna de WhatsApp.
- No está diseñada como un bloqueo inviolable. No actúa sobre barras laterales de tabletas ni pantallas externas.
- La nueva versión queda pendiente de comprobar en el Samsung SM-S938B del usuario con WhatsApp real.

Si la pantalla negra no aparece, entra en Novedades, vuelve a **Sin Novedades** y pulsa **Copiar diagnóstico**. El informe guarda por separado la última barra reconocida aunque después abras un chat. No incluye mensajes ni capturas.

## Privacidad y permisos

Accesibilidad permite consultar la interfaz visible para localizar las pestañas y colocar las cubiertas. Solo se buscan etiquetas y estados de navegación de WhatsApp; los cambios de ventana sirven para retirar las cubiertas al salir. El diagnóstico técnico se guarda en el móvil y solo se copia al portapapeles al pulsar el botón correspondiente.

La app no guarda mensajes, no realiza capturas, no tiene permiso de Internet, no incluye analítica ni bibliotecas de terceros. No solicita exploración táctil, no registra `TouchInteractionController`, no llama a `dispatchGesture`, `performAction` ni `performGlobalAction`. El servicio está protegido por `BIND_ACCESSIBILITY_SERVICE` y se activa desde Ajustes.

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

## Comprobaciones de la 0.4.1

**76 pruebas**: 44 del detector de pestañas y 32 de geometría, títulos y programación de inspecciones. Incluyen títulos parciales o parecidos a mensajes, cabeceras sin botón para volver, editor visible, coordenadas fuera de pantalla, alta densidad, orientación RTL, conservación de la navegación y prioridad de los avisos de selección frente a los cambios ordinarios de contenido.

La compilación directa verifica la firma original y la alineación de la APK. Las pruebas unitarias no miden el tiempo de dibujo ni reproducen el árbol accesible real de WhatsApp. La comprobación de integración de esta versión quedó pendiente porque el emulador Android 16 no terminó de arrancar; todavía falta comprobar las cubiertas y su rapidez en el móvil real.

**La app de `tests/android-fixture` usa deliberadamente el paquete `com.whatsapp` para probar el filtro de paquetes en un emulador vacío. Nunca se debe instalar en un teléfono con WhatsApp real.** No es un cliente de WhatsApp y no forma parte de la APK entregada. Incluye una barra seleccionable, navegación a chats y una pantalla sintética de Actualizaciones ocultas con flecha para volver, además del caso de un chat con ese mismo título.

## Documentación Android

- [Estados del nodo accesible](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo).
- [Selección de elementos de colección](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.CollectionItemInfo#isSelected()).
- [Opciones del servicio y exploración táctil](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_TOUCH_EXPLORATION_MODE).
