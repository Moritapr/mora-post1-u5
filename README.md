# ReservasLabs — Sistema de Reservas de Laboratorios

**Unidad 5 · Arquitectura en Capas y MVC + REST**
Java 17 · Spring Boot 3.2.12 · Spring Data JPA · H2 en memoria · Thymeleaf · Maven

---

## 1. Descripción

`reservas-labs-api` resuelve la reserva de laboratorios de la universidad. Un solicitante elige un
laboratorio y una franja horaria; el sistema acepta la reserva únicamente si el laboratorio existe,
la franja cae dentro de la jornada de atención, su duración es razonable y no choca con ninguna
reserva activa previa.

Lo relevante del ejercicio no es el dominio, sino **cómo está organizado**: el mismo caso de uso se
publica por dos canales de entrada distintos —una **API REST** que devuelve JSON y un **conjunto de
vistas MVC** renderizadas con Thymeleaf— sobre **una única capa de servicio**. Ninguna regla de
negocio está duplicada: ambos canales delegan en el mismo bean `ReservaService` gestionado por Spring.

```
                  ┌────────────────────────┐        ┌──────────────────────────┐
  JSON  ──────▶   │  controller  (REST)    │        │  web  (MVC + Thymeleaf)  │   ◀────── HTML
                  │  /api/reservas         │        │  /reservas               │
                  └───────────┬────────────┘        └────────────┬─────────────┘
                              │                                  │
                              └──────────────┬───────────────────┘
                                             ▼
                                   ┌───────────────────┐
                                   │      service      │   reglas de negocio (única fuente)
                                   │  ReservaService   │   jornada · duración · solapamiento
                                   └─────────┬─────────┘
                                             ▼
                                   ┌───────────────────┐
                                   │    repository     │   Spring Data JPA + JPQL
                                   └─────────┬─────────┘
                                             ▼
                                   ┌───────────────────┐
                                   │   model (@Entity) │   Laboratorio · Reserva
                                   └─────────┬─────────┘
                                             ▼
                                        H2 en memoria
```

### Mapa de paquetes

| Paquete | Responsabilidad | Depende de |
|---|---|---|
| `com.universidad.reservaslabs.model` | Entidades JPA y enumeraciones del dominio | — |
| `…​.repository` | Puertos de persistencia (Spring Data JPA + JPQL) | `model` |
| `…​.service` | Reglas de negocio y transaccionalidad | `model`, `repository`, `exception` |
| `…​.exception` | Excepciones de dominio y traducción a JSON | `model` |
| `…​.controller` (+ `.dto`) | Canal de entrega REST | `service`, `model` |
| `…​.web` (+ `.dto`) | Canal de entrega MVC/Thymeleaf | `service`, `model` |
| `…​.config` | Siembra de datos de arranque | `model`, `repository` |

La dependencia siempre apunta **hacia adentro**: `model` no conoce a nadie y ninguna clase de
`service` importa nada de `controller` ni de `web`.

---

## 2. Requisitos previos

| Herramienta | Versión |
|---|---|
| JDK | **17** (el proyecto compila con `maven.compiler.release=17`) |
| Apache Maven | 3.9 o superior |

> ⚠️ **Importante — versión del JDK.** Spring Boot 3.2.12 arrastra Lombok 1.18.30, que **no funciona
> sobre JDK 24 o superior**: el procesador de anotaciones se desactiva en silencio y la compilación
> falla con `variable <campo> not initialized in the default constructor`. Si `mvn -version` reporta
> un JDK más nuevo, fija explícitamente el 17 antes de compilar:
>
> ```bash
> # macOS con Homebrew
> export JAVA_HOME=/opt/homebrew/opt/openjdk@17
>
> # Linux / macOS genérico
> export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
> export JAVA_HOME=/usr/lib/jvm/java-17-openjdk       # Linux
> ```

---

## 3. Compilación y ejecución

