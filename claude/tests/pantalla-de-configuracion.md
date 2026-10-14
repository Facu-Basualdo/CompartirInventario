# Tests de la pantalla de configuración

[← Índice Tests](INDICE.md)

`ConfigScreenClientGameTest` (entrypoint `fabric-client-gametest`, `gradlew runClientGameTest`): un cliente de verdad con un mundo singleplayer. **Abre la pantalla con `context.setScreen`**, porque Mod Menu no está en el entorno de prueba: la integración con Mod Menu (el entrypoint) no se prueba acá. Deja capturas en `build/run/clientGameTest/screenshots`.

## Qué cubre

- Con la config por defecto la barra lateral del mundo arranca vacía (`barraLateral: ninguna`).
- Elegir *Culpas* en *Barra lateral* y 1,5 en *Corazones de un susto* (con coma) y guardar: la config del servidor integrado cambia y la barra lateral muestra un objetivo enseguida, sin `/soullink recargar`.
- 50 corazones: `SoulLinkConfig` avisa y usaría 2, así que no se guarda. La pantalla queda abierta y sigue 1,5.

## Gotchas

- **El cliente de prueba arranca en inglés** (`lang:en_us`): el test pone `getLanguageManager().setSelected("es_ar")` antes de crear el mundo para buscar los campos por su nombre en español. Eso no recarga los recursos de vanilla: en las capturas **Cancel** y **ON** siguen en inglés, y los nombres de los mobs también.
- **Los campos no están en `screen.children()` directamente**: están adentro del contenedor de `ScrollableLayout`. El test recorre los `ContainerEventHandler` y filtra los `EditBox` por `getMessage()` (el nombre de la fila). `clickScreenButton("Guardar")` sí lo encuentra, porque está en el pie.
- **`CycleButton.onPress` necesita un `InputWithModifiers` de verdad** (`new KeyEvent(InputConstants.KEY_RETURN, 0, 0)`): con null revienta en `hasShiftDown()`. Los `CycleButton` se buscan por su valor (`getValue()`), no por posición: los sí/no también son `CycleButton`.
- **`runClientGameTest` borra `build/run/clientGameTest/config` antes de arrancar**: el test deja valores cambiados, y la corrida siguiente tiene que empezar con los de por defecto.

## Ver también

- [Mod: pantalla de configuración](../mod/pantalla-de-configuracion.md)
