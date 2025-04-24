package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.http.ContentDisposition.Companion.File
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import io.ktor.http.content.*
import java.io.File
import java.util.UUID
import com.example.Photos

@Serializable
data class Treasure(
    val id: Int? = null,
    val name: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val photoPath: String? = null
)

@Serializable
data class FoundRequest(
    val userId: Int,
    val treasureId: Int

)

fun Route.treasureRoutes() {

    // ------------------------
    // Skarby: GET i POST
    // ------------------------
    route("/api/treasures") {

        // Zwraca listę skarbów
        get {
            val treasures = transaction {
                Treasures.selectAll().map {
                    Treasure(
                        id = it[Treasures.id],
                        name = it[Treasures.name],
                        description = it[Treasures.description],
                        lat = it[Treasures.lat],
                        lng = it[Treasures.lng]
                    )
                }
            }
            call.respond(treasures)
        }

        // Dodaje nowy skarb
        post {
            val treasure = call.receive<Treasure>()
            val id = transaction {
                Treasures.insert {
                    it[name] = treasure.name
                    it[description] = treasure.description
                    it[lat] = treasure.lat
                    it[lng] = treasure.lng
                } get Treasures.id
            }

            call.respond(HttpStatusCode.Created, treasure.copy(id = id))
        }
    }

    // ------------------------
    // Oznaczenie skarbu jako znalezionego
    // ------------------------
//    post("/api/found") {
//        val request = call.receive<FoundRequest>()
//        println("✅ Odebrano zgłoszenie znalezienia skarbu: userId=${request.userId}, treasureId=${request.treasureId}")
//
//        transaction {
//            val inserted = FoundTreasures.insertIgnore {
//                it[userId] = request.userId
//                it[treasureId] = request.treasureId
//            }
//            println("💾 Zapisano do bazy: $inserted")
//        }
//
//        call.respond(HttpStatusCode.Created)
//    }
    post("/api/found") {
        try {
            val request = call.receive<FoundRequest>()
            println("📥 Otrzymano: userId=${request.userId}, treasureId=${request.treasureId}")


            val alreadyExists = transaction {
                println("petla")

                FoundTreasures.select {
                    (FoundTreasures.userId eq request.userId) and
                            (FoundTreasures.treasureId eq request.treasureId)
                }.count() > 0
            }
            println("po")

            if (alreadyExists) {
                println("znaleziony")

                call.respond(HttpStatusCode.Conflict, "Skarb już został znaleziony.")
                return@post
            }

            println("transaction")

            transaction {
                FoundTreasures.insertIgnore {
                    it[userId] = request.userId
                    it[treasureId] = request.treasureId
                }
            }

            println("✅ Zapisano do bazy danych")
            call.respond(HttpStatusCode.Created)

        } catch (e: Exception) {
            println("❌ Błąd podczas zapisu: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Błąd serwera: ${e.message}")
        }
    }

// ------------------------
// Zwraca listę znalezionych skarbów danego użytkownika
// ------------------------
    get("/api/found/treasures") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, "Brakuje userId")
            return@get
        }

        val treasures = transaction {
            (FoundTreasures innerJoin Treasures)
                .select { FoundTreasures.userId eq userId }
                .map {
                    Treasure(
                        id = it[Treasures.id],
                        name = it[Treasures.name],
                        description = it[Treasures.description],
                        lat = it[Treasures.lat],
                        lng = it[Treasures.lng],
                        photoPath = it[FoundTreasures.photoPath]
                    )
                }
        }

        call.respond(treasures)
    }
    get("/api/clear") {
        transaction {
            FoundTreasures.deleteAll()
            Treasures.deleteAll()
            Users.deleteAll()
        }
        call.respond(HttpStatusCode.OK, "Baza wyczyszczona!")
    }
    post("/api/found/photo") {
        val multipart = call.receiveMultipart()
        var userId: Int? = null
        var treasureId: Int? = null
        var filename: String? = null

        var fileBytes: ByteArray? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "userId" -> userId = part.value.toIntOrNull()
                        "treasureId" -> treasureId = part.value.toIntOrNull()
                    }
                }
                is PartData.FileItem -> {
                    filename = part.originalFileName
                    fileBytes = part.streamProvider().readBytes()
                }
                else -> Unit
            }
            part.dispose()
        }

        if (userId == null || treasureId == null || fileBytes == null) {
            call.respond(HttpStatusCode.BadRequest, "Brakuje danych.")
            return@post
        }

        val filePath = "photos/user_${userId}_treasure_${treasureId}.jpg"
        val file = java.io.File("uploads/$filePath").apply {
            parentFile.mkdirs()
            writeBytes(fileBytes!!)
        }

        transaction {
            FoundTreasures.update({
                (FoundTreasures.userId eq userId!!) and
                        (FoundTreasures.treasureId eq treasureId!!)
            }) {
                it[photoPath] = filePath
            }
        }

        call.respond(HttpStatusCode.OK, "Zdjęcie zapisane.")
    }
    post("/api/upload") {
        val multipart = call.receiveMultipart()
        var userId: Int? = null
        var treasureId: Int? = null
        var fileName: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "userId" -> userId = part.value.toIntOrNull()
                        "treasureId" -> treasureId = part.value.toIntOrNull()
                    }
                }
                is PartData.FileItem -> {
                    fileName = part.originalFileName
                    val fileBytes = part.streamProvider().readBytes()
                    val file = File("uploads/${UUID.randomUUID()}_${fileName}")
                    file.parentFile.mkdirs()
                    file.writeBytes(fileBytes)
                    println("📸 Zapisano plik: ${file.absolutePath}")
                }
                else -> {}
            }
            part.dispose()
        }

        if (userId != null && treasureId != null && fileName != null) {
            call.respond(HttpStatusCode.OK, "Zdjęcie przesłane!")
        } else {
            call.respond(HttpStatusCode.BadRequest, "Brakuje danych")
        }
    }
    get("/api/found/withPhoto") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
        val ids = transaction {
            Photos.select { Photos.userId eq userId!! }
                .map { it[Photos.treasureId] }
        }
        call.respond(ids)
    }
