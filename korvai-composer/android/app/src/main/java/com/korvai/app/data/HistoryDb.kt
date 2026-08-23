package com.korvai.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.korvai.engine.CellCharacter
import com.korvai.engine.CellFunction
import com.korvai.engine.Resolution
import com.korvai.engine.Segment
import com.korvai.engine.Weight
import org.json.JSONArray
import org.json.JSONObject

/* ---------- Room: generated korvai history (§3 "Korvai (generated output — persisted)") ---------- */

@Entity(tableName = "korvai_history")
data class KorvaiHistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val savedAt: Long,
    val templateId: String,
    val talaName: String,
    val nadaiName: String,
    val kalai: Int,
    val avartanas: Int,
    val totalMatras: Int,
    val source: String,
    val json: String,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: KorvaiHistoryEntity): Long

    @Query("SELECT * FROM korvai_history ORDER BY savedAt DESC LIMIT 60")
    suspend fun recent(): List<KorvaiHistoryEntity>

    @Query("DELETE FROM korvai_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM korvai_history")
    suspend fun count(): Int
}

@Database(entities = [KorvaiHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "korvai.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

/* ---------- Resolution ⇄ JSON (org.json — Android built-in) ---------- */

object ResolutionJson {

    /* Android's org.json.JSONArray is not Iterable — small helpers */
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    fun toJson(res: Resolution): String {
        val root = JSONObject()
        val cfg = JSONObject()
            .put("talaId", res.config.talaId).put("talaName", res.config.talaName)
            .put("jati", res.config.jati).put("nadaiId", res.config.nadaiId)
            .put("nadaiName", res.config.nadaiName).put("kalai", res.config.kalai)
            .put("eduppuAksharas", res.config.eduppuAksharas).put("avartanas", res.config.avartanas)
            .put("templateId", res.config.templateId).put("templateName", res.config.templateName)
            .put("seed", res.config.seed).put("maxDifficulty", res.config.maxDifficulty)
            .put("targetDifficulty", res.config.targetDifficulty).put("landingMode", res.config.landingMode)
        root.put("id", res.id).put("config", cfg)
        root.put("templateId", res.template.id)
        root.put("repetitions", res.repetitions).put("landing", res.landing)
        root.put("pad", res.pad).put("totalMatras", res.totalMatras)
        root.put("source", res.source).put("generatedAt", res.generatedAt)
        val segs = JSONArray()
        res.segments.forEach { seg ->
            val s = JSONObject().put("slotId", seg.slotId).put("label", seg.label).put("matras", seg.matras)
            val cells = JSONArray()
            seg.cells.forEach { cells.put(cellToJson(it)) }
            s.put("cells", cells)
            segs.put(s)
        }
        root.put("segments", segs)
        res.landingCell?.let { root.put("landingCell", cellToJson(it)) }
        return root.toString(2)
    }

    fun cellToJson(c: com.korvai.engine.RhythmicCell): JSONObject = JSONObject()
        .put("id", c.id).put("notation", c.notation)
        .put("syllables", JSONArray(c.syllables))
        .put("durations", JSONArray(c.durations))
        .put("matraCount", c.matraCount)
        .put("weights", JSONArray(c.weights.map { it.name }))
        .put("character", c.character.name)
        .put("function", c.function.name)
        .put("usableNadais", JSONArray(c.usableNadais))
        .put("difficulty", c.difficulty)
        .put("kaarvai", c.kaarvai)

    private fun cellFromJson(o: JSONObject, library: com.korvai.engine.Library): com.korvai.engine.RhythmicCell {
        val id = o.optString("id")
        library.cells.firstOrNull { it.id == id }?.let { return it }
        return com.korvai.engine.RhythmicCell(
            id = id,
            notation = o.getString("notation"),
            syllables = o.getJSONArray("syllables").strings(),
            durations = o.getJSONArray("durations").ints(),
            matraCount = o.getInt("matraCount"),
            weights = o.getJSONArray("weights").strings().map { Weight.valueOf(it) },
            character = CellCharacter.valueOf(o.getString("character")),
            function = CellFunction.valueOf(o.getString("function")),
            usableNadais = o.getJSONArray("usableNadais").strings(),
            difficulty = o.getInt("difficulty"),
            kaarvai = o.optBoolean("kaarvai", false),
        )
    }

    fun fromJson(text: String, library: com.korvai.engine.Library): Resolution? = try {
        val root = JSONObject(text)
        val cfg = root.getJSONObject("config")
        Resolution(
            id = root.getString("id"),
            config = com.korvai.engine.ResolutionConfig(
                talaId = cfg.getString("talaId"), talaName = cfg.getString("talaName"),
                jati = cfg.getString("jati"), nadaiId = cfg.getString("nadaiId"),
                nadaiName = cfg.getString("nadaiName"), kalai = cfg.getInt("kalai"),
                eduppuAksharas = cfg.getDouble("eduppuAksharas"), avartanas = cfg.getInt("avartanas"),
                templateId = cfg.getString("templateId"), templateName = cfg.getString("templateName"),
                seed = cfg.getInt("seed"), maxDifficulty = cfg.getInt("maxDifficulty"),
                targetDifficulty = cfg.getInt("targetDifficulty"), landingMode = cfg.getString("landingMode"),
            ),
            template = library.templates.first { it.id == root.getString("templateId") },
            segments = root.getJSONArray("segments").objects().map { seg ->
                Segment(
                    slotId = seg.getString("slotId"),
                    label = seg.getString("label"),
                    cells = seg.getJSONArray("cells").objects().map { cellFromJson(it, library) },
                    matras = seg.getInt("matras"),
                )
            },
            repetitions = root.getInt("repetitions"),
            landingCell = if (root.has("landingCell")) cellFromJson(root.getJSONObject("landingCell"), library) else null,
            landing = root.getInt("landing"),
            pad = root.getInt("pad"),
            totalMatras = root.getInt("totalMatras"),
            source = root.getString("source"),
            generatedAt = root.optString("generatedAt"),
        )
    } catch (_: Exception) {
        null
    }
}
