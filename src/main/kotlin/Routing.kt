//package com.example
//
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import io.ktor.server.application.*
//import io.ktor.server.plugins.contentnegotiation.*
//import io.ktor.server.plugins.cors.routing.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import com.example.treasureRoutes
//
//fun Application.configureRouting() {
//    routing {
//        treasureRoutes()
////        foundRoutes()
//
//    }
//}


package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.* // DODANE
import java.io.File // DODANE
import com.example.treasureRoutes

fun Application.configureRouting() {
    routing {
        treasureRoutes()

        // 🔽 To udostępnia folder 'photos' jako statyczny
        static("/photos") {
            staticRootFolder = File(".") // aktualny katalog
            files("photos") // folder z plikami zdjęć
        }
    }
}