//    post("/api/photo") {
//        val multipart = call.receiveMultipart()
//        var userId: Int? = null
//        var treasureId: Int? = null
//        var fileName: String? = null
//
//        multipart.forEachPart { part ->
//            when (part) {
//                is PartData.FormItem -> {
//                    when (part.name) {
//                        "userId" -> userId = part.value.toIntOrNull()
//                        "treasureId" -> treasureId = part.value.toIntOrNull()
//                    }
//                }
//                is PartData.FileItem -> {
//                    fileName = part.originalFileName as String
//                    val fileBytes = part.streamProvider().readBytes()
//                    val file = File("photos/${userId}_${treasureId}_$fileName")
//                    file.parentFile?.mkdirs()
//                    file.writeBytes(fileBytes)
//                }
//                else -> Unit
//            }
//            part.dispose()
//        }
//
//        if (userId != null && treasureId != null && fileName != null) {
//            transaction {
//                Photos.insert {
//                    it[Photos.userId] = userId!!
//                    it[Photos.treasureId] = treasureId!!
//                    it[Photos.filePath] = "photos/${userId}_${treasureId}_$fileName"
//                }
//            }
//            call.respond(HttpStatusCode.OK)
//        } else {
//            call.respond(HttpStatusCode.BadRequest, "Missing data")
//        }
//    }