```bash
# Compilar, ejecutar las pruebas y empaquetar el JAR ejecutable
mvn clean package

# Solo la suite de pruebas
mvn clean test

# Levantar la aplicación (http://localhost:8080)
mvn spring-boot:run

# Alternativa: ejecutar el JAR ya empaquetado
java -jar target/reservas-labs-api-1.0.0.jar
```

Al arrancar, `DatosIniciales` siembra cuatro laboratorios si la tabla está vacía:

| id | nombre | ubicación | capacidad | tipo |
|---|---|---|---|---|
| 1 | Lab Computo 1 | Bloque A - Piso 2 | 30 | `COMPUTO` |
| 2 | Lab Computo 2 | Bloque A - Piso 3 | 25 | `COMPUTO` |
| 3 | Lab Fisica | Bloque B - Piso 1 | 20 | `FISICA` |
| 4 | Lab Quimica | Bloque C - Piso 1 | 18 | `QUIMICA` |

### Consola de base de datos

`http://localhost:8080/h2-console`

| Campo | Valor |
|---|---|
| JDBC URL | `jdbc:h2:mem:reservas_labs_db` |
| Usuario | `sa` |
| Contraseña | *(vacía)* |

La base es **en memoria**: se recrea en cada arranque (`spring.jpa.hibernate.ddl-auto=create-drop`).

---

## 4. Modelo de dominio y reglas de negocio

### Entidades

**`Laboratorio`** — `id` (IDENTITY), `nombre` (único), `ubicacion`, `capacidad`,
`tipo` ∈ {`COMPUTO`, `FISICA`, `QUIMICA`}.

**`Reserva`** — `id` (IDENTITY), `laboratorio` (`@ManyToOne` LAZY), `nombreSolicitante`,
`correoSolicitante`, `inicio`, `fin` (`LocalDateTime`), `estado` ∈ {`ACTIVA`, `CANCELADA`}.

### Reglas aplicadas en `ReservaService.crear(...)`, en este orden

| # | Regla | Incumplimiento |
|---|---|---|
| 1 | Datos obligatorios (laboratorio, solicitante, correo, inicio, fin) | `IllegalArgumentException` → **400** |
| 2 | El laboratorio debe existir | `RecursoNoEncontradoException` → **404** |
| 3 | `inicio` anterior a `fin`, y ambos el mismo día | `IllegalArgumentException` → **400** |
| 4 | Jornada de atención: de **07:00** a **21:00** | `IllegalArgumentException` → **400** |
| 5 | Duración entre **30 minutos** y **3 horas** | `IllegalArgumentException` → **400** |
| 6 | Sin solapamiento con otra reserva **activa** del mismo laboratorio | `ReservaConflictException` → **409** |

El orden importa: un laboratorio inexistente produce **404** aunque el horario también sea inválido.

`cancelar(id)` marca la reserva como `CANCELADA`; cancelar una ya cancelada produce **409**.

### Semántica del solapamiento

Las franjas se tratan como intervalos **semiabiertos** `[inicio, fin)`. Dos reservas chocan si:

```
inicioExistente < finNueva   ∧   finExistente > inicioNueva
```

Consecuencia práctica: una reserva de **09:00–11:00** y otra de **11:00–12:00** son compatibles —el
borde se comparte sin conflicto—, mientras que **10:00–12:00** sí choca con la primera. La condición
se resuelve en la base de datos mediante la consulta JPQL `ReservaRepository.buscarSolapamientos`,
que además filtra por `estado = ACTIVA`, de modo que una reserva cancelada libera su franja.

---

## 5. API REST

Base: `http://localhost:8080`

### Reservas

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/reservas` | Lista todas las reservas ordenadas por inicio | `200` |
| `GET` | `/api/reservas?laboratorioId={id}` | Filtra por laboratorio | `200`, `404` |
| `GET` | `/api/reservas/{id}` | Detalle de una reserva | `200`, `404` |
| `POST` | `/api/reservas` | Crea una reserva | `201` + `Location`, `400`, `404`, `409` |
| `PATCH` | `/api/reservas/{id}/cancelar` | Cancela una reserva activa | `200`, `404`, `409` |

### Laboratorios

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/laboratorios` | Lista los laboratorios por nombre | `200` |
| `GET` | `/api/laboratorios/{id}` | Detalle de un laboratorio | `200`, `404` |
| `POST` | `/api/laboratorios` | Registra un laboratorio | `201` + `Location`, `400`, `409` |
| `GET` | `/api/laboratorios/{id}/reservas` | Reservas de ese laboratorio | `200`, `404` |

