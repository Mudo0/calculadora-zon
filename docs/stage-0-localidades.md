# Stage 0 — Revisión de localidades de Córdoba

Se repitieron las 515 consultas de GeoRef con Photon local. Las 449 coincidencias
originales se mantuvieron. Para las 66 restantes se obtuvieron 9 alias candidatos
y se propuso usar el centroide oficial de GeoRef para los otros 57 casos.

Un alias candidato requiere un único resultado de Photon con el nombre alternativo
normalizado a no más de 2 km del centroide de GeoRef. La proximidad orienta la
revisión de identidad. Los casos compuestos conservan su nombre y centroide
oficial con el ID de GeoRef, aunque aparezca uno de sus componentes en Photon.
Si una búsqueda sigue siendo ambigua, se deberá pedir el departamento o marcar
el origen para revisión.

La columna de coordenadas usa el orden longitud, latitud. La comparación de
515 se hizo contra el índice actual. Los alias y el respaldo todavía no están
integrados en ZON. Cuando estén disponibles, repetir las consultas y verificar
que cada nombre y departamento resuelvan el origen esperado, en especial los
homónimos y las localidades censales compuestas.

Fuente: [GeoRef — localidades de Córdoba](https://apis.datos.gob.ar/georef/api/v2.0/localidades?provincia=14&max=5000).

| ID GeoRef | Localidad | Departamento | Propuesta | Nombre en Photon | Distancia en km | Centroide GeoRef |
| --- | --- | --- | --- | --- | ---: | --- |
| 14007020 | Arroyo San Antonio | Calamuchita | GeoRef · respaldo | — | — | -64.607798047, -32.256225120 |
| 14007050 | El Corcovado - El Torreón | Calamuchita | GeoRef · compuesto o calificado | — | — | -64.503477855, -32.140434340 |
| 14007160 | Parque Calmayo | Calamuchita | GeoRef · respaldo | — | — | -64.464679067, -32.025469880 |
| 14007190 | San Ignacio (Loteo San Javier) | Calamuchita | GeoRef · compuesto o calificado | — | — | -64.517909727, -32.165905283 |
| 1400721003 | San Ignacio (Loteo Vélez Crespo) | Calamuchita | GeoRef · compuesto o calificado | — | — | -64.537142167, -32.098950613 |
| 1400727002 | Va.Ciudad Pque.Los Reartes (1° Sección) | Calamuchita | GeoRef · compuesto o calificado | — | — | -64.525912299, -31.914248141 |
| 1400727003 | Va.Ciudad Pque.Los Reartes (3° Sección) | Calamuchita | GeoRef · compuesto o calificado | — | — | -64.561366682, -31.904934351 |
| 14007320 | Villa La Rivera | Calamuchita | GeoRef · respaldo | — | — | -64.524715383, -32.266632505 |
| 1400712002 | Villa San Miguel | Calamuchita | GeoRef · respaldo | — | — | -64.374997106, -31.863428358 |
| 1401401001 | Jardín Arenales | Capital | GeoRef · respaldo | — | — | -64.060522311, -31.368585102 |
| 1402131004 | 1° de Agosto | Colón | GeoRef · respaldo | — | — | -64.178144446, -31.307806181 |
| 1402131005 | Allmirante Brown | Colón | GeoRef · respaldo | — | — | -64.182727841, -31.307489714 |
| 1402131006 | Ciudad de los Niños | Colón | GeoRef · respaldo | — | — | -64.177328513, -31.295749989 |
| 1402115001 | Dumesnil | Colón | GeoRef · respaldo | — | — | -64.337166416, -31.336528266 |
| 1402115003 | El Diquecito | Colón | GeoRef · respaldo | — | — | -64.365321405, -31.351083671 |
| 1402125001 | El Pueblito | Colón | GeoRef · respaldo | — | — | -64.292290099, -31.106199583 |
| 14021130 | General Paz | Colón | GeoRef · respaldo | — | — | -64.140150624, -31.134480643 |
| 1402131001 | Guiñazú Norte | Colón | GeoRef · respaldo | — | — | -64.175442375, -31.307733967 |
| 1402132001 | Juárez Celman | Colón | GeoRef · nivel territorial distinto | Estación Juárez Celman | 0.219 | -64.166243094, -31.258042012 |
| 1402131002 | Parque Norte | Colón | GeoRef · respaldo | — | — | -64.176962424, -31.300499017 |
| 14021310 | Parque Norte - Ciudad de los Niños - Guiñazú Norte | Colón | GeoRef · compuesto o calificado | — | — | -64.176190867, -31.312414626 |
| 1402132002 | Villa Los Llanos | Colón | GeoRef · respaldo | — | — | -64.160904886, -31.276465429 |
| 14021320 | Villa Los Llanos - Juárez Celman | Colón | GeoRef · compuesto o calificado | — | — | -64.164615800, -31.275729827 |
| 1402131007 | Villa Pastora | Colón | GeoRef · respaldo | — | — | -64.179187316, -31.305815971 |
| 14028120 | Las Cañadas | Cruz del Eje | GeoRef · respaldo | — | — | -64.734725361, -30.914993056 |
| 14028170 | San Marcos Sierra | Cruz del Eje | GeoRef · respaldo | — | — | -64.636189565, -30.783012898 |
| 14035020 | Estación Lecueder | General Roca | Alias validado | Lecueder | 0.100 | -64.810684472, -34.496089630 |
| 14035030 | Hipólito Bouchard | General Roca | GeoRef · respaldo | — | — | -63.507264674, -34.723874431 |
| 14035110 | Santa Magdalena | General Roca | GeoRef · respaldo | — | — | -63.944039472, -34.518814618 |
| 14042190 | Villa Oeste | General San Martín | GeoRef · respaldo | — | — | -63.283458353, -32.416942409 |
| 14063040 | Capitán General Bernardo O'Higgins | Marcos Juárez | GeoRef · respaldo | — | — | -62.268834589, -33.248256579 |
| 14084060 | Río Bamba | Presidente Roque Sáenz Peña | GeoRef · respaldo | — | — | -63.733065523, -34.051372043 |
| 14091200 | San Roque | Punilla | GeoRef · respaldo | — | — | -64.451103393, -31.345298860 |
| 1409102002 | San Roque del Lago | Punilla | GeoRef · respaldo | — | — | -64.451972815, -31.323906579 |
| 14091300 | Villa Río Icho Cruz | Punilla | Alias validado | Icho Cruz | 0.108 | -64.564726293, -31.482675029 |
| 14098030 | Alcira Gigena | Río Cuarto | Alias validado | Alcira | 0.008 | -64.334033416, -32.758829230 |
| 14098120 | La Carolina | Río Cuarto | GeoRef · respaldo | — | — | -64.725112880, -33.186844322 |
| 14098180 | Las Peñas | Río Cuarto | GeoRef · respaldo | — | — | -64.104572596, -32.534705705 |
| 14098260 | Santa Catalina Holmberg | Río Cuarto | Alias validado | Santa Catalina | 0.015 | -64.437828682, -33.205189971 |
| 14098280 | Tosquitas | Río Cuarto | GeoRef · respaldo | — | — | -64.457670074, -33.819683625 |
| 14105190 | Montecristo | Río Primero | Alias validado | Monte Cristo | 0.005 | -63.947225299, -31.341986020 |
| 14105230 | Pueblo Comechingones | Río Primero | GeoRef · respaldo | — | — | -63.605325990, -31.171488727 |
| 14105260 | Santa Rosa de Río Primero | Río Primero | GeoRef · respaldo | — | — | -63.399412602, -31.153527657 |
| 14112090 | San Pedro de Gütemberg | Río Seco | GeoRef · equivalencia sin confirmar | San Pedro de Gutenberg | 0.076 | -63.560688021, -29.698365022 |
| 14112120 | Villa Candelaria | Río Seco | GeoRef · respaldo | — | — | -63.355126119, -29.812910245 |
| 14119070 | Costasacate | Río Segundo | GeoRef · respaldo | — | — | -63.758868660, -31.645319233 |
| 14126020 | Arroyo Los Patos | San Alberto | GeoRef · respaldo | — | — | -65.004348905, -31.762599051 |
| 1413319001 | Alto Resbaloso - El Barrial | San Javier | GeoRef · compuesto o calificado | — | — | -65.065810088, -31.960292972 |
| 1413319003 | El Valle | San Javier | GeoRef · respaldo | — | — | -65.064491322, -31.945167969 |
| 1413319004 | Las Chacras | San Javier | GeoRef · respaldo | — | — | -65.025124751, -31.941965032 |
| 14133170 | San Javier y Yacanto | San Javier | GeoRef · compuesto o calificado | — | — | -65.030268367, -32.026469651 |
| 14140070 | Colonia 10 de Julio | San Justo | GeoRef · respaldo | — | — | -62.112434416, -30.525237488 |
| 14140290 | Miramar | San Justo | Alias validado | Miramar de Ansenuza | 0.454 | -62.675009545, -30.915153180 |
| 1414703001 | Barrio Gilbert | Santa María | GeoRef · respaldo | — | — | -64.318002351, -31.441672213 |
| 14147030 | Barrio Gilbert (1º de Mayo) - Tejas Tres | Santa María | GeoRef · compuesto o calificado | — | — | -64.317995652, -31.439710418 |
| 1414731001 | Barrio Villa del Parque | Santa María | GeoRef · respaldo | — | — | -64.516386227, -31.798131561 |
| 1414732002 | La Donosa | Santa María | GeoRef · respaldo | — | — | -64.382051908, -31.621316685 |
| 14147170 | La Rancherita y Las Cascadas | Santa María | GeoRef · compuesto o calificado | — | — | -64.459039075, -31.754829253 |
| 1414719001 | Las Quintas | Santa María | GeoRef · respaldo | — | — | -64.303445067, -31.537683006 |
| 1414735001 | Mi Valle | Santa María | GeoRef · respaldo | — | — | -64.356257425, -31.584489999 |
| 1414703002 | Tejas Tres | Santa María | GeoRef · respaldo | — | — | -64.314610064, -31.440219850 |
| 14147310 | Villa Ciudad de América | Santa María | GeoRef · respaldo | — | — | -64.508654756, -31.791879762 |
| 1414731002 | Villa Ciudad de América | Santa María | GeoRef · respaldo | — | — | -64.530521273, -31.783243672 |
| 14161040 | Dalmacio Vélez | Tercero Arriba | Alias validado | Dalmacio Vélez Sarsfield | 0.301 | -63.583491031, -32.608600362 |
| 14168010 | Candelaria Sur | Totoral | GeoRef · respaldo | — | — | -63.801268051, -30.838967446 |
| 14182250 | San Marcos | Unión | GeoRef · respaldo | — | — | -62.484248004, -32.630235881 |
## Decisión para la integración

Usar siete alias con el departamento correspondiente y conservar los centroides
de GeoRef como respaldo para los otros 59 casos. Los nombres y distancias de
Photon de los dos casos descartados quedan en la tabla como evidencia de la
consulta. Las equivalencias se validaron combinando fuentes públicas y la
proximidad medida con el índice local.

| Localidad | Decisión y evidencia |
| --- | --- |
| Estación Lecueder | Aceptar «Lecueder». El nomenclador provincial usa ese nombre en General Roca y el listado nacional ubica «Estación Lecueder» en el mismo entorno. [Córdoba](https://www.cba.gov.ar/wp-content/uploads/2024/06/2025-NOMENCLADOR-UNICO-DE-LOCALIDADES.pdf) · [Listado nacional](https://www.argentina.gob.ar/normativa/274618_res3687-2_pdf/archivo) |
| Villa Río Icho Cruz | Aceptar «Icho Cruz». La historia municipal usa las dos formas. [Municipalidad](https://ichocruz.gob.ar/wp/history/) |
| Alcira Gigena | Aceptar «Alcira». La historia municipal documenta ambos nombres de la localidad. [Municipalidad](https://www.alcira-gigena.gob.ar/nuestra-historia/) |
| Santa Catalina Holmberg | Aceptar «Santa Catalina» en Río Cuarto. El Poder Judicial de Córdoba identifica la localidad como Santa Catalina (Holmberg). [Poder Judicial de Córdoba](https://dondevoto.justiciacordoba.gob.ar/Estatico/justiciaCordoba/files/Contenido/OficinasJudiciales/magistradosFuncionarios/N%C3%B3mina%20de%20magistrados%20y%20funcionarios%20judiciales.pdf) |
| Montecristo | Aceptar «Monte Cristo». Es la grafía municipal en Río Primero y el punto de Photon está a 5 m del centroide registrado. [Municipalidad](https://www.montecristo.gov.ar/contenidos/datos-generales-y-estadisticos-317) |
| Miramar | Aceptar «Miramar de Ansenuza» en San Justo. Córdoba Turismo usa el nombre corto y el extendido para la localidad junto a la laguna. [Córdoba Turismo](https://cordobaturismo.gov.ar/miramar-de-ansenuza/) |
| Dalmacio Vélez | Aceptar «Dalmacio Vélez Sarsfield» en Tercero Arriba. El listado provincial usa el nombre extendido y Nación registra la localidad como Dalmacio Vélez. [Córdoba](https://www.cba.gov.ar/wp-content/uploads/2020/10/Localidades-07.10.20.pdf) · [Argentina.gob.ar](https://www.argentina.gob.ar/node/362891) |
| Juárez Celman | Usar GeoRef para este ID. La municipalidad distingue el barrio Juárez Celman de la ciudad de Estación Juárez Celman, que abarca varios barrios. [Municipalidad](https://juarezcelman.gob.ar/ciudad/) |
| San Pedro de Gütemberg | Usar GeoRef para este ID. El catálogo provincial distingue San Pedro de Gütemberg y Gutemberg. La cercanía del resultado de Photon no bastó para aceptar su grafía alternativa. [Estadística de Córdoba](https://datosestadistica.cba.gov.ar/en/dataset/?depto=R%C3%ADo+Seco) |
