# Coroutines Structured Concurrency Visualizer

An interactive visualization tool that demonstrates Kotlin coroutines structured concurrency concepts — parent-child job hierarchies, state transitions, cancellation propagation (downward), and exception propagation (upward) — through animated tree visualizations.

## Architecture

- **Backend**: Kotlin + Ktor server serving simulation data via REST API (port 8080)
- **Frontend**: React + TypeScript + Vite with HTML5 Canvas for interactive visualization (port 5173)

## Project Structure

```
Coroutines Visualization/
├── backend/                          # Kotlin + Gradle
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/coroutines/viz/
│       ├── Application.kt            # Ktor server entry point
│       ├── model/                     # JobState, BuilderType, JobType, CoroutineNode
│       ├── event/                     # SimulationEvent sealed class, EventTimeline
│       ├── scenario/                  # 6 scenario implementations + registry
│       └── routes/                    # REST endpoints
│
├── frontend/                          # React + TypeScript + Vite
│   └── src/
│       ├── App.tsx                    # Root layout: sidebar + canvas + controls
│       ├── types/                     # TypeScript types matching Kotlin models
│       ├── api/                       # Fetch layer for backend API
│       ├── hooks/                     # useAnimationEngine, useBuilderState
│       ├── components/                # TreeCanvas, ScenarioPanel, ControlPanel, NodeContextMenu, etc.
│       ├── builder/                   # Custom scenario builder (panel, forms, preview, timeline generator)
│       ├── manipulation/              # Live node manipulation (event injector)
│       ├── rendering/                 # Reingold-Tilford layout, node/edge/wave renderers
│       └── utils/                     # Color palette, easing functions
│
└── src/resources/                     # Training materials (reference)
```

## Tech Stack

### Backend
- **Kotlin** (JVM, targeting JDK 17)
- **Gradle** with Kotlin DSL
- **Ktor** for HTTP server
- **kotlinx.serialization** for JSON

### Frontend
- **React 18** with functional components + hooks
- **TypeScript**
- **Vite** for dev server + build
- **HTML5 Canvas** for tree rendering (60fps animation)

## REST API

| Method | Path | Response |
|--------|------|----------|
| GET | `/api/scenarios` | List of `{id, name, description, category}` |
| GET | `/api/scenarios/{id}` | Full `EventTimeline` with tree structure + events |

## Scenarios

1. **Happy Path** (Basics) — All coroutines complete normally: Active → Completing → Completed. Parent waits for children.
2. **Suspension & Resumption** (Basics) — A coroutine hits delay() and suspends, freeing the thread. Other coroutines run, then it resumes. The "aha moment" for coroutine concurrency.
3. **Downward Cancellation** (Cancellation) — Parent cancelled, signal cascades to all descendants with staggered timing.
4. **Child Exception** (Exceptions) — Child throws, exception propagates up, siblings get cancelled.
5. **SupervisorJob** (Exceptions) — Same exception under SupervisorJob: only failing child cancelled, siblings continue.
6. **Scope Comparison** (Comparison) — Side-by-side: coroutineScope vs supervisorScope with same exception (two trees).
7. **Nested Scopes** (Advanced) — Deep tree (4 levels) with mixed scope types.

## Coroutine Job States

| State | Color | Description |
|-------|-------|-------------|
| New | Gray (`#565f89`) | Job created but not started |
| Active | Green (`#9ece6a`) | Job is running |
| Suspended | Purple (`#bb9af7`) | Job hit a suspend point (delay, network call) — thread is freed |
| Completing | Yellow (`#e0af68`) | Job finished work, waiting for children |
| Completed | Blue (`#7aa2f7`) | Job and all children finished successfully |
| Cancelling | Orange (`#ff9e64`) | Job is being cancelled |
| Cancelled | Red (`#f7768e`) | Job was cancelled |

## Key Concepts Visualized

- **Suspension frees the thread**: When a coroutine hits `delay()` or a suspending call, it suspends without blocking — the thread is released for other coroutines to use
- **Resumption is seamless**: When the suspend point completes (e.g., delay expires), the coroutine resumes exactly where it left off
- **Cancellation propagates DOWNWARD**: When a parent is cancelled, all children receive the cancellation signal
- **Exceptions propagate UPWARD**: When a child throws, the exception travels up to the parent scope
- **SupervisorJob**: Prevents exception propagation — a failing child doesn't affect its siblings
- **coroutineScope vs supervisorScope**: coroutineScope is all-or-nothing, supervisorScope allows independent child failures
- **Structured concurrency**: Parents always wait for children to complete before they can complete

## UI Features