### Ejemplos

```bash
# Crear una reserva
curl -i -X POST http://localhost:8080/api/reservas \
  -H 'Content-Type: application/json' \
  -d '{
        "laboratorioId": 1,
        "nombreSolicitante": "Alejandro Mora",
        "correoSolicitante": "tech@piremos.com",
        "inicio": "2026-10-05T09:00",
        "fin":    "2026-10-05T11:00"
      }'
```

```
HTTP/1.1 201
Location: /api/reservas/1

{"id":1,"laboratorioId":1,"laboratorioNombre":"Lab Computo 1",
 "nombreSolicitante":"Alejandro Mora","correoSolicitante":"tech@piremos.com",
 "inicio":"2026-10-05T09:00:00","fin":"2026-10-05T11:00:00","estado":"ACTIVA"}
```

```bash
# Cancelar
curl -X PATCH http://localhost:8080/api/reservas/1/cancelar
```

### Formato uniforme de error

Todos los errores de la API los produce `GlobalRestExceptionHandler` con la misma estructura:

```jsonc
{
  "marcaTiempo": "2026-09-25T10:42:15.345972",
  "estado": 409,
  "error": "Conflict",
  "mensaje": "El laboratorio Lab Computo 1 ya tiene una reserva activa que se cruza con el horario solicitado",
  "ruta": "/api/reservas",
  "detalles": {}
}
```

`detalles` solo se llena en los **400 de validación de formato**, con un mensaje por campo:

```jsonc
{
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "La solicitud contiene datos invalidos",
  "ruta": "/api/reservas",
  "detalles": {
    "laboratorioId": "El laboratorio es obligatorio",
    "nombreSolicitante": "El nombre del solicitante es obligatorio",
    "correoSolicitante": "El correo no tiene un formato valido"
  }
}
```

| Código | Cuándo | Excepción de origen |
|---|---|---|
| `400` | Formato inválido (`@Valid`), JSON ilegible, regla de horario o duración | `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `IllegalArgumentException` |
| `404` | Laboratorio o reserva inexistente | `RecursoNoEncontradoException` |
| `409` | Solapamiento, reserva ya cancelada, laboratorio duplicado | `ReservaConflictException`, `RecursoDuplicadoException` |
| `409` | Restricción de integridad de la base de datos (alta simultánea del mismo nombre) | `DataIntegrityViolationException` |

---

## 6. Vistas MVC (Thymeleaf)

| Método | Ruta | Vista / efecto |
|---|---|---|
| `GET` | `/` | Redirige a `/reservas` |
| `GET` | `/reservas` | `reservas/lista.html` — tabla de reservas con su estado y mensajes flash |
| `GET` | `/reservas/nueva` | `reservas/nueva.html` — formulario con selector de laboratorios |
| `POST` | `/reservas` | Crea y redirige a `/reservas` con mensaje de éxito |
| `POST` | `/reservas/{id}/cancelar` | Cancela y redirige a `/reservas` con mensaje de éxito |

- **`lista.html`** muestra laboratorio, solicitante, correo, inicio, fin y estado (distintivo verde
  para `ACTIVA`, gris para `CANCELADA`), el botón *Cancelar* solo en las reservas activas —dentro de
  un formulario `POST`, nunca un enlace `GET`— y un aviso cuando no hay reservas.
- **`nueva.html`** publica un `<select>` de laboratorios, dos `<input type="datetime-local">` y un
  bloque desplegable con los errores de validación del formulario y con el mensaje de negocio
  recibido por *flash*.
- Las fechas llegan a la plantilla **ya formateadas** (`dd/MM/yyyy HH:mm`) dentro del modelo de vista
  `ReservaVista`, de modo que las plantillas no navegan entidades JPA ni dependen de `#temporals`.