//    post("/api/photo") {
//        try {
//            val multipart = call.receiveMultipart()
//            var userId: Int? = null
//            var treasureId: Int? = null
//            var fileName: String? = null
//            var savedFilePath: String? = null
//
//            multipart.forEachPart { part ->
//                when (part) {
//                    is PartData.FormItem -> {
//                        when (part.name) {
//                            "userId" -> {
//                                userId = part.value.toIntOrNull()
//                                println("📨 userId: $userId")
//                            }
//                            "treasureId" -> {
//                                treasureId = part.value.toIntOrNull()
//                                println("📨 treasureId: $treasureId")
//                            }
//                        }
//                    }
//
//                    is PartData.FileItem -> {
//                        fileName = part.originalFileName ?: "photo.jpg"
//                        val fileBytes = part.streamProvider().readBytes()
//                        savedFilePath = "photos/${userId}_${treasureId}_$fileName"
//                        val file = File(savedFilePath)
//
//                        file.parentFile?.mkdirs()
//                        file.writeBytes(fileBytes)
//
//                        println("💾 Zapisano zdjęcie: ${file.absolutePath}")
//                        println("📏 Rozmiar pliku: ${fileBytes.size} bajtów")
//                    }
//
//                    else -> Unit
//                }
//                part.dispose()
//            }
//
//            if (userId != null && treasureId != null && savedFilePath != null) {
//                transaction {
//                    Photos.insert {
//                        it[Photos.userId] = userId!!
//                        it[Photos.treasureId] = treasureId!!
//                        it[Photos.filePath] = savedFilePath!!
//                    }
//                }
//                println("✅ Zapisano do bazy danych: userId=$userId, treasureId=$treasureId")
//                call.respond(HttpStatusCode.OK)
//            } else {
//                println("❌ Brak wymaganych danych")
//                call.respond(HttpStatusCode.BadRequest, "Missing data")
//            }
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            call.respond(HttpStatusCode.InternalServerError, "❌ Błąd serwera: ${e.localizedMessage}")
//        }
//    }
//?????????????????????????
//    post("/api/photo") {
//        val multipart = call.receiveMultipart()
//        var userId: Int? = null
//        var treasureId: Int? = null
//        var fileName: String? = null
//
//        println("📥 Otrzymano multipart...")
//
//        multipart.forEachPart { part ->
//            when (part) {
//                is PartData.FormItem -> {
//                    println("🧾 FormItem: ${part.name} = ${part.value}")
//                    when (part.name) {
//                        "userId" -> userId = part.value.toIntOrNull()
//                        "treasureId" -> treasureId = part.value.toIntOrNull()
//                    }
//                }
//                is PartData.FileItem -> {
//                    fileName = part.originalFileName ?: "unnamed.jpg"
//                    println("📸 Plik: $fileName")
//
//                    val fileBytes = part.streamProvider().readBytes()
//
//                    val safeFileName = "${userId ?: "NOUSER"}_${treasureId ?: "NOTREASURE"}_$fileName"
//                    val file = File("photos/$safeFileName")
//                    file.parentFile?.mkdirs()
//                    file.writeBytes(fileBytes)
//
//                    println("💾 Zapisano plik jako: ${file.absolutePath}")
//                }
//                else -> {
//                    println("⚠️ Inna część multipart: ${part::class.simpleName}")
//                }
//            }
//            part.dispose()
//        }
//
//        if (userId != null && treasureId != null && fileName != null) {
//            try {
//                transaction {
//                    Photos.insert {
//                        it[Photos.userId] = userId!!
//                        it[Photos.treasureId] = treasureId!!
//                        it[Photos.filePath] = "photos/${userId}_${treasureId}_$fileName"
//                    }
//                }
//                println("✅ Zapisano wpis w bazie danych: user=$userId, treasure=$treasureId")
//                call.respond(HttpStatusCode.OK)
//            } catch (e: Exception) {
//                println("❌ Błąd zapisu do bazy: ${e.message}")
//                call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
//            }
//        } else {
//            println("❌ Brakuje danych: userId=$userId, treasureId=$treasureId, fileName=$fileName")
//            call.respond(HttpStatusCode.BadRequest, "Brakuje danych (userId, treasureId lub fileName)")
//        }
//    }

    post("/api/photo") {
        val multipart = call.receiveMultipart()
        var userId: Int? = null
        var treasureId: Int? = null
        var fileName: String? = null
        var savedFilePath: String? = null

        println("📥 Otrzymano multipart...")

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    println("🧾 FormItem: ${part.name} = ${part.value}")
                    when (part.name) {
                        "userId" -> userId = part.value.toIntOrNull()
                        "treasureId" -> treasureId = part.value.toIntOrNull()
                    }
                }
                is PartData.FileItem -> {
                    fileName = part.originalFileName ?: "unnamed.jpg"
                    println("📸 Plik: $fileName")

                    val fileBytes = part.streamProvider().readBytes()
                    val safeFileName = "${userId ?: "NOUSER"}_${treasureId ?: "NOTREASURE"}_$fileName"
                    val file = File("photos/$safeFileName")
                    file.parentFile?.mkdirs()
                    file.writeBytes(fileBytes)

                    savedFilePath = "photos/$safeFileName"
                    println("💾 Zapisano plik jako: ${file.absolutePath}")
                }
                else -> {
                    println("⚠️ Inna część multipart: ${part::class.simpleName}")
                }
            }
            part.dispose()
        }

        if (userId != null && treasureId != null && savedFilePath != null) {
            try {
                transaction {
                    // 1. Zapisz do tabeli Photos
                    Photos.insert {
                        it[Photos.userId] = userId!!
                        it[Photos.treasureId] = treasureId!!
                        it[Photos.filePath] = savedFilePath!!
                    }

                    // 2. Zapisz lub zaktualizuj wpis w FoundTreasures
                    FoundTreasures.insertIgnore {
                        it[FoundTreasures.userId] = userId!!
                        it[FoundTreasures.treasureId] = treasureId!!
                    }

                    FoundTreasures.update({
                        (FoundTreasures.userId eq userId!!) and (FoundTreasures.treasureId eq treasureId!!)
                    }) {
                        it[FoundTreasures.photoPath] = savedFilePath
                    }
                }

                println("✅ Zapisano wpisy w bazie danych: user=$userId, treasure=$treasureId, photo=$savedFilePath")
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                println("❌ Błąd zapisu do bazy: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
            }
        } else {
            println("❌ Brakuje danych: userId=$userId, treasureId=$treasureId, filePath=$savedFilePath")
            call.respond(HttpStatusCode.BadRequest, "Brakuje danych (userId, treasureId lub zdjęcie)")
        }
    }


