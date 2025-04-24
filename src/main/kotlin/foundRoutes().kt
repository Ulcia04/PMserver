//package com.example
//
//import io.ktor.server.application.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import io.ktor.http.*
//import org.jetbrains.exposed.sql.*
//import org.jetbrains.exposed.sql.transactions.transaction
//
//fun Route.foundRoutes() {
//    get("/api/found/treasures") {
//        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
//        if (userId == null) {
//            call.respond(HttpStatusCode.BadRequest, "Brakuje userId")
//            return@get
//        }
//
//        val treasures = transaction {
//            (FoundTreasures innerJoin Treasures)
//                .select { FoundTreasures.userId eq userId }
//                .map {
//                    Treasure(
//                        id = it[Treasures.id],
//                        name = it[Treasures.name],
//                        description = it[Treasures.description],
//                        lat = it[Treasures.lat],
//                        lng = it[Treasures.lng]//,
////                                photoPath = it[FoundTreasures.photoPath]
//                    )
//                }
//        }
//
//        call.respond(treasures)
//    }
//}
