# Wakker

App d'Android per saber què gasta cada aparell de casa i quant costa, amb un mesurador d'energia Shelly i sense cap servidor.

*English below.* Fins a la 1.0.2 es deia Consum: aquesta és la mateixa app, amb el nom nou i l'identificador nou (`cat.jordi.wakker`); Consum té una còpia de seguretat per passar-hi.

## Què fa

- **Ara:** el consum en directe, el preu del kWh d'aquesta hora i què hi ha encès.
  - Amb la potència contractada, quina part se'n fa servir. A la gràfica, en taronja des del 80 % i en vermell des del 90 %.
  - Un avís quan la casa passa del 90 %. Amb l'app tancada, al moment (amb una notificació fixa) o cada 15 minuts.
- **Historial:** el consum per dies, setmanes, mesos i anys, en kWh i en euros, amb fletxes i un calendari per anar als períodes anteriors i la comparació amb l'anterior. Les barres de cada dia van amb els mateixos colors que el rànquing d'aparells.
  - Els moments punta: el minut de més consum i els 5 que gastaven més, amb des de quan eren encesos. Si un salt no s'assembla a cap aparell conegut, l'app pregunta què era i ho aprèn.
- **Aparells:** el consum de cada aparell, comparat amb el que diu la seva etiqueta energètica.
  - Els aparells s'afegeixen amb un qüestionari estança per estança, escanejant el QR de l'etiqueta (la base de dades europea EPREL), buscant-ne la marca i el model a l'EPREL, fent una foto de la placa de característiques o, sense cap dada, només amb el tipus.
  - Cada aparell diu com funciona: sempre encès (router), s'engega sol (nevera, bomba de calor, termo) o l'engega l'usuari. Només aquests últims s'entrenen. El compressor de la nevera, l'app el troba sola a les dades de les nits.
  - Una ajuda diu on és la placa amb el model a cada tipus d'aparell. Una fitxa de l'EPREL, una foto o un PDF es poden compartir amb l'app des d'una altra.
  - L'app llegeix la potència de la foto de la placa amb Tesseract, dins del mòbil.
- **Per fer:** tot el que l'app et pregunta, en un sol lloc: el que més gasta i encara no té nom, el que es repeteix i els avisos de consum inesperat. Cada cosa es contesta d'un toc amb els aparells que s'hi assemblen, o amb qualsevol altre.
  - «Ensenya-li tu»: en directe, li dius quan encens o apagues un aparell, i l'app n'aprèn la potència.
  - També amb una estona passada: «el rentaplats va funcionar ahir de 17 a 21 h». L'app hi busca els salts, te'ls ensenya en una gràfica i tu marques els que eren l'aparell.
  - Si el consum es dispara i l'app no sap què és, t'ho pregunta amb una notificació. Tu tries a partir de quant (de 0,5 a 3 kW) i durant quant, i l'app et diu quants avisos t'hauria donat la setmana passada.
  - D'un aparell que s'engega sol i no gasta sempre igual (una bomba de calor inverter), n'aprèn l'interval de potència amb una sola resposta, i no te'n torna a preguntar.
- **El preu de la llum:**
  - Espanya: 2.0TD de 3 franges o PVPC.
  - Països Baixos: fix, normaal/dal o dinàmic.
  - Un altre país: un preu únic.
  - Cada kWh es paga al preu de la seva hora. Els costos fixos van a part.
  - Els preus es poden omplir fent una foto o triant el PDF del contracte o de la factura. L'app els hi busca i els ensenya abans de posar-los. Provat amb un contracte holandès i amb factures d'Endesa i d'Estabanell.
- **Dues pinces:** la primera, la de tota la casa; la segona, una línia concreta, que es mesura per separat.

## Què cal

- **Un Shelly amb mesura d'energia de la família Gen2 o Gen3 que tingui els components EM1 i EM1Data**, com el Shelly EM Gen3. Els trifàsics (Pro 3EM) encara no hi són.
- **Android 8 o més nou.**
- **Per veure-ho des de fora de casa, la clau del núvol de Shelly.** És a l'app de Shelly: Configuració → Llar → Accés i permisos → Cloud Key.

