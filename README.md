# 🀄 MahjongAI (香港麻雀)

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-29%20--%2037-blue?style=for-the-badge)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)](LICENSE)

A modern, high-performance, intelligent 4-player **Hong Kong Style Mahjong (香港麻雀)** Android video game developed natively in **Kotlin** and **Jetpack Compose (Material 3)**.

MahjongAI combines authentic cultural board game mechanics—including 3D dice physics, dynamic tile wall stacks, complete Fan (番) scoring patterns, and in-turn Kong bonus point rules—with an advanced **Hybrid Artificial Intelligence System** powered by a **2-Layer Actor-Critic Neural Network** trained across **40,000 self-play game simulations**, Expectimax Search, Monte Carlo Tree Search (MCTS), Bayesian Opponent Hand Reading, and dynamic Tactical Posture adaptation.

---

## 📋 Table of Contents
1. [Key Features](#-key-features)
2. [Game Rules & Scoring (Hong Kong Mahjong)](#-game-rules--scoring-hong-kong-mahjong)
   - [Tiles & Deck Structure](#tiles--deck-structure)
   - [Match Flow & State Lifecycle](#match-flow--state-lifecycle)
   - [Claim Priorities (Win > Pung/Kong > Chow)](#claim-priorities)
   - [Scoring System & Fan Patterns](#scoring-system--fan-patterns)
   - [Point Scoring & Kong Rules](#point-scoring--kong-rules)
3. [AI Engine Architecture](#-ai-engine-architecture)
   - [Actor-Critic Neural Network](#1-actor-critic-neural-network-mahjongneuralnet)
   - [Expectimax & Live Acceptance (Ukeire)](#2-expectimax--live-acceptance-ukeire-expectimaxengine)
   - [Bayesian Opponent Hand Reading](#3-bayesian-opponent-hand-reading-opponenthandreader)
   - [Tactical Defense & Danger Analysis](#4-tactical-defense--danger-analysis-safetytileanalyzer)
   - [Dynamic Tactical Posture](#5-dynamic-tactical-posture-tacticalposture)
   - [MCTS & Determinization Engine](#6-mcts--determinization-engine-mctsengine)
   - [Headless Self-Play Simulation Pipeline](#7-headless-self-play-simulation-pipeline)
4. [User Interface & Animations](#-user-interface--animations)
5. [Architecture & Project Structure](#-architecture--project-structure)
6. [Tech Stack & Ecosystem](#-tech-stack--ecosystem)
7. [Prerequisites & Build Guide](#-prerequisites--build-guide)
8. [Testing & Verification](#-testing--verification)
9. [GitHub Setup & Push Guide](#-github-setup--push-guide)

---

## 🌟 Key Features

- **Authentic Hong Kong Rules:** Complete 144-tile set including all 8 Flowers and Seasons, dealer rotation (*Ren-zhuang*), prevalent wind cycles (East $\rightarrow$ South $\rightarrow$ West $\rightarrow$ North), and exhaustive draws (*Lau Kuk*).
- **Deep Reinforcement Learning AI:** Pure-Kotlin Actor-Critic Multi-Layer Perceptron (MLP) with domain skip connections and 40,000 self-play trained weights loaded directly into the on-device inference engine.
- **Dynamic AI Postures:** AI opponents switch tactical postures between **ATTACK**, **SPEED**, **DEFENSIVE_FOLD**, and **BALANCED** based on early-wall seed evaluation, table score pressure, and opponent Tenpai threats.
- **Bayesian Opponent Hand Reading:** Defends against opponent flushes, All-Pung (*Toitoi*) attempts, and hidden Tenpai hands using *Genbutsu* (absolute safe tiles), *Kabe* (wall barriers), and *Suji* (half-safety).
- **Physical 3D Dice Simulation:** Canvas-based 3D dice physics with real-time velocity, angular momentum, rotation matrices, drop shadows, and sound effects.
- **Layered Wall Depletion:** Visual 3D-styled wall stacks (18 stacks $\times$ 2 layers on each of the 4 table sides) showing real-time depletion and break markers.
- **Fluid Composables & Micro-Animations:** Built with Jetpack Compose, featuring tile-lift selections, bonus tile melds, discard flight paths, and celebratory round-end sheets.
- **Edge-to-Edge Experience:** Dedicated landscape layout that gracefully handles system gesture bars and display cutouts using Android WindowInsets.

---

## 🀅 Game Rules & Scoring (Hong Kong Mahjong)

### Tiles & Deck Structure
The game uses a complete standard set of **144 tiles**:
- **Suits (108 tiles):**
  - **Characters (萬子 - Wan):** 1 to 9 (4 copies each)
  - **Bamboo (條子/索子 - Tiao/Sou):** 1 to 9 (4 copies each)
  - **Dots (筒子/餅子 - Tong/Pin):** 1 to 9 (4 copies each)
- **Honors (28 tiles):**
  - **Wind Tiles (四風牌):** East (東), South (南), West (西), North (北) (4 copies each)
  - **Dragon Tiles (三元牌):** Red Dragon (紅中), Green Dragon (發財), White Dragon (白板) (4 copies each)
- **Bonus Tiles (8 tiles):**
  - **Flowers (花牌):** Plum (梅 - 1), Orchid (蘭 - 2), Chrysanthemum (菊 - 3), Bamboo (竹 - 4)
  - **Seasons (季牌):** Spring (春 - 1), Summer (夏 - 2), Autumn (秋 - 3), Winter (冬 - 4)

### Match Flow & State Lifecycle
The game is managed by a deterministic Finite State Machine (`GameStateMachine`) adhering to strict phases:

```
[SETUP] ───► [ROLLING_DICE] ───► [ARRANGING] ───► [DISTRIBUTION]
                                                        │
┌───────────────────────────────────────────────────────┘
▼
[PLAYING] ◄─────────────────────────────────────────────┐
   │                                                    │
   ├─► Draw Tile ──► In-Turn Kong / Self-Draw (Hu)?     │
   │                                                    │
   └─► Discard Tile ──► [CLAIM_INTERCEPTION] ───────────┘
                              │
                    (No claims or claim executed)
                              │
                        (Win Declared or Wall Depleted)
                              ▼
                        [ROUND_END]
                              │
                     (Match End Criteria Met?)
                     ├── Yes ──► [GAME_OVER]
                     └── No ───► [SETUP] (Next Round / Wind)
```

1. **`SETUP`:** Wall decks are shuffled and built (18 stacks $\times$ 2 layers per wall side).
2. **`ROLLING_DICE`:** Two 3D dice rolls select the dealer and determine the wall break position.
3. **`ARRANGING`:** Player hands and hidden stacks are initialized.
4. **`DISTRIBUTION`:** 13 tiles are dealt to each player (14 to the dealer), followed by bonus tile replacement rounds.
5. **`PLAYING`:** Players draw, declare in-turn concealed or added Kongs, declare Self-Draw wins (*Tsumo*), or discard.
6. **`CLAIM_INTERCEPTION`:** Other players evaluate whether to intercept the discarded tile for Chow, Pung, Kong, or Win (*Ron*).
7. **`ROUND_END`:** Calculates Fan, updates player game scores, handles dealer rotation (*Ren-zhuang* if dealer won; otherwise rotates counter-clockwise), and advances prevalent winds (East $\rightarrow$ South $\rightarrow$ West $\rightarrow$ North).
8. **`GAME_OVER`:** Triggered when the match concludes or any player's score reaches zero, displaying final match rankings and statistics.

### Claim Priorities
When a tile is discarded, claim intents are resolved in strict accordance with Hong Kong Mahjong rules:
$$\text{Win (Hu / Ron)} > \text{Pung / Kong} > \text{Chow}$$

- **Win (胡牌):** Highest priority. Can be claimed off any opponent's discard.
- **Pung (碰) & Kong (槓):** Can be claimed off any opponent's discard (requires 2 or 3 matching tiles in hand).
- **Chow (吃):** Lowest priority. Allowed **strictly from the player immediately to the left** (turn order distance = 1).

### Scoring System & Fan Patterns
The scoring engine evaluates winning configurations across classic Hong Kong Mahjong patterns:

| Fan (番) | Pattern (牌型) | Description |
| :---: | :--- | :--- |
| **0** | **Chicken Hand (雞胡)** | Basic complete hand (4 melds + 1 pair) without special patterns (Human min: 0, AI min: 1). |
| **1** | **Concealed Hand (門前清)** | Hand completed without declaring any open Chows, Pungs, or melded Kongs. |
| **1** | **Self-Draw (自摸)** | Winning on a tile drawn from the wall yourself (*Tsumo*). |
| **1** | **All Chows (平胡)** | Hand consists entirely of 4 Chows and a non-scoring pair (no dragons or seat/prevalent winds). |
| **1** | **Dragon Pung (三元牌)** | Pung or Kong of Red Dragon (中), Green Dragon (發), or White Dragon (白). |
| **1** | **Seat / Prevalent Wind (門風/圈風)** | Pung or Kong matching your current seat wind or the round's prevalent wind. |
| **1** | **No Flowers (無花)** | Winning without holding any bonus Flower or Season tiles. |
| **1** | **Seat Flower/Season (正花/正季)** | Holding a Flower or Season tile matching your seat number (East=1, South=2, West=3, North=4). |
| **1** | **Under the Sea (海底撈月)** | Winning on the very last tile drawn from the wall. |
| **1** | **Under the River (河底撈魚)** | Winning by claiming the very last discard of the round. |
| **1** | **Win on Replacement (槓上開花)** | Winning immediately on the supplemental tile drawn after declaring a Kong. |
| **1** | **Robbing the Kong (搶槓)** | Winning by claiming an opponent's added tile when they promote a Pung to a Kong. |
| **1** | **Before the Fifth (五巡內)** | Winning within the first 4 turns of the game. |
| **2** | **Pure Straight (一條龍)** | Complete 1 through 9 sequence (1-2-3, 4-5-6, 7-8-9) in a single suit. |
| **2** | **Complete Flower/Season Set (一台花/季)** | Holding all 4 Flowers (Plum, Orchid, Chrysanthemum, Bamboo) or all 4 Seasons. |
| **3** | **Mixed One Suit / Half Flush (混一色)** | Hand formed exclusively of one suit tiles plus Honor tiles (Winds/Dragons). |
| **3** | **All Pungs (對對胡)** | Hand composed of 4 Pungs/Kongs and 1 pair (no Chows). |
| **4** | **Seven Pairs (七對子)** | Special hand formed of 7 distinct pairs. |
| **4** | **All Terminals & Honors (混么九)** | Hand composed entirely of terminal numbers (1 and 9) and Honor tiles. |
| **5** | **Small Three Dragons (小三元)** | Two Pungs of Dragons plus a pair of the third Dragon. |
| **7** | **All One Suit / Full Flush (清一色)** | Hand composed exclusively of a single suit with zero Honor tiles. |
| **8** | **Big Three Dragons (大三元)** | Three Pungs of all three Dragons (Red, Green, and White). |
| **8** | **Small Four Winds (小四喜)** | Three Pungs of Winds plus a pair of the fourth Wind. |
| **8** | **Pure Terminals (清么九)** | Hand composed exclusively of terminal suit tiles (1s and 9s), with no honors. |
| **10** | **All Honors (字一色)** | Hand composed exclusively of Winds and Dragons. |
| **13** | **Thirteen Orphans (十三幺)** | 1 and 9 of each suit, one of each Wind, one of each Dragon, plus any duplicate pair. |
| **13** | **All Green (綠一色)** | Hand composed exclusively of 2, 3, 4, 6, 8 Bamboo and Green Dragons. |
| **13** | **Big Four Winds (大四喜)** | Pungs/Kongs of all four Winds (East, South, West, North). |
| **13** | **Four Kongs (十八羅漢)** | Completing four separate Kongs in a single hand. |

### Point Scoring & Kong Rules
- **Starting Points:** 20,000 points per player.
- **Fan-to-Points Scale:**
  - 0 Fan: 100 points
  - 1 Fan: 200 points
  - 2 Fan: 400 points
  - 3 Fan: 800 points
  - 4 Fan: 1,600 points
  - 5 Fan: 2,400 points
  - 6 Fan: 3,200 points
  - 7 Fan: 4,800 points
  - 8 Fan: 6,400 points
  - 9 Fan: 9,600 points
  - 10+ Fan: 12,800 points
- **Win on Discard (Ron):** The discarder awards $3 \times \text{Base Points}$ to the winning player.
- **Win by Self-Draw (Tsumo):** All 3 opponents each award $1 \times \text{Base Points}$ to the winning player.
- **Immediate Kong Bonus Points:**
  - **Concealed Kong (暗槓):** All 3 opponents each award 400 points to the declarer (+1,200 points total).
  - **Added / Promoted Kong (加槓/補槓):** All 3 opponents each award 200 points to the declarer (+600 points total).
  - **Melded Kong from Discard (大明槓/直槓):** The discarder awards 600 points to the declarer (+600 points total).

---

## 🧠 AI Engine Architecture

MahjongAI implements a state-of-the-art hybrid reasoning engine tailored specifically to the imperfect-information game of Mahjong.

```
                    ┌────────────────────────────┐
                    │    Current Game State      │
                    └─────────────┬──────────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ ExpectimaxEngine │    │SafetyTileAnalyzer│    │OpponentHandReader│
│ • Shanten (0-6)  │    │ • Genbutsu (100%)│    │ • Tenpai Prob.   │
│ • Ukeire Outs    │    │ • Kabe Barriers  │    │ • Flush Reading  │
│ • Fan Potential  │    │ • Suji Traps     │    │ • Toitoi Reading │
└────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                     ┌───────────────────────┐
                     │MahjongFeatureExtractor│ (24-dim vector)
                     └───────────┬───────────┘
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │   MahjongNeuralNet    │ (ResNet MLP)
                     │ • Q(s, a) Evaluation  │
                     └───────────┬───────────┘
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │    TacticalPosture    │ (ATTACK / DEFEND / SPEED)
                     │ • Softmax Temperature │
                     └───────────┬───────────┘
                                 │
                                 ▼
                         Optimal Discard / Claim
```

### 1. Actor-Critic Neural Network (`MahjongNeuralNet`)
- **Architecture:** 24 Input Features $\rightarrow$ Dense 1 (48 units, LeakyReLU) $\rightarrow$ Dense 2 (24 units, LeakyReLU) $\rightarrow$ Output Head (Scalar Q-Value).
- **Residual Skip Connection:** A direct linear skip connection ($W_{\text{skip}}$) from the 24 input features directly to the output layer. This guarantees strong domain heuristics at initialization and allows the deep layers to learn subtle non-linear corrections.
- **Pre-trained Weights:** Loaded from `mahjong_nn_weights.json`, containing optimization results from 40,000 self-play episodes trained with Huber-clipped TD($\lambda$) returns and Potential-Based Reward Shaping.

### 2. Expectimax & Live Acceptance (Ukeire) (`ExpectimaxEngine`)
- Computes exact **Shanten (向聽數)** distance (steps away from Tenpai) using fast bitwise and recursive combinatorial partitioning.
- Evaluates live tile acceptance (**Ukeire / 進張**), taking into account all visible tiles across the board, discards, and open melds.
- Projects expected Fan potential along flush lines, dragon pungs, and concealed straights.

### 3. Bayesian Opponent Hand Reading (`OpponentHandReader`)
- Tracks each opponent's individual discard stream (*Ho*) and exposed melds (*Furo*).
- Predicts opponent **Tenpai probabilities** dynamically based on late-game turn thresholds, meld counts, and middle tile discards.
- Detects early Flush commitments and All-Pung (*Toitoi*) trajectories to avoid dealing into high-Fan hands.

### 4. Tactical Defense & Danger Analysis (`SafetyTileAnalyzer`)
- Calculates a granular danger score from `0.0` (completely safe) to `1.0` (high risk):
  - **Genbutsu (現物):** 100% absolute safety against any player who has previously discarded the tile.
  - **Kabe (壁 - No-Chance Wall Barrier):** When all 4 copies of a suit rank (e.g. 3-Dot) are visible, Ryanmen waits on adjacent ranks (1-Dot and 2-Dot) become structurally impossible.
  - **Suji (筋 - Half-Safety):** Discards of 4-7, 5-8, and 6-9 lines provide probabilistic defensive clues against two-sided waits.
  - **Exhaustion & Scarcity:** Tiles with 3 visible copies cannot form Pungs or Pairs, making them safe late-game discards.

### 5. Dynamic Tactical Posture (`TacticalPosture`)
The AI transitions between four operating postures depending on early-wall seed evaluation, table score pressure, and opponent threats:
- **`ATTACK`:** Triggered when holding high-Fan seeds (e.g. 7+ cards of one suit for a Flush, or 4+ pairs for Seven Pairs / All-Pungs). Pushes aggressively even in the presence of opponent danger.
- **`SPEED`:** Accelerates hand completion with quick Chows and Pungs to prevent opponent win streaks or protect a lead.
- **`DEFENSIVE_FOLD`:** Triggered when an opponent is in Tenpai and the AI's hand is far from winning. Discards safe tiles (*Betaori*).
- **`BALANCED`:** Standard mode maximizing Ukeire and flexible progression.

### 6. MCTS & Determinization Engine (`MctsEngine`)
- Employs **Information Set Monte Carlo Tree Search** for complex claim and discard branching.
- Generates determinized candidate states by randomly distributing unobserved tiles among opponents' hidden hands and the unrevealed wall.
- Selects actions using the Upper Confidence Bound for Trees (UCT):
  $$\text{UCT}(v) = \frac{Q(v)}{N(v)} + c \cdot \sqrt{\frac{\ln N(\text{parent})}{N(v)}}$$

### 7. Headless Self-Play Simulation Pipeline
- Includes `HeadlessSelfPlayTrainer`, a zero-UI, multi-threaded simulator that runs complete 4-player matches concurrently on background coroutines.
- Employs **Potential-Based Reward Shaping (PBRS)**:
  $$F(s, s') = \gamma \Phi(s') - \Phi(s)$$
  where $\Phi(s)$ rewards improvements in Shanten, live tile acceptance, and hand structure.
- Verifiable via the included unit test `SelfPlayTrainingTest`.

---

## 🎨 User Interface & Animations

- **Material 3 Theming:** Custom color palette tuned for Mahjong felts, tiles, and status badges (`SubtleGreen`, `CrimsonRed`, `CoolOrange`, `PureWhite`).
- **3D Dice Component (`Dice3D`):** Full 3D perspective projection with tumbling physics, edge bevels, authentic pip layouts, and bouncing momentum.
- **Dynamic Wall Visualization:** Visual horizontal and vertical tile stacks accurately reflecting remaining tiles in the wall and indicating the break location.
- **Tile View (`TileView`):** Rendered tiles featuring subtle depth bevels, layered shadows, suit color styling, and custom vector/drawable mappings via `TileImageMapper`.
- **Interactive Claims Control:** Context-aware floating action bar that highlights legal Chow/Pung/Kong/Win options with one-touch intent execution.
- **Audio Feedback (`GameSoundManager` / `DiceSoundManager`):** High-fidelity sound effects for rolling dice, drawing, discarding, melding, claiming, and declaring a win.
- **Result Dialogs:** Transparent modal overlays for Round End, Match Results, and Game Over with point updates and breakdown badges.

---

## 📁 Architecture & Project Structure

The project follows Clean Architecture principles with strict Unidirectional Data Flow (UDF) managed by Koin:

```
app/src/main/java/com/nowlsantos/mahjongai/
├── MahjongAIApplication.kt          # Application entrypoint & Koin initialization
├── MainActivity.kt                  # Edge-to-edge landscape activity
├── di/
│   └── AppModule.kt                 # Koin dependency injection bindings
├── engine/
│   ├── ExpectimaxEngine.kt          # Expectimax search & offensive valuation
│   ├── defense/
│   │   ├── OpponentHandReader.kt    # Bayesian opponent Tenpai & Flush reader
│   │   └── SafetyTileAnalyzer.kt    # Genbutsu, Suji, and Kabe danger analysis
│   ├── flow/
│   │   ├── ClaimResolver.kt         # Conflict resolution for claims
│   │   ├── MahjongGameEngine.kt     # Core table state orchestrator
│   │   └── MoveGenerator.kt         # Legal turn and claim action generator
│   ├── logic/
│   │   ├── HandEvaluator.kt         # Winning hand shape validation (Hu)
│   │   └── TingCalculator.kt        # Exact Shanten calculation & Ukeire outs
│   ├── mcts/
│   │   ├── DeterminizationEngine.kt # Imperfect information world sampling
│   │   ├── MctsEngine.kt            # Monte Carlo Tree Search
│   │   └── MctsNode.kt              # MCTS search tree node
│   ├── rl/
│   │   ├── HeadlessSelfPlayTrainer.kt # Headless multi-threaded trainer
│   │   ├── MahjongFeatureExtractor.kt # 24-dim domain feature extraction
│   │   ├── MahjongNeuralNet.kt       # 2-Layer Actor-Critic ResNet MLP
│   │   ├── RewardCalculator.kt      # PBRS & terminal score returns
│   │   ├── RlDecisionEngine.kt      # Discard selection via Boltzmann Softmax
│   │   └── TacticalPosture.kt       # Attack/Defend/Speed posture manager
│   └── scoring/
│       ├── FanCalculator.kt         # Hong Kong Mahjong Fan scoring rules
│       └── KongPaymentRules.kt      # Modern Kong point calculations
├── fsm/
│   └── game/                        # Finite State Machine states
│       ├── GamePhase.kt             # GamePhase enumeration
│       ├── GameStateMachine.kt      # State machine coordinator
│       ├── SetupState.kt            # Deck shuffle & wall building
│       ├── RollDiceState.kt         # 3D dice rolling & dealer break
│       ├── ArrangingState.kt        # Tile arrangement
│       ├── DistributionState.kt     # Dealing & flower replacement
│       ├── PlayingState.kt          # Active turn loop
│       ├── ClaimInterceptionState.kt # Reaction window & claim resolution
│       ├── RoundEndState.kt         # Scoring, point adjustment & wind rotation
│       └── GameOverState.kt         # Match termination
├── model/                           # Game domain & UI models
│   ├── GameState.kt                 # Immutable top-level game state
│   ├── GameViewModel.kt             # Presentation ViewModel & UDF pipeline
│   ├── MahjongTile.kt               # Tile data classes & enumerations
│   ├── PlayerState.kt               # Player hand, melds, discards & score
│   └── WallStack.kt                 # Physical wall stack models
├── ui/
│   ├── animations/                  # Micro-animations (Draw, Discard, Meld, Bonus)
│   ├── components/                  # Jetpack Compose UI components
│   │   ├── CentralDiscardPool.kt    # Table discard pool
│   │   ├── TileView.kt              # Mahjong tile composable
│   │   ├── dice/Dice3D.kt           # Canvas-based 3D physics dice
│   │   ├── dialogs/                 # RoundEnd, GameResults, GameOver dialogs
│   │   ├── player/                  # PlayerHand, PlayerMelds, PlayerController
│   │   └── walls/                   # 3D visual wall stacks
│   ├── screens/MahjongTable.kt      # Main Mahjong table screen
│   └── theme/                       # Material 3 colors, typography, theme
└── util/                            # Helpers, Sound Managers & Constants
```

---

## 🛠 Tech Stack & Ecosystem

- **Language:** Kotlin 2.2.10
- **Android Target:** Android API 37 (Compile SDK: 37, Min SDK: 29)
- **UI Toolkit:** Jetpack Compose (BOM `2026.02.01`) with Material 3
- **Build System:** Gradle 9.6.0 with Android Gradle Plugin (AGP) 9.4.1
- **Dependency Injection:** Koin 4.2.2 (`koin-android`, `koin-androidx-compose`)
- **Concurrency & Reactivity:** Kotlin Coroutines & `StateFlow` / `SharedFlow`
- **Testing Frameworks:** JUnit 4, Espresso, AndroidX Test Runner

---

## 🚀 Prerequisites & Build Guide

### Prerequisites
- **Android Studio:** Android Studio Ladybug | 2024.2.1 or newer
- **Java Development Kit (JDK):** JDK 17 or JDK 21
- **Android Device / Emulator:** Running Android 10 (API level 29) or higher

### Building from Command Line
Clone the repository and build the debug APK using the included Gradle wrapper:

```bash
# Clone the repository
git clone https://github.com/your-username/MahjongAI.git
cd MahjongAI

# Build the Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Testing & Verification

The project includes unit tests covering game rules, move generation, wall management, scoring calculations, and self-play training:

```bash
# Run all unit tests
./gradlew testDebugUnitTest
```

### Verified Test Suites:
- **`MoveGeneratorTest`:** Validates legal Chows, Pungs, Kongs, and Self-Draw win options under Hong Kong rules.
- **`KongPaymentTest`:** Validates immediate point adjustments for Concealed, Added, and Melded Kongs.
- **`RoundEndSimulationTest`:** Tests complete multi-round match flows, dealer rotations, prevalent wind cycles, and round termination.
- **`PreciseWallManagerTest`:** Verifies 144-tile wall building, break offsets, and draw exhaustion.
- **`SelfPlayTrainingTest`:** Tests the headless reinforcement learning training loop and weight updates.
- **`GameValidatorTest`:** Asserts table invariant checks and state transition safety.

---

## 📤 GitHub Setup & Push Guide

To upload this project to your GitHub account:

1. **Create a new empty repository** on GitHub (e.g. `https://github.com/<your-username>/MahjongAI.git`).
2. Run the following commands in the project root directory:

```bash
# Verify git status
git status

# Stage all files
git add .

# Create the initial commit
git commit -m "Initial commit: MahjongAI complete Hong Kong Mahjong engine with RL AI"

# Rename default branch to main (if preferred)
git branch -M main

# Add your GitHub repository remote
git remote add origin https://github.com/<your-username>/MahjongAI.git

# Push to GitHub
git push -u origin main
```

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
