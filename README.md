# LudiiExplainableMCTS

Explainable MCTS with Enhancements for Ludii GGP System

## How to Run

After installing the dependencies, you need to download `Ludii.jar`.
You can do this manually, or by running one of the following commands:

- `./gradlew downloadLudiiJar` (for Linux/macOS)
- `.\gradlew.bat downloadLudiiJarWindows` (for Windows)

Then, run the project with:

- **Linux/macOS**:
  ```bash
  ./gradlew run
  ```
- **Windows**:
  ```bash
  .\gradlew.bat run
  ```
## Dependencies

This project requires the following tools:

* `java 21`
* `wget`

## Agent Configuration

To customize the agent you can modify [`config/mctsConfig.json`](config/mctsConfig.json) file.
It contains a configuration in JSON format, where you can specify the following parameters:

- **`useScoreBounds`**: Enables or disables the use of score bounds.
- **`usePNS`**: Enables or disables the use of proof and disproof numbers.
- **`selectionPolicy`**: Strategy used during the selection phase. Supported values: `"UCT"`, `"UCB1"`, `"RAVE"`, `"GRAVE"`, `"RobustChild"`, `"MostVisited"`.
- **`finalMoveSelectionPolicy`**: Strategy for selecting the final move. Supported values are the same as for `selectionPolicy`.
- **`playoutPolicy`**: Policy used for move selection during rollouts. Supported values: `"Uniform"`, `"MAST"`, `"NST"`.
- **`graveBias`**: Bias term in the GRAVE selection formula.
- **`graveRef`**: Visit count threshold for GRAVE (`ref` parameter).
- **`epsilon`**: Exploration rate for $\epsilon$-greedy playout strategies (MAST and NST).
- **`maxNGramLength`**: Maximum N-Gram length for the NST playout policy.

Once you have updated the configuration, simply save the file and run the application - no rebuild is required.