## Com s'instal·la

1. Baixa l'APK de l'última versió a [Releases](../../releases).
2. Obre'l des de «Fitxers» i permet instal·lar apps d'aquesta font.
   - En un Samsung, si surt «S'ha bloquejat una aplicació desconeguda», cal desactivar Ajustaments → Seguretat i privadesa → Bloquejador automàtic.
3. A partir d'aquí, l'app avisa quan hi ha una versió nova i s'actualitza des de la Configuració.

## Les dades

- **No hi ha cap servidor.** Les dades del Shelly es guarden només al mòbil.
- **Les fotos de la placa i els contractes o factures es llegeixen dins del mòbil** i no s'envien enlloc.
- **A casa,** l'app llegeix el Shelly directament per la wifi.
- **Des de fora,** fa servir el núvol de Shelly amb la teva clau, que no surt del mòbil.
  - El núvol només dona el comptador d'energia, i per això el consum de fora és aproximat. Quan el mòbil torna a casa, l'app el substitueix per les dades de cada minut del Shelly, que en guarda una setmana.
  - Si el mòbil ha estat fora més d'una setmana, o per als dies d'abans d'instal·lar l'app, l'app pot omplir el que falta amb el consum de cada hora que guarda el núvol de Shelly. Es pot apagar a la Configuració.
- **Les úniques connexions a internet són aquestes:**
  - els preus de mercat: EnergyZero als Països Baixos i Red Eléctrica a Espanya;
  - les fitxes de les etiquetes energètiques i la cerca per model (la web de l'EPREL);
  - GitHub, per mirar si hi ha una versió nova.

## Limitacions

- **El repartiment per aparells és aproximat.** Amb una sola pinça per a tota la casa, els aparells de potència semblant es confonen, i els de menys de 30–50 W no es distingeixen bé.
- **Els avisos poden trigar entre 15 i 30 minuts quan l'app és tancada.** És el mínim que permet Android a les feines en segon pla.
- **Aquesta app no té res a veure amb Shelly (Allterco Robotics).**

## Com es compila

Cal el JDK 17 i l'SDK d'Android 35:

```bash
./gradlew assembleRelease
```

## Llengües

L'app és en català i en neerlandès. Si el mòbil és en neerlandès, surt en neerlandès, i si no, en català. A Android 13 o més nou, la llengua també es pot triar a la Configuració de l'app.

**Per traduir-la a una altra llengua:**
1. Copia [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) a `app/src/main/res/values-<codi>/strings.xml`, per exemple `values-es` o `values-en`.
2. Tradueix cada text. No canviïs els `name` ni els `%1$s`, `%2$d`… del text.
3. A `codi_idioma`, posa-hi el codi de la teva llengua (decideix com s'escriuen els números).
4. Obre una *pull request*. La llengua nova surt sola a l'app; no cal tocar cap més fitxer.

Si deixes textos sense traduir, l'app els ensenya en català.

## Privadesa i exactitud

- **Les dades queden al mòbil.** L'app no té cap servidor, no envia estadístiques i no demana cap compte. Només parla amb el Shelly (a casa, o pel núvol de Shelly amb la clau de l'usuari, que no surt del mòbil), amb l'EPREL quan s'afegeix un aparell, amb EnergyZero o Red Eléctrica per baixar els preus, i amb GitHub per mirar si hi ha una versió nova. Cap d'aquestes connexions porta dades del consum, només la petició.
- **El total és exacte; el repartiment, no.** El total de la casa és el que mesura el Shelly. El repartiment per aparells és una estimació: amb una pinça per a tota la casa, els aparells de potència semblant o que s'encenen alhora es poden confondre. Els euros surten de la tarifa que s'hi posa, no de la factura.
- L'app no està afiliada a Shelly (Allterco Robotics), ni a l'EPREL, EnergyZero o Red Eléctrica.

## Llicència

[GPL-3.0](LICENSE).

Fa servir aquestes llibreries i dades, totes amb llicència Apache-2.0:
- [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded), per llegir els QR;
- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android), per llegir les plaques;
- el model anglès de [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast).

