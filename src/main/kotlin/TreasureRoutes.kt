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
import org.jetbrains.exposed.dao.id.IntIdTable

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

@kotlinx.serialization.Serializable
data class SimpleUser(
    val id: Int,
    val username: String,
    val ip: String,
    val foundCount: Int
)

@kotlinx.serialization.Serializable
data class UserResponse(
    val id: Int,
    val username: String
)


fun Route.treasureRoutes() {

    route("/api/treasures") {

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


    post("/api/found") {
        try {
            val request = call.receive<FoundRequest>()

            val alreadyExists = transaction {

                FoundTreasures.select {
                    (FoundTreasures.userId eq request.userId) and
                            (FoundTreasures.treasureId eq request.treasureId)
                }.count() > 0
            }
            if (alreadyExists) {

                call.respond(HttpStatusCode.Conflict, "Skarb już został znaleziony.")
                return@post
            }


            transaction {
                FoundTreasures.insertIgnore {
                    it[userId] = request.userId
                    it[treasureId] = request.treasureId
                }
            }

            call.respond(HttpStatusCode.Created)

        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Błąd serwera: ${e.message}")
        }
    }

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
    post("/api/photo") {
        val multipart = call.receiveMultipart()
        var userId: Int? = null
        var treasureId: Int? = null
        var fileName: String? = null
        var savedFilePath: String? = null


        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "userId" -> userId = part.value.toIntOrNull()
                        "treasureId" -> treasureId = part.value.toIntOrNull()
                    }
                }
                is PartData.FileItem -> {
                    fileName = part.originalFileName ?: "unnamed.jpg"

                    val fileBytes = part.streamProvider().readBytes()
                    val safeFileName = "${userId ?: "NOUSER"}_${treasureId ?: "NOTREASURE"}_$fileName"
                    val file = File("photos/$safeFileName")
                    file.parentFile?.mkdirs()
                    file.writeBytes(fileBytes)

                    savedFilePath = "photos/$safeFileName"
                }
                else -> {
                }
            }
            part.dispose()
        }

        if (userId != null && treasureId != null && savedFilePath != null) {
            try {
                transaction {
                    Photos.insert {
                        it[Photos.userId] = userId!!
                        it[Photos.treasureId] = treasureId!!
                        it[Photos.filePath] = savedFilePath!!
                    }
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

              call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Database error: ${e.message}")
            }
        } else {
           call.respond(HttpStatusCode.BadRequest, "Brakuje danych (userId, treasureId lub zdjęcie)")
        }
    }


    get("/api/users") {
        val users = transaction {
            Users.leftJoin(FoundTreasures)
                .slice(Users.id, Users.username, Users.ip, FoundTreasures.treasureId.count())
                .selectAll()
                .groupBy(Users.id, Users.username, Users.ip)
                .map {
                    val id = it[Users.id].value
                    val username = it[Users.username]
                    val ip = it[Users.ip]
                    val foundCount = it[FoundTreasures.treasureId.count()].toInt()
                    SimpleUser(id, username, ip, foundCount)
                }
        }

        call.respond(users)
    }




    get("/api/photos") {
        val photos = transaction {
            Photos.selectAll().map {
                mapOf(
                    "id" to it[Photos.id],
                    "userId" to it[Photos.userId],
                    "treasureId" to it[Photos.treasureId],
                    "filePath" to it[Photos.filePath]
                )
            }
        }
        call.respond(photos)
    }


    put("/api/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        val newUsername = call.receiveText()

        if (id == null || newUsername.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Brak danych")
            return@put
        }

        transaction {
            Users.update({ Users.id eq id }) {
                it[username] = newUsername
            }
        }

        call.respond(HttpStatusCode.OK, "Zaktualizowano nazwę użytkownika")
    }

    post("/api/users") {
        val params = call.receiveParameters()
        val username = params["username"]
        val ip = params["ip"]

        if (username == null || ip == null) {
            call.respond(HttpStatusCode.BadRequest, "Brakuje username lub ip")
            return@post
        }

        val existingUser = transaction {
            Users.select { Users.ip eq ip }.singleOrNull()
        }

        val userId = if (existingUser != null) {
            existingUser[Users.id].value
        } else {
            transaction {
                Users.insertAndGetId {
                    it[Users.username] = username
                    it[Users.ip] = ip
                }.value
            }
        }

        call.respond(mapOf("userId" to userId))
    }



    get("/api/userByIp") {
        val ip = call.request.queryParameters["ip"]
        if (ip == null) {
            call.respond(HttpStatusCode.BadRequest, "Brakuje adresu IP")
            return@get
        }

        val user = transaction {
            Users.select { Users.ip eq ip }
                .map {
                    UserResponse(
                        id = it[Users.id].value,
                        username = it[Users.username]
                    )
                }
                .singleOrNull()
        }

        if (user != null) {
            call.respond(user)
        } else {
            call.respond(HttpStatusCode.NotFound, "Nie znaleziono użytkownika o IP: $ip")
        }
    }


    get("/api/ranking/position") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, "Brakuje userId")
            return@get
        }

        val ranking = transaction {
            val counts = FoundTreasures
                .slice(FoundTreasures.userId, FoundTreasures.treasureId.count())
                .selectAll()
                .groupBy(FoundTreasures.userId)
                .map { it[FoundTreasures.userId] to it[FoundTreasures.treasureId.count()] }
                .sortedByDescending { it.second }

            counts.indexOfFirst { it.first == userId } + 1
        }

        call.respond(ranking)
    }

}