- El texto de ayuda del formulario (horario y duración permitidos) se construye con `ReglasDeReserva`
  a partir de las constantes de `ReservaService`: cambiar la jornada en el servicio cambia también lo
  que lee el usuario, sin tocar el HTML.

---

## 7. Pruebas

```bash
mvn clean test
```

| Clase | Tipo | Pruebas | Qué cubre |
|---|---|---|---|
| `ReservaServiceTest` | Unitaria (Mockito) | 31 | Creación (con `ArgumentCaptor` sobre lo que realmente se guarda, incluido el `trim`), laboratorio inexistente, solapamiento, jornada, duración, orden de fechas, cruce de días, datos obligatorios, cancelación y doble cancelación |
| `ReservaServiceTest$BordesValidos` | Unitaria | 4 | Los límites **inclusivos**: 07:00, 21:00, exactamente 30 min y exactamente 3 h se aceptan |
| `ReservaRepositoryTest` | `@DataJpaTest` sobre H2 | 9 | La JPQL `buscarSolapamientos`: cruce parcial, contigua por el inicio y por el fin, envolvente, contenida, idéntica, otro día, reserva cancelada y otro laboratorio |
| `ReservaControllerTest` | `@WebMvcTest` | 12 | `/api/reservas`: `200`, `201` + `Location`, `400` con `detalles`, `400` por regla de negocio, `400` por JSON ilegible, `404`, `409`, y el filtro por `laboratorioId` |
| `LaboratorioControllerTest` | `@WebMvcTest` | 10 | `/api/laboratorios`: listado, detalle, `404`, alta `201`, `409` por nombre repetido, `400` por capacidad inválida y reservas del laboratorio |
| `ReservaWebControllerTest` | `@WebMvcTest` (renderiza Thymeleaf real) | 12 | `/reservas`: vistas, HTML renderizado con las fechas ya formateadas, botón de cancelar solo en las activas, redirecciones, mensajes flash y el enrutado del *advice* según el origen del error |
| `InicioWebControllerTest` | `@WebMvcTest` | 1 | `/` redirige a `/reservas` |
| `DatosInicialesTest` | Unitaria (Mockito) | 4 | La siembra ocurre solo con la tabla vacía, y con los 4 laboratorios esperados |
| `ReservasLabsIntegracionTest` | `@SpringBootTest` + `MockMvc`, sin mocks | 7 | Extremo a extremo contra H2 real: alta, consulta con el laboratorio resuelto, solapamiento, franja contigua, cancelación persistida por *dirty checking*, liberación de la franja, la vista MVC y el `409` por laboratorio duplicado |
| `ReservasLabsApplicationTests` | `@SpringBootTest` | 1 | El contexto de Spring se levanta completo |

**Total: 87 pruebas.**

### La suite tiene dientes

Se comprobó con tres mutaciones deliberadas sobre el código de producción; las tres hacen fallar la
suite, lo que descarta que las pruebas sean tautológicas:

| Mutación introducida | Resultado |
|---|---|
| En la JPQL, `r.inicio < :fin` → `r.inicio <= :fin` | 1 fallo (`reservaContiguaPorElInicioNoSolapa`) |
| En `validarJornada`, adelantar la apertura una hora | 1 error (`BordesValidos`, la reserva de 07:00 deja de aceptarse) |
| Quitar `@EntityGraph` de `findById` | 3 errores con `LazyInitializationException` en `ReservasLabsIntegracionTest` |

Cada nivel prueba lo que le corresponde: la lógica con dobles, la consulta contra una base real y
cada canal de entrega contra su propio contrato.

---

## 8. Decisiones de diseño

### Punto de decisión 1 — ¿Dónde viven las reglas de negocio?

**Decisión: exclusivamente en `ReservaService`.**