---

## English

**Wakker** is an Android app that shows what each household appliance uses and what it costs, using a Shelly energy meter (Gen2/Gen3 with EM1/EM1Data, e.g. Shelly EM Gen3) and no server of its own.

- **Live view and history** (days, weeks, months and years, browsable back in time): Spain 2.0TD/PVPC, Netherlands fixed, normaal/dal or dynamic (EnergyZero), or a single price. Fixed costs are shown separately.
- **Appliance inventory**: a room-by-room questionnaire, the EU energy label QR (EPREL), a brand and model search on the EPREL website, a photo of the rating plate (read on the device with Tesseract), or just the appliance type. Appliances are always on, self-starting (fridge, heat pump, water heater) or switched on by the user; only the last ones need training. The fridge compressor is found automatically in the night-time data.
- **Contracted power**: how close the house is to it, with an alert above 90 % (immediately, with a persistent notification, or every 15 minutes).
- **Training**: tell the app when you switch an appliance on or off and it learns its power, live or afterwards ("the dishwasher ran yesterday from 5 to 9 pm": the app finds the jumps in that window and you tick the right ones).
- **Peak moments**: the busiest minute of each day and the 5 loads behind it, with when each switched on; unknown steps can be labelled to teach the app.
- **Unexpected-consumption alerts**: when consumption jumps (a threshold and a duration you choose) and the app cannot explain it, it asks what it is through a notification. A self-starting inverter appliance is learned as a power range from a single answer.
- **Fill in prices from a contract or bill** (photo or PDF), read on the device.
- **Privacy**: data stays on the phone. Remote access uses your own Shelly cloud key, which never leaves the device.
- **Away from home**, consumption is estimated from the cloud energy counter and replaced with the Shelly's per-minute data once the phone is back on the home Wi-Fi.

Install the APK from [Releases](../../releases). Licence: GPL-3.0. Not affiliated with Shelly (Allterco Robotics).

**Languages:** Catalan and Dutch. To add one, copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`, translate the texts (keep the names and the `%1$s` placeholders), set `codi_idioma` to your language code and open a pull request. Nothing else needs changing; untranslated texts fall back to Catalan.

## Crèdits

Feta per Jordi Relats, a Catalunya, per a una casa amb un Shelly; programada amb l'ajuda de Claude Code (Anthropic). Programari lliure, GPL-3.0.

D'on surten les dades:

- **Shelly** (Allterco Robotics): el comptador EM Gen3 i el seu núvol, d'on surten totes les lectures.
- **EPREL** (Comissió Europea): la base de dades europea de l'etiqueta energètica.
- **EnergyZero**: els preus dinàmics dels Països Baixos.
- **Red Eléctrica de España** (REData): el PVPC d'Espanya.

El que porta a dins:

- **Inter** (Rasmus Andersson), la lletra: SIL Open Font License 1.1 (`LLICENCIES/Inter-OFL.txt`).
- **Tesseract OCR** (Google, HP) amb **Tesseract4Android** (Adaptech) i el model `eng` de tessdata_fast: Apache 2.0.
- **ZXing Android Embedded** (JourneyApps) i **ZXing**: Apache 2.0.
- **Jetpack Compose, AndroidX i Material 3** amb les seves icones (Google): Apache 2.0.
- **Kotlin** i **kotlinx.coroutines** (JetBrains): Apache 2.0.

Els mateixos crèdits són a l'app, a Configuració → Sobre l'app.

## La lletra

L'app fa servir la lletra [Inter](https://rsms.me/inter/), de Rasmus Andersson, amb la llicència SIL Open Font License 1.1 (`LLICENCIES/Inter-OFL.txt`). Els fitxers de `app/src/main/res/font/` són un subconjunt (el llatí) dels pesos que l'app fa servir.