//    post("/api/photo") {
//        val multipart = call.receiveMultipart()
//        var userId: Int? = null
//        var treasureId: Int? = null
//        var fileName: String? = null
//        var savedFilePath: String? = null
//
//        println("📥 Otrzymano multipart...")
//
//        multipart.forEachPart { part ->
//            when (part) {
//                is PartData.FormItem -> {
//                    println("🧾 FormItem: ${part.name} = ${part.value}")
//                    when (part.name) {
//                        "userId" -> userId = part.value.toIntOrNull()
//                        "treasureId" -> treasureId = part.value.toIntOrNull()
//                    }
//                }
//                is PartData.FileItem -> {
//                    fileName = part.originalFileName ?: "unnamed.jpg"
//                    println("📸 Plik: $fileName")
//
//                    val fileBytes = part.streamProvider().readBytes()
//
//                    val safeFileName = "${userId ?: "NOUSER"}_${treasureId ?: "NOTREASURE"}_$fileName"
//                    val file = File("photos/$safeFileName")
//                    file.parentFile?.mkdirs()
//                    file.writeBytes(fileBytes)
//
//                    savedFilePath = file.path
//                    println("💾 Zapisano plik jako: ${file.absolutePath}")
//                }
//                else -> {
//                    println("⚠️ Inna część multipart: ${part::class.simpleName}")
//                }
//            }
//            part.dispose()
//        }
//
//        if (userId != null && treasureId != null && fileName != null && savedFilePath != null) {
//            try {
//                transaction {
//                    // Zapisz do tabeli Photos
//                    Photos.insert {
//                        it[Photos.userId] = userId!!
//                        it[Photos.treasureId] = treasureId!!
//                        it[Photos.filePath] = savedFilePath!!
//                    }
//
//                    // Zapisz do tabeli FoundTreasures (jeśli nie istnieje)
//                    val alreadyFound = FoundTreasures.select {
//                        (FoundTreasures.userId eq userId!!) and (FoundTreasures.treasureId eq treasureId!!)
//                    }.count() > 0
//
//                    if (!alreadyFound) {
//                        FoundTreasures.insert {
//                            it[FoundTreasures.userId] = userId!!
//                            it[FoundTreasures.treasureId] = treasureId!!
//                            it[FoundTreasures.photoPath] = savedFilePath!!
//                        }
//                        println("📥 Zapisano do FoundTreasures")
//                    } else {
//                        println("ℹ️ Skarb już był oznaczony jako znaleziony")
//                    }
//                }
//                println("✅ Zapisano wpis w bazie danych: user=$userId, treasure=$treasureId")
//                call.respond(HttpStatusCode.OK)
//            } catch (e: Exception) {
//                println("❌ Błąd zapisu do bazy: ${e.message}")
//                call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
//            }
//        } else {
//            println("❌ Brakuje danych: userId=$userId, treasureId=$treasureId, fileName=$fileName")
//            call.respond(HttpStatusCode.BadRequest, "Brakuje danych (userId, treasureId lub fileName)")
//        }
//    }



}

