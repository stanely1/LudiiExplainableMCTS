package main;

import app.StartDesktopApp;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import mcts.ExplainableMcts;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {

        // Always load config from config.json in working directory
        String configPath = "config.json";
        String jsonConfig;
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(configPath));
            jsonConfig = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read config.json: " + e.getMessage());
            System.exit(1);
            return;
        }

        ExplainableMcts ai;
        try {
            ai = ExplainableMctsFactory.fromJson(jsonConfig);
        } catch (IOException e) {
            System.err.println("Failed to parse config.json: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Register Explainable MCTS
        if (!AIRegistry.registerAI("Explainable MCTS", () -> ai, game -> true)) {
            System.err.println("WARNING! Explainable MCTS already registered.");
        }

        // Register Proof-Number Search
        // if (!AIRegistry.registerAI(
        //         "Proof-Number Search",
        //         ProofNumberSearch::new,
        //         game -> true)) {
        //     System.err.println("WARNING! Proof-Number Search already registered.");
        // }

        // Launch Ludii desktop UI
        StartDesktopApp.main(new String[0]);
    }
}
