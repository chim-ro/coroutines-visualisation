package com.coroutines.viz.routes

import com.coroutines.viz.scenario.ScenarioRegistry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.scenarioRoutes() {
    routing {
        route("/api/scenarios") {
            get {
                call.respond(ScenarioRegistry.listAll())
            }

            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val level = call.request.queryParameters["level"] ?: "beginner"
                val scenario = ScenarioRegistry.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Scenario '$id' not found")
                call.respond(scenario.buildTimeline(level))
            }
        }
    }
}
