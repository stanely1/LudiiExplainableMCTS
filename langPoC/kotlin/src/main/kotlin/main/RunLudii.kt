package main

import ai.RandomAI

import app.StartDesktopApp
import utils.AIRegistry

fun main() {
    if (!AIRegistry.registerAI("Kotlin Random AI", { RandomAI() }, { true })) {
        System.err.println("WARNING! Failed to register AI because one with that name already existed!")
    }

    StartDesktopApp.main(emptyArray())
}
