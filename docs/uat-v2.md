# Pruebas de aceptación de usuario (UAT) · WokControl v2.0 (PBI-30)

| Campo | Valor |
|---|---|
| Sistema | WokControl 2.0.0 (rama `sprint-4/cierre-v2`) |
| Ambiente | Render (entorno de pruebas) · `https://wokcontrol-s1-gateway-lu10.onrender.com` o local con `scripts/iniciar.ps1` |
| Usuario de aceptación | Administrador del restaurante (stakeholder del Product Backlog) |
| Conducen | Javier Steven Becerra Mantilla (PO) y Yuly Marcela Gómez Rico (SM) |
| Fecha de la sesión | 04/12/2026, en la Sprint Review |
| Datos | Usuarios y carta de demostración (`scripts/cargar-datos-demo.py`); ningún dato real de clientes |

Columna «Ejecución previa»: resultado de las pruebas automatizadas que cubren el escenario en la regresión del 27/11/2026 (264 pruebas en verde) y de las demos de las Reviews de los Sprints 1 a 3. Columna «Administrador»: la diligencia el administrador del restaurante en la sesión: **A** = aceptado, **O** = observación (se corrige y el escenario se repite antes del cierre, AC-C04.2).

## Épica 7 · Cliente en el salón

| # | Escenario | Resultado esperado | Ejecución previa | Administrador |
|---|---|---|---|---|
| U01 | Escanear el QR de la mesa 5 con el celular | Abre la carta con foto, precio y categoría; no aparecen los platos agotados | Cumple (AC-033.1) | |
| U02 | Agregar 2 «Arroz chino especial», cambiar a 3 y escribir «sin cebolla» | El pedido muestra 3 unidades, la observación y el total recalculado | Cumple (AC-032.1) | |
| U03 | Enviar el pedido y confirmarlo como mesero | Queda POR_CONFIRMAR; al confirmar pasa a cocina; el cliente ve el estado de cada plato | Cumple (AC-034.1, AC-037.1, AC-034.2) | |
| U04 | El cocinero agota un plato que está en el pedido sin enviar | El cliente ve el aviso y no puede enviar hasta quitarlo | Cumple (AC-038.1, AC-038.2) | |
| U05 | Pedir la cuenta desde el celular y cobrarla | El cajero ve «Cuenta solicitada»; al cobrar, la sesión vence y la mesa pasa a «por limpiar» | Cumple (AC-035.1, AC-036.2, AC-044.1) | |

## Épica 8 · Anfitrión y gestión del salón

| # | Escenario | Resultado esperado | Ejecución previa | Administrador |
|---|---|---|---|---|
| U06 | Registrar «Familia Rojas, 4 personas» en la lista de espera | Aparece al final con la hora y el tiempo de espera; sin nombre se rechaza | Cumple (AC-040.1, AC-040.2) | |
| U07 | Abrir el mapa del salón en una tablet | Ve cada mesa con su estado y capacidad y los totales libre / ocupada / por limpiar | Cumple (AC-041.1, AC-046.2) | |
| U08 | Sentar al grupo en la mesa 3 con el mesero Carlos | La mesa pasa a OCUPADA en las dos tablets en menos de 5 s; Carlos la ve | Cumple (AC-042.1, AC-046.1) | |
| U09 | Unir las mesas 4 y 5 para un grupo de 8 | Queda un grupo de 8 puestos OCUPADO; al liberarse se separan | Cumple (AC-045.1) | |
| U10 | El anfitrión intenta crear una mesa o abrir la caja | 403 / «Acceso no permitido» | Cumple (AC-043.1, AC-043.2) | |

## Épica 9 · Domicilios (CR-001)

| # | Escenario | Resultado esperado | Ejecución previa | Administrador |
|---|---|---|---|---|
| U11 | Registrar el domicilio de «Ana Pérez» con 2 platos y $4.500 de domicilio | Queda RECIBIDO, va a cocina y el total suma platos + impuesto + domicilio | Cumple (AC-047.1) | |
| U12 | Asignar el domicilio a Luis antes y después de que cocina lo marque listo | Antes: PEDIDO_NO_LISTO; después: ASIGNADO a Luis | Cumple (AC-048.1, AC-048.2) | |
| U13 | Luis abre «Mis entregas», lo marca en camino y cobra $50.000 en efectivo | Ve solo los suyos con el QR de la ruta; se genera el comprobante y el cambio es $5.000 | Cumple (AC-049.1, AC-050.1) | |
| U14 | Marta intenta abrir el domicilio de Luis | 403 | Cumple (AC-051.1) | |
| U15 | Luis marca otro domicilio «no entregado» con motivo | Se anula sin factura y queda en la auditoría; el cierre de caja del cajero incluye solo los cobrados | Cumple (AC-052.1, AC-050.1) | |

## Observaciones

| # | Escenario | Observación | Corrección | Repetido |
|---|---|---|---|---|
| | | | | |

## Acta de aceptación

Resultado: ____ escenarios aceptados de 15.

| Rol | Nombre | Firma | Fecha |
|---|---|---|---|
| Administrador del restaurante | | | |
| Product Owner | Javier Steven Becerra Mantilla | | |
| Scrum Master | Yuly Marcela Gómez Rico | | |