- **Animated tree canvas**: Nodes change color on state transitions with flash/scale animations
- **Propagation waves**: Colored dots travel along edges (orange for cancellation, red for exceptions)
- **Active node breathing**: Subtle glow animation on Active nodes
- **Cancelling node flicker**: Flickering opacity on Cancelling nodes
- **Playback controls**: Play/Pause/Step Forward/Step Backward/Reset with speed slider (0.25x–4x)
- **Interactive canvas**: Click nodes to see details, drag to pan, scroll to zoom
- **Side-by-side comparison**: Scope Comparison scenario shows two trees side by side
- **Custom scenario builder**: Create your own coroutine trees with configurable failures
- **Live node manipulation**: Right-click active nodes during playback to cancel, inject exceptions, or force-complete

## Build & Run

```bash
# Backend
cd backend
./gradlew run                    # Starts Ktor on http://localhost:8080

# Frontend (in separate terminal)
cd frontend
npm install
npm run dev                      # Starts Vite on http://localhost:5173 (proxies API to :8080)
```

## Creating Custom Scenarios

You can build your own coroutine trees and simulate structured concurrency behavior without any backend changes.

### How to use the scenario builder

1. Click the **"+ Create Scenario"** button at the bottom of the left sidebar.
2. A modal opens with two panels:
   - **Left panel** — Node configuration forms. Each node has:
     - **Name**: A display name for the coroutine (e.g. "networkCall").
     - **Builder**: The coroutine builder type (`Launch`, `Async`, `CoroutineScope`, `SupervisorScope`, `RunBlocking`).
     - **Job Type**: `Job` (regular) or `SupervisorJob` (absorbs child exceptions).
     - **Throws exception** (checkbox): Enable to configure a failure for this node.
       - **Message**: The exception message shown in the event log.
       - **Timing (ms)**: When the exception occurs (relative to simulation start). Minimum 100ms.
     - **+ Add Child**: Adds a child coroutine under this node.
     - **Remove**: Deletes this node and its subtree (not available for the root).
   - **Right panel** — A live tree preview that updates as you add/remove/edit nodes. Click nodes in the preview to select them.
3. Enter a **Scenario Name** at the top of the left panel.
4. Click **"Generate & Play"** to auto-generate the event timeline and start playback.

### What the generator does

The timeline is generated automatically based on structured concurrency rules:
- **Activation**: All nodes transition from `New` to `Active` in BFS order, staggered by tree depth.
- **Failure**: Nodes with the "Throws exception" flag fail at their configured timing.
- **Exception propagation (upward)**: Exceptions travel up the tree. `SupervisorJob`/`SupervisorScope` parents absorb the exception; regular `Job` parents enter `Cancelling` and cancel their other children.
- **Cancellation propagation (downward)**: When a node enters `Cancelling`, all its active children are cancelled recursively.
- **Completion**: Unaffected nodes complete bottom-up after all failures resolve.

### Managing custom scenarios

- Custom scenarios appear under the **"Custom"** category in the sidebar.
- Click the **x** button next to a custom scenario to delete it.
- Custom scenarios are **persisted to localStorage** and survive page refreshes.

## Live Node Manipulation

During playback, you can interact with active nodes in real time:

1. **Right-click** any node on the canvas to open a context menu.
2. Available actions (enabled only when the node is in a valid state):
   - **Cancel Node**: Cancels the node and all its active descendants.
   - **Inject Exception**: Opens a prompt for an exception message, then propagates the exception upward using structured concurrency rules (supervisors absorb, regular jobs cascade).
   - **Force Complete**: Immediately transitions the node through `Completing` to `Completed`.
3. Injected events are spliced into the timeline, so **Step Backward** correctly reconstructs prior states.

A hint ("Right-click an active node to manipulate it") appears at the bottom-right of the canvas during playback.

## Verification

1. Start backend: `cd backend && ./gradlew run`
2. Start frontend: `cd frontend && npm run dev`
3. Open `http://localhost:5173`
4. Click "Happy Path" — tree animates through all states
5. Click "Child Exception" — exception bubbles up, siblings cancel
6. Click "Supervisor Job" — only failing child cancels
7. Click "Scope Comparison" — side-by-side trees show different behavior
8. Test controls: play/pause/step/reset/speed slider
9. Click nodes to see details in InfoPanel
10. Click "+ Create Scenario", build a 3-4 node tree, mark one as failing, click "Generate & Play"
11. Right-click an Active node during playback — try Cancel, Inject Exception, and Force Complete
12. Use Step Backward after a live manipulation to verify correct state reconstruction
13. Refresh the page — custom scenarios should still appear in the sidebar
