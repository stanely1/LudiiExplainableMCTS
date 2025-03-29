package main

import ai.RandomAI

import app.StartDesktopApp
import utils.AIRegistry;

object RunLudii extends App {
    if (!AIRegistry.registerAI("Scala Random AI", () => new RandomAI(), (game) => true)) {
        System.err.println("WARNING! Failed to register AI because one with that name already existed!")
    }

    StartDesktopApp.main(Array.empty[String])
}
