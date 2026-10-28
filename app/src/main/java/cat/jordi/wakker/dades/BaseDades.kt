package cat.jordi.wakker.dades

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Un minut del Shelly: l'energia del minut (Wh) i les potències màxima i mínima (W). */
data class Minut(val ts: Long, val wh: Double, val pmax: Double, val pmin: Double)

/**
 * Un punt de la gràfica: la potència mitjana del minut (W) del total i de la línia. [aprox]: no és del Shelly minut a
 * minut (el comptador del núvol repartit, o el consum per hores del núvol).
 */
data class Punt(val ts: Long, val wTotal: Double, val wLinia: Double, val aprox: Boolean = false)

/**
 * L'energia d'una hora (kWh) repartida: sempre encès, la línia de la segona pinça i la resta sense identificar.
 * [nuvol]: quanta ve de les dades per hores del núvol (maqueta 11), que l'Historial pinta en lila.
 */
data class Hora(val ts: Long, val sempre: Double, val linia: Double, val desconegut: Double, val nuvol: Double = 0.0) {
    val total get() = sempre + linia + desconegut
}

/**
 * Les dades queden al mòbil. Canal 0 = pinça 1 (tota la casa), canal 1 = pinça 2 (una línia).
 * La "resta" és el total menys la línia; d'aquí surten el "sempre encès" i el "sense identificar".
 * Els minuts aproximats (aprox = 1) es fan des de fora amb el comptador del núvol; els del núvol per hores
 * (aprox = 2, maqueta 11) omplen les absències llargues i el d'abans de l'app; els exactes, que es baixen
 * del Shelly a casa, els substitueixen.
 */
class BaseDades(context: Context) : SQLiteOpenHelper(context, "wakker.db", null, 9) {