La tentación natural en un proyecto con dos canales es validar en el sitio donde el dato llega: un
`if` en el controlador REST y otro en el controlador web. Eso produce dos implementaciones de la
misma regla que divergen en la primera modificación.

Aquí los controladores **no contienen ni una condición de negocio**. `ReservaController` traduce el
DTO a un comando y devuelve el resultado; `ReservaWebController` hace lo mismo con el formulario.
Jornada, duración y solapamiento están en métodos privados de `ReservaService`
(`validarJornada`, `validarDuracion`, `validarDisponibilidad`), uno por regla, con los límites
extraídos a constantes públicas:

```java
public static final LocalTime HORA_APERTURA   = LocalTime.of(7, 0);
public static final LocalTime HORA_CIERRE     = LocalTime.of(21, 0);
public static final Duration DURACION_MINIMA  = Duration.ofMinutes(30);
public static final Duration DURACION_MAXIMA  = Duration.ofHours(3);
```

Las reglas temporales se resuelven con **Java puro** (`LocalTime`, `Duration`) y no con anotaciones
de Bean Validation, porque son reglas del negocio y no del formato del mensaje: cambiar el horario de
atención debe ser un cambio en el dominio, no en el DTO de un canal. La comprobación de solapamiento
sí se delega a la base de datos, porque depende del estado persistido y no del contenido de la
petición (ver punto 4 de las decisiones complementarias).

*Consecuencia verificable:* la prueba de "fuera de jornada" vive en `ReservaServiceTest` y no necesita
`MockMvc`; y el mismo rechazo se obtiene por REST (`400`) y por la vista (mensaje flash) sin escribir
la regla dos veces.

---

### Punto de decisión 2 — ¿Qué cruza la frontera HTTP: entidades o DTOs?

**Decisión: la entidad nunca sale de la capa de servicio. Cada canal tiene su propio contrato.**

| Capa | Objeto | Motivo |
|---|---|---|
| REST, entrada | `CrearReservaRequest` (`record` + Bean Validation) | Contrato JSON estable e inmutable |
| REST, salida | `ReservaResponse` (`record`) | Aplana `laboratorio` a `laboratorioId` + `laboratorioNombre` |
| MVC, entrada | `ReservaFormulario` (getters/setters) | `th:field` exige una propiedad mutable para el *binding* bidireccional |
| MVC, salida | `ReservaVista` (`record` con textos ya formateados) | La plantilla no navega entidades ni formatea fechas |
| Servicio, entrada | `NuevaReservaCommand` (`record`) | Contrato único del caso de uso, independiente del canal |

Exponer `Reserva` directamente habría sido más corto y habría acoplado tres cosas indeseables: el
esquema de la base de datos al contrato público, la serialización JSON a una relación `LAZY`
—`LazyInitializationException` en cuanto Jackson toque `getLaboratorio()` fuera de la transacción— y
la plantilla Thymeleaf al modelo de persistencia.

La asimetría entre `ReservaFormulario` (mutable) y el resto (`record` inmutables) es **deliberada**:
es el precio exacto del *data binding* de Spring MVC, y queda confinado a una única clase de la capa
web en lugar de contaminar el dominio.

*Consecuencia verificable:* `ReservaVista` entrega `inicio` como `"05/10/2026 09:00"`; la plantilla
solo imprime cadenas, y por eso `ReservaWebControllerTest` puede renderizar las vistas de verdad sin
una base de datos detrás.

---

### Punto de decisión 3 — ¿Cómo comparten REST y MVC la misma lógica sin duplicarla?

**Decisión: un único bean `ReservaService` inyectado por constructor en ambos controladores, con un
comando común como contrato de entrada.**

Spring gestiona `ReservaService` como *singleton*; `ReservaController` y `ReservaWebController`
reciben **la misma instancia**:

```java
@RestController @RequestMapping("/api/reservas") @RequiredArgsConstructor
public class ReservaController {
    private final ReservaService reservaService;   // ← mismo bean
}

@Controller @RequestMapping("/reservas") @RequiredArgsConstructor
public class ReservaWebController {
    private final ReservaService reservaService;   // ← mismo bean
}
```

