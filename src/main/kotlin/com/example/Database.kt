package com.example

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.dao.id.IntIdTable

object Treasures : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val description = text("description")
    val lat = double("lat")
    val lng = double("lng")

    override val primaryKey = PrimaryKey(id)
}

object Users : IntIdTable() {
    val username = varchar("username", 50)
    val ip = varchar("ip", 50).uniqueIndex()
}
object FoundTreasures : Table() {
    val userId = integer("user_id").references(Users.id)
    val treasureId = integer("treasure_id").references(Treasures.id)
    val photoPath = varchar("photo_path", 255).nullable() ///!!!
    override val primaryKey = PrimaryKey(userId, treasureId)
}
object Photos : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("userId")
    val treasureId = integer("treasureId")
    val filePath = varchar("filePath", 255)

    override val primaryKey = PrimaryKey(id)
}

fun initDatabase() {
    Database.connect("jdbc:sqlite:findit.db", driver = "org.sqlite.JDBC")

    transaction {
        SchemaUtils.create(Treasures, Users, FoundTreasures,Photos)
        if (Treasures.selectAll().empty()) {
            Treasures.insert {
                it[name] = "A"
                it[description] = "Skarb A"
                it[lat] = 52.2397
                it[lng] = 21.0522
            }
            Treasures.insert {
                it[name] = "B"
                it[description] = "Skarb B"
                it[lat] = 52.1090
                it[lng] = 21.0424
            }
            Treasures.insert {
                it[name] = "C"
                it[description] = "Skarb C"
                it[lat] = 52.3295
                it[lng] = 21.0000
            }
            Treasures.insert {
                it[name] = "D"
                it[description] = "Skarb D"
                it[lat] = 52.0000
                it[lng] = 21.0051
            }
            println("🪙 Dodano domyślne skarby A–D")
        }
    }
}