    /**
     * Fins on hi ha el resum per hores (maqueta 16; s, una mitjanit; 0: encara no n'hi ha). D'aquí enrere, les sumes surten de
     * la taula «hores»: l'any sencer són 8.760 files en lloc de mig milió de minuts. Els minuts encara hi són fins que fan
     * 400 dies, per als moments punta, les gràfiques i exportar-los; després s'esborren.
     */
    @Volatile
    var resumFins = 0L

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE minuts (ts INTEGER NOT NULL, canal INTEGER NOT NULL, wh REAL NOT NULL,
               pmax REAL, pmin REAL, aprox INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (ts, canal)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE aparells (id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT NOT NULL,
               categoria TEXT NOT NULL, font TEXT NOT NULL, kwh_any REAL NOT NULL, espera_w REAL,
               marca TEXT, model TEXT, classe TEXT, eprel TEXT, creat INTEGER NOT NULL, potencia_w REAL, funciona TEXT,
               linia INTEGER NOT NULL DEFAULT 0)"""
        )
        creaPreus(db)
        creaEntrenament(db)
        creaRepetits(db)
        creaVegades(db)
        creaHores(db)
    }

    /**
     * El resum per hores (versió 9, maqueta 16), en Wh: el total de la casa, el sempre encès, la línia i la resta (amb el fons
     * d'aquells dies), el que ve del núvol per hores, quants minuts eren exactes, el que passa del fons als minuts aproximats,
     * la línia als aproximats, i si n'hi havia del comptador de fora (aprox = 1). [ts]: l'inici de l'hora local.
     */
    private fun creaHores(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE hores (ts INTEGER PRIMARY KEY, total REAL NOT NULL, sempre REAL NOT NULL, linia REAL NOT NULL,
               desconegut REAL NOT NULL, nuvol REAL NOT NULL, exactes INTEGER NOT NULL, sense_detall REAL NOT NULL,
               linia_aprox REAL NOT NULL, aprox1 INTEGER NOT NULL)"""
        )
    }

    /**
     * Les vegades (versió 8, maqueta 13): cada episodi de la resta de la casa (canal 0) i de la línia (canal 1), amb l'aparell
     * de qui és; i les etiquetes, el que l'usuari ha dit de cada estona. Les vegades es poden tornar a calcular; les etiquetes,
     * no.
     */
    private fun creaVegades(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE vegades (id INTEGER PRIMARY KEY AUTOINCREMENT, canal INTEGER NOT NULL, ts INTEGER NOT NULL,
               fi INTEGER NOT NULL, minuts INTEGER NOT NULL, w REAL NOT NULL, kwh REAL NOT NULL, variacio REAL NOT NULL,
               arrencada REAL NOT NULL, aparell INTEGER, com TEXT)"""
        )
        db.execSQL("CREATE INDEX vegades_ts ON vegades (ts)")
        db.execSQL(
            """CREATE TABLE etiquetes (id INTEGER PRIMARY KEY AUTOINCREMENT, canal INTEGER NOT NULL, des_de INTEGER NOT NULL,
               fins_a INTEGER NOT NULL, aparell INTEGER NOT NULL, no INTEGER NOT NULL DEFAULT 0)"""
        )
    }

    /** El que es repeteix sempre igual (versió 7, maqueta 12), per preguntar-ne un cop. */
    private fun creaRepetits(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE repetits (id INTEGER PRIMARY KEY AUTOINCREMENT, w REAL NOT NULL, wmin REAL NOT NULL, wmax REAL NOT NULL,
               minuts REAL NOT NULL, cada REAL NOT NULL, kwh REAL NOT NULL, variacio REAL, arrencada REAL, cops TEXT NOT NULL,
               estat TEXT NOT NULL, aparell INTEGER)"""
        )
    }

    /** L'entrenament (versió 3): les mostres de cada aparell i les preguntes sobre consums desconeguts. */
    private fun creaEntrenament(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE mostres (id INTEGER PRIMARY KEY AUTOINCREMENT, aparell INTEGER NOT NULL, ts INTEGER NOT NULL,
               dp REAL NOT NULL, durada INTEGER, font TEXT NOT NULL, wmin REAL, wmax REAL, variacio REAL, arrencada REAL)"""
        )
        db.execSQL(
            """CREATE TABLE preguntes (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, dp REAL NOT NULL,
               estat TEXT NOT NULL, aparell INTEGER)"""
        )
    }

    /** Els preus de mercat per hores (versió 2): EnergyZero als Països Baixos, el PVPC a Espanya. */
    private fun creaPreus(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE preus (ts INTEGER NOT NULL, pais TEXT NOT NULL, valor REAL NOT NULL, PRIMARY KEY (ts, pais)) WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) creaPreus(db)
        if (oldVersion < 3) creaEntrenament(db)
        // Versió 4: la potència de la placa de característiques
        if (oldVersion < 4) db.execSQL("ALTER TABLE aparells ADD COLUMN potencia_w REAL")
        // Versió 5: els minuts aproximats, calculats des de fora
        if (oldVersion < 5) db.execSQL("ALTER TABLE minuts ADD COLUMN aprox INTEGER NOT NULL DEFAULT 0")
        // Versió 6: com funciona cada aparell (buit: el del seu tipus) i l'interval de potència de les mostres
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE aparells ADD COLUMN funciona TEXT")
            // Les taules de la versió 3 ja tenen les columnes noves si s'acaben de crear en aquesta actualització
            if (oldVersion >= 3) {
                db.execSQL("ALTER TABLE mostres ADD COLUMN wmin REAL")
                db.execSQL("ALTER TABLE mostres ADD COLUMN wmax REAL")
            }
        }
        // Versió 7 (maqueta 12): els aparells de la línia de la pinça 2, la firma de les mostres i el que es repeteix
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE aparells ADD COLUMN linia INTEGER NOT NULL DEFAULT 0")
            if (oldVersion >= 3) {
                db.execSQL("ALTER TABLE mostres ADD COLUMN variacio REAL")
                db.execSQL("ALTER TABLE mostres ADD COLUMN arrencada REAL")
            }
            creaRepetits(db)
        }
        if (oldVersion < 9) creaHores(db)
        if (oldVersion < 8) {
            creaVegades(db)
            // El que ja s'havia ensenyat o respost abans de la 0.26 passa a ser etiquetes: el seu consum és d'aquell aparell.
            // Una estona passada, un moment punta i una pregunta, l'instant de la mostra (és dins de la vegada); el que es
            // repeteix, tota la durada; en directe, quan es va encendre
            db.execSQL(
                """INSERT INTO etiquetes (canal, des_de, fins_a, aparell, no)
                   SELECT 0, CASE WHEN font = 'directe' THEN ts - durada ELSE ts END,
                          CASE WHEN font = 'directe' THEN ts - durada WHEN font = 'repetit' AND durada IS NOT NULL THEN ts + durada - 1 ELSE ts END, aparell, 0
                   FROM mostres WHERE font IN ('passat', 'pic', 'pregunta', 'repetit') OR (font = 'directe' AND durada IS NOT NULL)"""
            )
        }
    }

    /** Els minuts exactes, baixats del Shelly: substitueixen els aproximats que hi hagués. */
    fun insereixMinuts(canal: Int, files: List<Minut>) = insereix(canal, files, aprox = false)

    /** Els minuts aproximats: no toquen els exactes que ja hi són. */
    fun insereixAproximats(canal: Int, files: List<Minut>) = insereix(canal, files, aprox = true)

    private fun insereix(canal: Int, files: List<Minut>, aprox: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql = db.compileStatement(
                "INSERT OR ${if (aprox) "IGNORE" else "REPLACE"} INTO minuts (ts, canal, wh, pmax, pmin, aprox) VALUES (?, ?, ?, ?, ?, ${if (aprox) 1 else 0})"
            )
            for (m in files) {
                sql.bindLong(1, m.ts)
                sql.bindLong(2, canal.toLong())
                sql.bindDouble(3, m.wh)
                sql.bindDouble(4, m.pmax)
                sql.bindDouble(5, m.pmin)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** L'últim minut exacte: d'aquí en endavant es baixa del Shelly (també on ara hi ha minuts aproximats). */
    fun darrerTs(canal: Int): Long? =
        readableDatabase.rawQuery("SELECT MAX(ts) FROM minuts WHERE canal = ? AND aprox = 0", arrayOf(canal.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    private val restaSql = """FROM minuts m0 LEFT JOIN minuts m1 ON m1.ts = m0.ts AND m1.canal = 1
        WHERE m0.canal = 0 AND m0.ts >= ? AND m0.ts < ?"""

    fun serie(desDe: Long, finsA: Long): List<Punt> =
        readableDatabase.rawQuery(
            "SELECT m0.ts, m0.wh * 60, IFNULL(m1.wh, 0) * 60, m0.aprox $restaSql ORDER BY m0.ts",
            arrayOf(desDe.toString(), finsA.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(Punt(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getInt(3) != 0)) }
        }

    /**
     * Els minuts exactes amb la potència màxima i la mínima, per buscar-hi episodis (maqueta 12): de la línia ([linia]), o
     * de la resta de la casa (el total menys la línia; el màxim i el mínim del total, menys la mitjana de la línia).
     */
    fun minutsW(linia: Boolean, desDe: Long, finsA: Long): List<MinutW> {
        val sql = if (linia) "SELECT ts, wh * 60, IFNULL(pmax, wh * 60), IFNULL(pmin, wh * 60) FROM minuts WHERE canal = 1 AND aprox = 0 AND ts >= ? AND ts < ? ORDER BY ts"
        else """SELECT m0.ts, (m0.wh - IFNULL(m1.wh, 0)) * 60, IFNULL(m0.pmax, m0.wh * 60) - IFNULL(m1.wh, 0) * 60,
                IFNULL(m0.pmin, m0.wh * 60) - IFNULL(m1.wh, 0) * 60 $restaSql AND m0.aprox = 0 ORDER BY m0.ts"""
        return readableDatabase.rawQuery(sql, arrayOf(desDe.toString(), finsA.toString())).use { c ->
            buildList { while (c.moveToNext()) add(MinutW(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getDouble(3))) }
        }
    }

    /** El pic instantani (W) d'un minut exacte del total, o null si no se sap. */
    fun pmax(ts: Long): Double? =
        readableDatabase.rawQuery("SELECT pmax FROM minuts WHERE canal = 0 AND ts = ? AND aprox = 0", arrayOf(ts.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getDouble(0).takeIf { w -> w > 0 } else null
        }

    // ---------- Les dades per hores del núvol (maqueta 11) ----------

    /** El primer minut exacte (del Shelly), o null. */
    fun primerExacte(): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM minuts WHERE canal = 0 AND aprox = 0", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    /** Els minuts exactes d'un canal entre dos instants: l'inici de cada minut i els Wh. */
    fun minutsExactes(canal: Int, desDe: Long, finsA: Long): Map<Long, Double> =
        readableDatabase.rawQuery(
            "SELECT ts, wh FROM minuts WHERE canal = ? AND aprox = 0 AND ts >= ? AND ts < ?", arrayOf(canal.toString(), desDe.toString(), finsA.toString()),
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getDouble(1)) } }

    /**
     * Els minuts fets amb el consum per hores del núvol: substitueixen els aproximats del comptador (que són pitjors)
     * d'aquelles hores, i mai no toquen els exactes; quan arriben els del Shelly, aquests els substitueixen.
     */
    fun insereixNuvol(canal: Int, minuts: List<Pair<Long, Double>>) {
        if (minuts.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Fora els aproximats i els del núvol d'abans (un dia recent es torna a demanar i pot haver canviat)
            db.delete("minuts", "canal = ? AND aprox <> 0 AND ts >= ? AND ts <= ?", arrayOf(canal.toString(), minuts.minOf { it.first }.toString(), minuts.maxOf { it.first }.toString()))
            val sql = db.compileStatement("INSERT OR IGNORE INTO minuts (ts, canal, wh, pmax, pmin, aprox) VALUES (?, ?, ?, ?, ?, 2)")
            for ((ts, wh) in minuts) {
                sql.bindLong(1, ts)
                sql.bindLong(2, canal.toLong())
                sql.bindDouble(3, wh)
                sql.bindDouble(4, wh * 60)
                sql.bindDouble(5, wh * 60)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Si s'apaga l'opció: fora els minuts del núvol per hores. */
    fun esborraNuvol() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("minuts", "aprox = 2", null)
            // Les hores del resum dels dies que encara tenen minuts es tornaran a fer sense el núvol ([Repositori.oblidaNuvol])
            db.execSQL("DELETE FROM hores WHERE nuvol > 0 AND ts >= IFNULL((SELECT MIN(ts) FROM minuts), 0)")
            // Les més velles ja no tenen minuts: se'n treu la part del núvol, i el que era exacte es queda
            db.execSQL(
                """UPDATE hores SET sempre = sempre * (total - nuvol) / total, linia = linia * (total - nuvol) / total,
                   desconegut = desconegut * (total - nuvol) / total, sense_detall = sense_detall * (total - nuvol) / total,
                   linia_aprox = linia_aprox * (total - nuvol) / total, total = total - nuvol, nuvol = 0
                   WHERE nuvol > 0 AND total > nuvol"""
            )
            db.execSQL("DELETE FROM hores WHERE nuvol > 0")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Què s'ha recuperat del núvol: quants dies (de la zona del mòbil), quants kWh i des de quan. */
    fun resumNuvol(): Triple<Int, Double, Long?> =
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT date(ts, 'unixepoch', 'localtime')), IFNULL(SUM(wh), 0), MIN(ts) FROM minuts WHERE canal = 0 AND aprox = 2", null,
        ).use { c ->
            if (c.moveToFirst()) Triple(c.getInt(0), c.getDouble(1) / 1000, if (c.isNull(2)) null else c.getLong(2)) else Triple(0, 0.0, null)
        }

    /** Si entre aquestes dues hores hi ha algun minut del núvol per hores. */
    fun teNuvol(desDe: Long, finsA: Long): Boolean =
        delResum("nuvol", desDe, finsA) > 0 || ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery(
                "SELECT 1 FROM minuts WHERE aprox = 2 AND ts >= ? AND ts < ? LIMIT 1", arrayOf(d.toString(), f.toString())
            ).use { if (it.moveToFirst()) 1.0 else 0.0 }
        } > 0

    /** Si entre aquestes dues hores hi ha algun minut aproximat. */
    fun teAproximats(desDe: Long, finsA: Long): Boolean =
        delResum("aprox1", desDe, finsA) > 0 || ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery(
                "SELECT 1 FROM minuts WHERE aprox = 1 AND ts >= ? AND ts < ? LIMIT 1", arrayOf(d.toString(), f.toString())
            ).use { if (it.moveToFirst()) 1.0 else 0.0 }
        } > 0

    /**
     * El consum de fons (Wh per minut): el percentil 5 de la resta. Null si encara no hi ha prou dades.
     * Amb [nomesExactes], sense els minuts aproximats, que són mitjanes i el farien més alt.
     */
    fun fons(desDe: Long, finsA: Long, nomesExactes: Boolean): Double? {
        val args = arrayOf(desDe.toString(), finsA.toString())
        val filtre = if (nomesExactes) " AND m0.aprox = 0" else ""
        val n = readableDatabase.rawQuery("SELECT COUNT(*) $restaSql$filtre", args).use { if (it.moveToFirst()) it.getLong(0) else 0 }
        if (n < 60) return null
        return readableDatabase.rawQuery(
            "SELECT MAX(m0.wh - IFNULL(m1.wh, 0), 0) AS resta $restaSql$filtre ORDER BY resta LIMIT 1 OFFSET ${n / 20}", args
        ).use { if (it.moveToFirst()) it.getDouble(0) else null }
    }

    /**
     * L'energia per hores locals (kWh), amb el fons donat en Wh per minut. [desfas]: quants segons passa l'hora
     * local de l'hora en punt UTC ([desfasHora]). El fons es converteix a número a la consulta: rawQuery passa els
     * arguments com a text, i MIN(número, text) de SQLite torna sempre el número.
     */
    fun perHores(desDe: Long, finsA: Long, fonsWh: Double, desfas: Long): List<Hora> {
        val b = resumFins
        val vells = if (desDe < b) readableDatabase.rawQuery(
            "SELECT ts, sempre, linia, desconegut, nuvol FROM hores WHERE ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(desDe.toString(), minOf(finsA, b).toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Hora(c.getLong(0), c.getDouble(1) / 1000, c.getDouble(2) / 1000, c.getDouble(3) / 1000, c.getDouble(4) / 1000)) }
        } else emptyList()
        return if (finsA > b) vells + perHoresMinuts(maxOf(desDe, b), finsA, fonsWh, desfas) else vells
    }

    private fun perHoresMinuts(desDe: Long, finsA: Long, fonsWh: Double, desfas: Long): List<Hora> {
        val f = fonsWh.toString()
        val d = desfas.toString()
        return readableDatabase.rawQuery(
            """SELECT ((m0.ts + CAST(? AS INTEGER)) / 3600) * 3600 - CAST(? AS INTEGER) AS h,
                 SUM(MIN(MAX(m0.wh - IFNULL(m1.wh, 0), 0), CAST(? AS REAL))),
                 SUM(IFNULL(m1.wh, 0)),
                 SUM(MAX(m0.wh - IFNULL(m1.wh, 0) - CAST(? AS REAL), 0)),
                 SUM(CASE WHEN m0.aprox = 2 THEN m0.wh ELSE 0 END)
               $restaSql GROUP BY h ORDER BY h""",
            arrayOf(d, d, f, f, desDe.toString(), finsA.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Hora(c.getLong(0), c.getDouble(1) / 1000, c.getDouble(2) / 1000, c.getDouble(3) / 1000, c.getDouble(4) / 1000))
            }
        }
    }

    fun desaPreus(pais: Pais, preus: List<Pair<Long, Double>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql = db.compileStatement("INSERT OR REPLACE INTO preus (ts, pais, valor) VALUES (?, ?, ?)")
            for ((ts, valor) in preus) {
                sql.bindLong(1, ts)
                sql.bindString(2, pais.name)
                sql.bindDouble(3, valor)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun preus(pais: Pais, desDe: Long, finsA: Long): Map<Long, Double> =
        readableDatabase.rawQuery(
            "SELECT ts, valor FROM preus WHERE pais = ? AND ts >= ? AND ts < ?", arrayOf(pais.name, desDe.toString(), finsA.toString())
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getDouble(1)) } }

    /** L'energia de tota la casa (kWh) entre dos instants. */
    fun kwhEntre(desDe: Long, finsA: Long): Double =
        delResum("total", desDe, finsA) / 1000 + ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery(
                "SELECT IFNULL(SUM(wh), 0) FROM minuts WHERE canal = 0 AND ts >= ? AND ts < ?", arrayOf(d.toString(), f.toString()),
            ).use { if (it.moveToFirst()) it.getDouble(0) / 1000 else 0.0 }
        }

    /** La suma d'una columna del resum per hores entre dos instants (la part d'abans de [resumFins]). */
    private fun delResum(columna: String, desDe: Long, finsA: Long): Double {
        if (desDe >= resumFins) return 0.0
        return readableDatabase.rawQuery(
            "SELECT IFNULL(SUM($columna), 0) FROM hores WHERE ts >= ? AND ts < ?", arrayOf(desDe.toString(), minOf(finsA, resumFins).toString()),
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    /** El que surt dels minuts: la part des de [resumFins]. */
    private fun ambMinuts(desDe: Long, finsA: Long, consulta: (Long, Long) -> Double): Double {
        val d = maxOf(desDe, resumFins)
        return if (finsA > d) consulta(d, finsA) else 0.0
    }

    /**
     * Fa el resum per hores d'un dia (o d'una estona) amb el fons d'aquells dies ([fonsWh], Wh per minut). Si no hi ha cap
     * minut (ja s'han esborrat), no hi toca: el resum que hi hagi és l'únic que en queda.
     */
    fun resumeix(desDe: Long, finsA: Long, fonsWh: Double, desfas: Long) {
        val args = arrayOf(desDe.toString(), finsA.toString())
        val n = readableDatabase.rawQuery("SELECT COUNT(*) FROM minuts WHERE canal = 0 AND ts >= ? AND ts < ?", args).use { if (it.moveToFirst()) it.getLong(0) else 0 }
        if (n == 0L) return
        val f = fonsWh.toString()
        val d = desfas.toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("hores", "ts >= ? AND ts < ?", args)
            db.execSQL(
                """INSERT OR REPLACE INTO hores (ts, total, sempre, linia, desconegut, nuvol, exactes, sense_detall, linia_aprox, aprox1)
                   SELECT ((m0.ts + CAST(? AS INTEGER)) / 3600) * 3600 - CAST(? AS INTEGER) AS h,
                     SUM(m0.wh),
                     SUM(MIN(MAX(m0.wh - IFNULL(m1.wh, 0), 0), CAST(? AS REAL))),
                     SUM(IFNULL(m1.wh, 0)),
                     SUM(MAX(m0.wh - IFNULL(m1.wh, 0) - CAST(? AS REAL), 0)),
                     SUM(CASE WHEN m0.aprox = 2 THEN m0.wh ELSE 0 END),
                     SUM(CASE WHEN m0.aprox = 0 THEN 1 ELSE 0 END),
                     SUM(CASE WHEN m0.aprox <> 0 THEN MAX(m0.wh - IFNULL(m1.wh, 0) - CAST(? AS REAL), 0) ELSE 0 END),
                     SUM(CASE WHEN IFNULL(m1.aprox, 0) <> 0 THEN m1.wh ELSE 0 END),
                     MAX(m0.aprox = 1)
                   $restaSql GROUP BY h""",
                arrayOf(d, d, f, f, f, desDe.toString(), finsA.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Fora els minuts d'abans de [abans]: ja són al resum per hores. Les vegades i les etiquetes es queden. */
    fun esborraMinuts(abans: Long) {
        if (abans <= 0) return
        writableDatabase.delete("minuts", "ts < ?", arrayOf(abans.toString()))
    }

    /** L'últim minut que hi ha, exacte o no. */
    fun darrerMinut(): Long? =
        readableDatabase.rawQuery("SELECT MAX(ts) FROM minuts", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    /** El primer minut que encara hi és (d'abans, només hi ha el resum). */
    fun primerMinut(): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM minuts", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    /** El primer minut de totes les dades (també del resum per hores, si els minuts ja s'han esborrat). */
    fun primerTs(): Long? = primer("SELECT MIN(ts) FROM minuts", "SELECT MIN(ts) FROM hores")

    /**
     * El primer minut en què la pinça 2 mesura alguna cosa (30 W o més): des de quan hi ha la pinça. El Shelly desa el canal 2
     * encara que no hi hagi pinça, amb zeros (a Guíxols, fins al 25/10).
     */
    fun primerLinia(): Long? = primer("SELECT MIN(ts) FROM minuts WHERE canal = 1 AND wh >= 0.5", "SELECT MIN(ts) FROM hores WHERE linia >= 30")

    private fun primer(deMinuts: String, deHores: String): Long? =
        listOf(deMinuts, deHores).mapNotNull { sql ->
            readableDatabase.rawQuery(sql, null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
        }.minOrNull()

    fun aparells(): List<Aparell> =
        readableDatabase.rawQuery(
            "SELECT id, nom, categoria, font, kwh_any, espera_w, marca, model, classe, eprel, potencia_w, funciona, linia FROM aparells ORDER BY kwh_any DESC", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val categoria = Categoria.de(c.getString(2))
                    add(
                        Aparell(
                            id = c.getLong(0), nom = c.getString(1), categoria = categoria,
                            font = Font.de(c.getString(3)), kwhAny = c.getDouble(4),
                            esperaW = if (c.isNull(5)) null else c.getDouble(5),
                            marca = c.getString(6) ?: "", model = c.getString(7) ?: "",
                            classe = c.getString(8) ?: "", eprel = c.getString(9) ?: "",
                            potenciaW = if (c.isNull(10)) null else c.getDouble(10),
                            funciona = Funcionament.de(c.getString(11), categoria), linia = c.getInt(12) != 0,
                        )
                    )
                }
            }
        }

    /** Desa un aparell; torna el seu id (el nou, si se n'ha creat un). */
    fun desaAparell(a: Aparell): Long {
        val v = ContentValues().apply {
            put("nom", a.nom); put("categoria", a.categoria.name); put("font", a.font.name)
            put("kwh_any", a.kwhAny); put("espera_w", a.esperaW); put("marca", a.marca)
            put("model", a.model); put("classe", a.classe); put("eprel", a.eprel); put("potencia_w", a.potenciaW)
            put("funciona", a.funciona.name); put("linia", if (a.linia) 1 else 0)
        }
        return if (a.id == 0L) {
            v.put("creat", System.currentTimeMillis() / 1000)
            writableDatabase.insert("aparells", null, v)
        } else {
            writableDatabase.update("aparells", v, "id = ?", arrayOf(a.id.toString()))
            a.id
        }
    }

    fun esborraAparell(id: Long) {
        writableDatabase.delete("aparells", "id = ?", arrayOf(id.toString()))
        esborraMostres(id)
    }

    fun desaMostra(
        aparellId: Long, ts: Long, dp: Double, durada: Long?, font: String,
        interval: Pair<Double, Double>? = null, variacio: Double? = null, arrencada: Double? = null,
    ): Long =
        writableDatabase.insert("mostres", null, ContentValues().apply {
            put("aparell", aparellId); put("ts", ts); put("dp", dp); put("durada", durada); put("font", font)
            put("wmin", interval?.first); put("wmax", interval?.second); put("variacio", variacio); put("arrencada", arrencada)
        })

    /** Si algun aparell ja té una mostra d'aquest instant i d'aquesta font. */
    fun hiHaMostra(ts: Long, font: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM mostres WHERE ts = ? AND font = ? LIMIT 1", arrayOf(ts.toString(), font)).use { it.moveToFirst() }

    fun teMostra(aparellId: Long, ts: Long, font: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM mostres WHERE aparell = ? AND ts = ? AND font = ? LIMIT 1", arrayOf(aparellId.toString(), ts.toString(), font),
        ).use { it.moveToFirst() }

    fun mostres(): List<Mostra> =
        readableDatabase.rawQuery("SELECT id, aparell, ts, dp, durada, wmin, wmax, font, variacio, arrencada FROM mostres ORDER BY ts", null).use { c ->
            fun real(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
            buildList {
                while (c.moveToNext()) add(
                    Mostra(
                        c.getLong(0), c.getLong(1), c.getLong(2), c.getDouble(3), if (c.isNull(4)) null else c.getLong(4),
                        real(5), real(6), c.getString(7) ?: "", real(8), real(9),
                    )
                )
            }
        }

    /** L'interval de potència d'una mostra, quan ja hi ha les dades de l'episodi. */
    fun desaInterval(mostraId: Long, wMin: Double, wMax: Double) {
        writableDatabase.update("mostres", ContentValues().apply { put("wmin", wMin); put("wmax", wMax) }, "id = ?", arrayOf(mostraId.toString()))
    }

    fun esborraMostres(aparellId: Long) {
        writableDatabase.delete("mostres", "aparell = ?", arrayOf(aparellId.toString()))
    }

    /** Les mostres d'un aparell d'una font (les apreses de la línia, en treure'l de la línia). */
    fun esborraMostres(aparellId: Long, font: String) {
        writableDatabase.delete("mostres", "aparell = ? AND font = ?", arrayOf(aparellId.toString(), font))
    }

    fun creaPregunta(ts: Long, dp: Double): Long =
        writableDatabase.insert("preguntes", null, ContentValues().apply {
            put("ts", ts); put("dp", dp); put("estat", EstatPregunta.PENDENT.name)
        })

    fun preguntes(estat: EstatPregunta): List<Pregunta> =
        readableDatabase.rawQuery("SELECT id, ts, dp, estat, aparell FROM preguntes WHERE estat = ? ORDER BY ts DESC", arrayOf(estat.name)).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Pregunta(c.getLong(0), c.getLong(1), c.getDouble(2), EstatPregunta.valueOf(c.getString(3)), if (c.isNull(4)) null else c.getLong(4))
                )
            }
        }

    fun pregunta(id: Long): Pregunta? =
        readableDatabase.rawQuery("SELECT id, ts, dp, estat, aparell FROM preguntes WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Pregunta(c.getLong(0), c.getLong(1), c.getDouble(2), EstatPregunta.valueOf(c.getString(3)), if (c.isNull(4)) null else c.getLong(4)) else null
        }

    fun respon(id: Long, estat: EstatPregunta, aparellId: Long?) {
        writableDatabase.update("preguntes", ContentValues().apply { put("estat", estat.name); put("aparell", aparellId) }, "id = ?", arrayOf(id.toString()))
    }

    // ---------- El que es repeteix (maqueta 12) ----------

    /**
     * Els pendents es tornen a buscar cada vegada. Un que es torna a trobar s'actualitza i es queda amb el seu id (una
     * targeta ja dibuixada encara el pot respondre); els que ja no hi són, fora; els nous, a dins.
     */
    fun substitueixRepetits(nous: List<Repetit>) {
        val abans = repetits(EstatPregunta.PENDENT).toMutableList()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in nous) {
                val v = ContentValues().apply {
                    put("w", r.w); put("wmin", r.wMin); put("wmax", r.wMax); put("minuts", r.minuts); put("cada", r.cadaMinuts)
                    put("kwh", r.kwh); put("variacio", r.variacio); put("arrencada", r.arrencada)
                    put("cops", r.cops.joinToString(";") { (ts, w, m) -> "$ts,${w.toInt()},$m" })
                    put("estat", r.estat.name)
                }
                val mateix = abans.firstOrNull { Episodis.mateix(it, r.w, r.minuts) }
                if (mateix != null) {
                    abans -= mateix
                    db.update("repetits", v, "id = ?", arrayOf(mateix.id.toString()))
                } else {
                    db.insert("repetits", null, v)
                }
            }
            for (r in abans) db.delete("repetits", "id = ?", arrayOf(r.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun repetits(estat: EstatPregunta? = null): List<Repetit> =
        readableDatabase.rawQuery(
            "SELECT id, w, wmin, wmax, minuts, cada, kwh, variacio, arrencada, cops, estat, aparell FROM repetits" +
                (if (estat != null) " WHERE estat = ?" else "") + " ORDER BY kwh DESC",
            estat?.let { arrayOf(it.name) },
        ).use { c ->
            fun real(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
            buildList {
                while (c.moveToNext()) {
                    val cops = c.getString(9).split(";").mapNotNull { s ->
                        s.split(",").takeIf { it.size == 3 }?.let { (ts, w, m) -> Triple(ts.toLong(), w.toDouble(), m.toInt()) }
                    }
                    add(
                        Repetit(
                            c.getLong(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getDouble(6),
                            real(7), real(8), cops, EstatPregunta.valueOf(c.getString(10)), if (c.isNull(11)) null else c.getLong(11),
                        )
                    )
                }
            }
        }

    fun responRepetit(id: Long, estat: EstatPregunta, aparellId: Long?) {
        writableDatabase.update("repetits", ContentValues().apply { put("estat", estat.name); put("aparell", aparellId) }, "id = ?", arrayOf(id.toString()))
    }

    // ---------- Les vegades i les etiquetes (maqueta 13) ----------

    /** Fora les vegades des de [desDe] i a dins les noves (ja amb l'aparell de qui són). */
    fun substitueixVegades(desDe: Long, noves: List<Vegada>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("vegades", "ts >= ?", arrayOf(desDe.toString()))
            val sql = db.compileStatement(
                "INSERT INTO vegades (canal, ts, fi, minuts, w, kwh, variacio, arrencada, aparell, com) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )
            for (v in noves) {
                sql.bindLong(1, v.canal.toLong()); sql.bindLong(2, v.ts); sql.bindLong(3, v.fi); sql.bindLong(4, v.minuts.toLong())
                sql.bindDouble(5, v.w); sql.bindDouble(6, v.kwh); sql.bindDouble(7, v.variacio); sql.bindDouble(8, v.arrencada)
                if (v.aparellId != null) sql.bindLong(9, v.aparellId) else sql.bindNull(9)
                if (v.com != null) sql.bindString(10, v.com.name) else sql.bindNull(10)
                sql.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Les vegades que toquen l'estona entre dos instants, també les que hi comencen abans o s'hi acaben després ([aparellId]:
     * només les d'aquest aparell), de la més antiga a la més nova.
     */
    fun vegades(desDe: Long, finsA: Long, aparellId: Long? = null): List<Vegada> =
        readableDatabase.rawQuery(
            "SELECT id, canal, ts, fi, minuts, w, kwh, variacio, arrencada, aparell, com FROM vegades WHERE fi + 60 > CAST(? AS INTEGER) AND ts < CAST(? AS INTEGER)" +
                (if (aparellId != null) " AND aparell = ?" else "") + " ORDER BY ts",
            listOfNotNull(desDe.toString(), finsA.toString(), aparellId?.toString()).toTypedArray(),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Vegada(
                        c.getLong(0), c.getInt(1), c.getLong(2), c.getLong(3), c.getInt(4), c.getDouble(5), c.getDouble(6),
                        c.getDouble(7), c.getDouble(8), if (c.isNull(9)) null else c.getLong(9), if (c.isNull(10)) null else Com.valueOf(c.getString(10)),
                    )
                )
            }
        }

    /** Els aparells que tenen alguna mostra. */
    fun aparellsAmbMostres(): Set<Long> =
        readableDatabase.rawQuery("SELECT DISTINCT aparell FROM mostres", null).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    /** L'energia de la línia (kWh) als minuts aproximats (des de fora, o del núvol per hores) d'entre dos instants. */
    fun liniaAproximada(desDe: Long, finsA: Long): Double =
        delResum("linia_aprox", desDe, finsA) / 1000 + ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery(
                "SELECT IFNULL(SUM(wh), 0) FROM minuts WHERE canal = 1 AND aprox <> 0 AND ts >= ? AND ts < ?", arrayOf(d.toString(), f.toString()),
            ).use { if (it.moveToFirst()) it.getDouble(0) / 1000 else 0.0 }
        }

    fun hiHaVegades(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM vegades LIMIT 1", null).use { it.moveToFirst() }

    /** Les últimes [n] vegades d'un aparell, de la més nova a la més antiga. */
    fun ultimesVegades(aparellId: Long, n: Int): List<Vegada> =
        readableDatabase.rawQuery(
            "SELECT id, canal, ts, fi, minuts, w, kwh, variacio, arrencada, aparell, com FROM vegades WHERE aparell = ? ORDER BY ts DESC LIMIT $n",
            arrayOf(aparellId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Vegada(
                        c.getLong(0), c.getInt(1), c.getLong(2), c.getLong(3), c.getInt(4), c.getDouble(5), c.getDouble(6),
                        c.getDouble(7), c.getDouble(8), c.getLong(9), if (c.isNull(10)) null else Com.valueOf(c.getString(10)),
                    )
                )
            }
        }

    /** L'última vegada que comença abans de [ts] i encara durava (per tornar a mirar una vegada que no s'havia acabat). */
    fun vegadaOberta(desDe: Long): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM vegades WHERE fi >= ?", arrayOf(desDe.toString())).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    /** Canvia de qui són unes vegades: (id, aparell, com). */
    fun reassigna(canvis: List<Triple<Long, Long?, Com?>>) {
        if (canvis.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sql = db.compileStatement("UPDATE vegades SET aparell = ?, com = ? WHERE id = ?")
            for ((id, aparell, com) in canvis) {
                if (aparell != null) sql.bindLong(1, aparell) else sql.bindNull(1)
                if (com != null) sql.bindString(2, com.name) else sql.bindNull(2)
                sql.bindLong(3, id)
                sql.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Desa el que l'usuari ha dit d'una estona; torna l'id, per poder-ho desfer (maqueta 17). */
    fun desaEtiqueta(e: Etiqueta): Long =
        writableDatabase.insert("etiquetes", null, ContentValues().apply {
            put("canal", e.canal); put("des_de", e.desDe); put("fins_a", e.finsA); put("aparell", e.aparellId); put("no", if (e.no) 1 else 0)
        })

    fun etiquetes(): List<Etiqueta> =
        readableDatabase.rawQuery("SELECT canal, des_de, fins_a, aparell, no, id FROM etiquetes", null).use { c ->
            buildList { while (c.moveToNext()) add(Etiqueta(c.getInt(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getInt(4) != 0, c.getLong(5))) }
        }

    /** Fora unes etiquetes i unes mostres concretes (en desfer una resposta, o treure'n una de la fitxa). */
    fun esborra(etiquetes: List<Long>, mostres: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            etiquetes.forEach { db.delete("etiquetes", "id = ?", arrayOf(it.toString())) }
            mostres.forEach { db.delete("mostres", "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Fora el que l'usuari havia dit d'un aparell (en esborrar-lo o tornar-lo a entrenar). */
    fun esborraEtiquetes(aparellId: Long) {
        writableDatabase.delete("etiquetes", "aparell = ?", arrayOf(aparellId.toString()))
    }

    /** Quants minuts exactes de tota la casa hi ha entre dos instants. */
    fun minutsExactes(desDe: Long, finsA: Long): Long =
        (delResum("exactes", desDe, finsA) + ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery("SELECT COUNT(*) FROM minuts WHERE canal = 0 AND aprox = 0 AND ts >= ? AND ts < ?", arrayOf(d.toString(), f.toString())).use {
                if (it.moveToFirst()) it.getLong(0).toDouble() else 0.0
            }
        }).toLong()

    /**
     * El que passa del fons als minuts aproximats (des de fora, o del núvol per hores) d'entre dos instants (kWh): sense el
     * detall de cada minut, no se sap de qui és. El fons, en Wh per minut, es converteix a número (vegeu [perHores]).
     */
    fun senseDetall(desDe: Long, finsA: Long, fonsWh: Double): Double =
        delResum("sense_detall", desDe, finsA) / 1000 + ambMinuts(desDe, finsA) { d, f ->
            readableDatabase.rawQuery(
                "SELECT IFNULL(SUM(MAX(m0.wh - IFNULL(m1.wh, 0) - CAST(? AS REAL), 0)), 0) $restaSql AND m0.aprox <> 0",
                arrayOf(fonsWh.toString(), d.toString(), f.toString()),
            ).use { if (it.moveToFirst()) it.getDouble(0) / 1000 else 0.0 }
        }
}
