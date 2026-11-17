# AGENTS.MD - AI Development & Engineering Standards (MahjongAI)

## 1. Overview & Agent Mission
This document defines the strict engineering guidelines, architectural invariants, coding conventions, and tool execution standards for AI coding agents working within the **MahjongAI** Android game project.

**STRICT PROHIBITION ON HALLUCINATION:** The agent must never guess project state, file paths, or code logic. All information provided to the user must be verified via tool execution (`read_file`, `list_files`, `find_usages`, etc.). If information is missing, the agent must ask for clarification rather than assuming.

**MANDATORY USER CONFIRMATION FOR FEATURE REMOVAL:**
The agent MUST NEVER remove, deprecate, disable, or alter any existing feature, UI component, workflow, or system behavior without asking for explicit user confirmation first.

**MANDATORY RULE: STRICT COMPLIANCE WITH USER-UPDATED CODE:**
The agent MUST NEVER modify, overwrite, or revert code sections that the user has already updated or written. When adding new features, parameters, or callbacks (such as click listeners), patch only the specific new parameter/callback without altering or resetting the existing layout, positioning, or rendering logic that the user has custom-configured.

**MANDATORY TECHNICAL LEADERSHIP:**
Do not be a "yes-man" or blindly agree with user suggestions. Provide rigorous, uncompromising technical leadership, analytical scrutiny, and architectural best practices from the start.

**MANDATORY DEEP SCRUTINY:**
Every proposed solution, refactoring, or code change MUST be the result of analytical scrutiny across state flows, AI inference lifecycles, and architectural boundaries.

---

## 2. Tech Stack & Ecosystem Standards
- **Platform / IDE:** Android Studio (Latest Stable), targeting Android API level 37 (`minSdk 29`, `compileSdk 37`, `targetSdk 37`).
- **Language:** Kotlin (Latest stable version, strict null safety, leveraging advanced Kotlin collection features such as immutable/read-only collection APIs, chunking, grouping, windowed operations, and functional transformations essential for tile distribution, hand evaluation, meld sorting, and MCTS state management).
- **Frontend UI:** Jetpack Compose with Material 3, adhering strictly to Unidirectional Data Flow (UDF) and edge-to-edge display rules.
- **Dependency Injection:** Koin (`koin-android`, `koin-androidx-compose`).
- **AI Inference & Logic:** MCTS (`MctsEngine`), Expectimax (`ExpectimaxEngine`), Determinization (`DeterminizationEngine`), Safety Tile Analyzer (`SafetyTileAnalyzer`), Claim Resolver (`ClaimResolver`), and State Machine (`GameStateMachine`).
- **Architecture Pattern:** Clean Architecture principles structured within the Android module (`fsm`, `engine`, `model`, `ui`, `di`, `util`).
- **Asynchronous Concurrency:** Kotlin Coroutines and `StateFlow` / `SharedFlow`. **Strict prohibition:** Never use RxJava or LiveData.

---

## 3. Project Architecture & Directory Layout (`:app`)
- **`com.nowlsantos.mahjongai.fsm`**: State machines handling game phases (`GameStateMachine`, `SetupState`, `RollDiceState`, `DistributionState`, `PlayingState`, `ClaimInterceptionState`, `RoundEndState`, `GameOverState`).
- **`com.nowlsantos.mahjongai.engine`**: Advanced AI and game rules logic, including MCTS, Expectimax, determinization, safety tile analysis, hand evaluation, and claim resolution.
- **`com.nowlsantos.mahjongai.model`**: Core game models (`GameState`, `MahjongTile`, `WallStack`, `PlayerWall`, `GameInput`, `GameViewModel`).
- **`com.nowlsantos.mahjongai.ui.components` & `ui.screens`**: Jetpack Compose UI components (`TileView``) and screens (`GameScreen`).
- **`com.nowlsantos.mahjongai.di`**: Koin dependency injection modules (`appModule`).
- **`com.nowlsantos.mahjongai.util`**: Utilities including `Logger` and `TileImageMapper`.

---

## 4. Coding Style & Architectural Rules

### 4.1. Jetpack Compose & UI Layer
1. **Stateless Composables:** Screens must have a stateful root collecting from ViewModel and stateless content.
2. **Material 3 & Theming:** Consume styling from `MaterialTheme`. Leverage Jetpack Compose styling and resource mappings (`TileImageMapper`).
3. **Edge-to-Edge & Insets:** Ensure all composables properly handle window insets (system bars and gesture navigation) and landscape orientation.
4. **Localization:** All user-facing text MUST use `stringResource(R.string...)` (Zero raw strings).
5. **Lifecycle Awareness:** Use `collectAsState()` / `collectAsStateWithLifecycle()`.

### 4.2. ViewModel & State Management
- Follow strict **Unidirectional Data Flow (UDF)**.
- Expose read-only `StateFlow` (`StateFlow<GameState>`) to the UI, backed by `GameStateMachine` and managed within `GameViewModel`.

### 4.3. Error Handling & Robustness
- Use Kotlin's `Result<T>` for domain/data boundaries where applicable.
- UI state should include structured error handling and loading states.

---

## 5. Agent Quality Checklist
Before finalizing code, verify:
- [x] **Zero Hallucination:** All logic is grounded in actual project files.
- [x] **No Raw Strings:** All UI text is in `strings.xml`.
- [x] **UDF Enforcement:** ViewModel state mutations are private and flows are read-only.
- [x] **Resource Cleanup:** Native resources and coroutine scopes are properly managed and closed.
- [x] **Edge-to-Edge & M3 Compliance:** UI properly respects system insets and Material 3 design tokens.