La inyección es **por constructor y sobre campos `final`**, no por `@Autowired` sobre el campo. Esto
no es una preferencia estética: un campo `final` asignado en el constructor hace el objeto seguro
frente a hilos una vez construido, deja la dependencia explícita en la firma —imposible olvidarla— y
permite instanciar la clase en una prueba unitaria sin levantar Spring, que es exactamente lo que
hace `ReservaServiceTest` con `@InjectMocks`.

La pieza que evita el acoplamiento es `NuevaReservaCommand`: el servicio **no acepta ni
`CrearReservaRequest` ni `ReservaFormulario`**. Si lo hiciera, la capa de negocio importaría clases de
un canal de entrega e invertiría la dirección de las dependencias. Cada canal convierte lo suyo:

```java
// controller/dto/CrearReservaRequest.java  y  web/dto/ReservaFormulario.java
public NuevaReservaCommand aComando() { … }
```

Añadir mañana un tercer canal —una CLI, un consumidor de mensajería— cuesta una clase que sepa
construir el comando; ni una línea del negocio cambia.

*Consecuencia verificable:* la misma reserva solapada devuelve `409` por REST y un mensaje flash en
la vista, y ambos textos salen del mismo `String` construido una sola vez en `ReservaService`.

---

### Punto de decisión 4 — ¿Cómo se traduce un error de negocio a dos canales con expectativas opuestas?

**Decisión: una excepción de dominio, dos traductores independientes, seleccionados por su ámbito.**

Un cliente REST espera un código HTTP y un cuerpo JSON; un navegador espera un `302` y una página
legible. La misma excepción no puede satisfacer a ambos, pero **tampoco debe conocerlos**: por eso
`ReservaService` lanza excepciones del dominio (`ReservaConflictException`,
`RecursoNoEncontradoException`, `IllegalArgumentException`) sin ninguna referencia a HTTP, y la
traducción vive en dos *advice* con ámbitos disjuntos:

```java
@RestControllerAdvice(annotations = RestController.class)          // solo los @RestController
public class GlobalRestExceptionHandler { … }                      // → JSON 400 / 404 / 409

@ControllerAdvice(assignableTypes = ReservaWebController.class)    // solo el controlador web
public class ReservaWebExceptionHandler { … }                      // → redirect + RedirectAttributes
```

El detalle fino está en cómo se **acota** cada uno. Un `@RestControllerAdvice` sin atributos habría
capturado también las excepciones del controlador MVC y habría devuelto JSON a un navegador; por eso
se restringe con `annotations = RestController.class`. En el lado web, `assignableTypes` limita el
manejo al controlador de reservas y evita convertir en redirección cualquier error de otra parte de
la aplicación.

Las dos traducciones:

| Excepción | REST | MVC |
|---|---|---|
| `RecursoNoEncontradoException` | `404` + cuerpo `ErrorResponse` | `redirect:/reservas` + flash `mensajeError` |
| `ReservaConflictException` | `409` + cuerpo `ErrorResponse` | `redirect:` según el origen + flash `mensajeError` |
| `IllegalArgumentException` | `400` + cuerpo `ErrorResponse` | `redirect:` según el origen + flash `mensajeError` |
| `MethodArgumentNotValidException` | `400` + mapa `detalles` | *(no aplica: el formulario usa `BindingResult`)* |

El destino de la redirección MVC **no está cableado a una ruta fija**. `ReservaWebExceptionHandler`
recibe el `HandlerMethod` que falló y comprueba si aceptaba un `ReservaFormulario`:

```java
private String destinoDe(HandlerMethod origen) {
    return recibiaFormulario(origen) ? REDIRECCION_FORMULARIO : REDIRECCION_LISTA;
}
```

