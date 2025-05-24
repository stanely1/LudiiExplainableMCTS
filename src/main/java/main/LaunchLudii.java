package main;

import app.StartDesktopApp;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import mcts.ExplainableMcts;
import pns.ProofNumberSearch;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {

        final String configDir = "config";
        final String configName = "mctsConfig";
        final String defaultConfigName = "defaultMctsConfig";

        String jsonConfig = loadConfig(configDir, configName);
        if (jsonConfig == null) {
            System.err.println("Using default config");
            jsonConfig = loadConfig(configDir, defaultConfigName);
        }
        final String finalJsonConfig = jsonConfig;

        if (!AIRegistry.registerAI("Explainable MCTS", () -> createMctsFromJson(finalJsonConfig), game -> true)) {
            System.err.println("WARNING! Explainable MCTS already registered.");
        }

        if (!AIRegistry.registerAI("Proof-Number Search", () -> new ProofNumberSearch(), game -> true)) {
            System.err.println("WARNING! Proof-Number Search already registered.");
        }

        StartDesktopApp.main(new String[0]);
    }

    private static String loadConfig(final String configDir, final String configName) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(String.format("%s/%s.json", configDir, configName)));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println(String.format("Failed to read %s: %s", configName, e.getMessage()));
            return null;
        }
    }

    private static ExplainableMcts createMctsFromJson(final String jsonConfig) {
        try {
            return ExplainableMctsFactory.fromJson(jsonConfig);
        } catch (IOException e) {
            System.err.println(String.format("Failed to parse config: %s", e.getMessage()));
            return null;
        }
    }
}
