package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import java.io.File
import com.example.treasureRoutes

fun Application.configureRouting() {
    routing {
        treasureRoutes()

        //udostepniam folder photos jakos statyczny
        static("/photos") {
            staticRootFolder = File(".")
            files("photos")
        }
    }
}