Así, el mismo `409` devuelve al formulario cuando el usuario intentaba **crear** una reserva sobre una
franja ocupada —que es donde puede corregir— y al listado cuando intentaba **cancelar** una reserva ya
cancelada, donde no hay formulario que arreglar. Mandar siempre al formulario, que es lo que sale de
escribir la ruta a mano en el *advice*, deja al usuario en una página que no tiene nada que ver con lo
que estaba haciendo.

La validación de **formato** se maneja distinto a propósito. En REST, `@Valid` sobre el `@RequestBody`
produce una `MethodArgumentNotValidException` que el *advice* convierte en un `400` con el desglose
campo a campo. En MVC no llega nunca al *advice*: el controlador recibe un `BindingResult`, detecta
`hasErrors()` y **devuelve la misma vista** con los datos que el usuario ya había escrito, que es lo
único aceptable en un formulario. El *advice* web queda reservado para los errores de negocio, que sí
ocurren después de un `POST` válido y por tanto exigen el patrón *POST-Redirect-GET* con `flash`
—de lo contrario, recargar la página reenviaría la reserva.

*Consecuencia verificable:* `POST /api/reservas` con una franja ocupada devuelve
`{"estado":409,…}`; el mismo intento desde el formulario responde `302 → /reservas/nueva` y la página
muestra el aviso en rojo.

---

### Decisiones complementarias

1. **`spring.jpa.open-in-view=false`.** Spring Boot deja la sesión de Hibernate abierta hasta el
   renderizado por defecto, lo que disimula los accesos `LAZY` tardíos y provoca consultas
   imprevistas durante la vista. Al desactivarlo, cualquier navegación fuera de la transacción
   falla de inmediato, así que la carga se declara donde corresponde: en el repositorio, con
   `@EntityGraph(attributePaths = "laboratorio")`. El resultado es una sola consulta con `JOIN FETCH`
   en lugar del problema N+1.

2. **Transaccionalidad declarada en el servicio.** `@Transactional` a nivel de clase y
   `@Transactional(readOnly = true)` en las consultas. La frontera transaccional coincide con el caso
   de uso, no con la petición HTTP ni con el repositorio. Gracias a esto `cancelar(...)` no necesita
   llamar a `save(...)`: la entidad está gestionada y el *dirty checking* sincroniza el cambio al
   cerrar la transacción.

3. **Entidades con comportamiento, sin `setters`.** `Reserva` expone `cancelar()`, `estaActiva()` y
   `estaCancelada()` en lugar de un `setEstado(...)` público. La invariante «una reserva cancelada no
   se cancela dos veces» queda protegida por la propia entidad (`IllegalStateException`), mientras el
   servicio se ocupa de traducirla al lenguaje de la aplicación (`ReservaConflictException`, `409`).
   Inmutabilidad total no es posible en JPA —el proveedor necesita un constructor sin argumentos—, así
   que se acota: constructor por defecto `protected`, sin `setters` y construcción vía `@Builder`.

4. **El solapamiento se resuelve en la base de datos, no en memoria.** Cargar todas las reservas del
   laboratorio y filtrarlas en Java sería `O(n)` en memoria y crecería sin límite. La JPQL
   `buscarSolapamientos` traslada el predicado al motor, que puede resolverlo con índices.

5. **Tres excepciones de dominio, no dos.** El enunciado exige `RecursoNoEncontradoException` y
   `ReservaConflictException`. Se añadió `RecursoDuplicadoException` para el alta de laboratorios con
   nombre repetido: reutilizar `ReservaConflictException` para un conflicto que no involucra ninguna
   reserva habría hecho mentir al nombre de la clase. Las reglas de horario y duración usan
   `IllegalArgumentException` estándar, ya que describen un argumento inválido y no un estado del
   sistema en conflicto.

6. **`GenerationType.IDENTITY`.** Delega la generación del identificador a la columna autoincremental
   de H2. Es la estrategia que pide el enunciado y la más predecible en una base en memoria; su
   contrapartida conocida —impide el agrupamiento de `INSERT`— es irrelevante a esta escala.

---

## 9. Limitaciones conocidas

Tres decisiones se tomaron a conciencia y conviene dejarlas por escrito antes de que alguien las
descubra como si fueran descuidos:

1. **La comprobación de solapamiento es un *check-then-act* sin bloqueo.** `crear(...)` consulta
   `buscarSolapamientos` y, si no hay conflicto, inserta. Entre ambas operaciones cabe otra
   transacción: dos peticiones simultáneas con la misma franja pueden pasar las dos. Resolverlo
   correctamente exige bloqueo pesimista (`SELECT … FOR UPDATE` sobre el laboratorio), nivel de
   aislamiento `SERIALIZABLE` o una restricción de exclusión de rangos en la base —ninguna de las
   tres es expresable con JPQL portable, y todas quedan fuera del alcance de esta unidad. La ventana
   real es de milisegundos y el único efecto es una reserva duplicada, no una corrupción de datos.

2. **Un error de negocio vacía el formulario.** El *advice* web sigue el patrón *POST-Redirect-GET*
   que exige el enunciado, y en un `@ExceptionHandler` Spring construye un `ModelAndViewContainer`
   nuevo: el objeto que el usuario acababa de rellenar no está disponible para reenviarlo por flash.
   Conservarlo requeriría `@SessionAttributes` en el controlador, es decir, estado de sesión para un
   caso de error. Se prefirió el flujo simple y sin sesión; el mensaje de error sí llega intacto.

3. **La API no autentica ni autoriza.** Cualquiera puede crear o cancelar una reserva a nombre de
   quien sea. La unidad trata de arquitectura en capas, no de seguridad; añadir Spring Security sería
   un canal transversal más, no un cambio en el diseño de capas.

---

## 10. Conclusiones sobre la arquitectura en capas

**La capa no es un directorio, es una dirección.** Lo que convierte esto en una arquitectura en capas
no son los siete paquetes, sino que las dependencias apunten siempre hacia el dominio. `model` no
importa nada; `service` no sabe que existe HTTP; `controller` y `web` conocen a `service` y jamás al
revés. Basta con que una entidad importe un DTO de la capa web para que las capas se conviertan en
carpetas decorativas.

**Un caso de uso, varios canales de entrega.** El ejercicio deja ver que REST y MVC no son
arquitecturas rivales sino dos *adaptadores* del mismo núcleo. `ReservaService` no cambió ni una línea
al añadir las vistas; lo único que hizo falta fue un traductor de formulario a comando y otro de
entidad a modelo de vista. Ese es el rendimiento real de haber puesto las reglas en un solo sitio:
el segundo canal costó una capa de traducción, no una reimplementación.

**El contrato de entrada del negocio debe ser propio.** Que `ReservaService.crear` reciba
`NuevaReservaCommand` y no el DTO de un canal parece un rodeo hasta que se añade el segundo canal: sin
esa pieza, la capa de negocio habría terminado importando clases anotadas con `@NotNull` de Jakarta
Validation y pensadas para JSON, y la dirección de las dependencias se habría invertido en silencio.

**El manejo de errores revela dónde están de verdad las fronteras.** La excepción de dominio es una y
viaja hacia arriba sin saber a dónde va; lo que cambia es quién la traduce. Dos `@ControllerAdvice`
acotados por `annotations` y por `assignableTypes` permiten que el mismo `409` conceptual sea un
cuerpo JSON para un cliente y un mensaje en rojo para un navegador, sin un solo `if` que pregunte
«¿quién me está llamando?».

**Las capas se pagan y hay que saber por qué.** Cinco objetos para una reserva —`request`, `comando`,
entidad, `response`, vista— son más código que exponer la entidad de extremo a extremo. Lo que se
compra a cambio es concreto y comprobable: el contrato JSON no se mueve cuando cambia el esquema de la
tabla, las reglas se prueban sin levantar Spring, la relación `LAZY` no explota al serializar y un
tercer canal no obliga a tocar el dominio. En un sistema de tres clases no compensaría; a partir de
dos canales de entrega y media docena de reglas, sí.
