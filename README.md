# TechPath

TechPath is an Android app for university technology students. It was built for a Strathmore University
research project (BICS, ICS 3A). A student picks a career path and gets a staged roadmap of skills to
tick off. Each skill has an explanation and real learning links. The app also lists internships,
attachments, entry-level jobs and hackathons, and bundles career-prep guides.

The research question is whether an **AI-personalised** roadmap works better than a **static** one. A
study mode setting switches between `AI` and `STATIC`. Both modes look the same. Only the ordering and
the explanations differ.

## Method (RAG) in five lines

1. **Retrieve on-device:** load the chosen path's nodes from the dataset. Nodes whose titles match skills the student already knows are marked done. If more than 120 nodes remain, keep the 120 that best overlap the path's core topics.
2. **STATIC:** use dataset file order, split into stages of about 8 skills.
3. **AI:** send the year, the path, the known skills and the retrieved `{id, title}` pairs to Gemini, which returns 4–6 stages as JSON.
4. **Validate:** drop unknown IDs and duplicates, and append any skills the model left out. Invalid JSON gets one repair retry. If that fails, or there is no key or no network, the app falls back to STATIC.
5. **Explain:** in AI mode, a fixed prompt template (the same one used in the fine-tuning set) is grounded in the dataset description. Links always come from the dataset, never from the model.

## Build and run

```bash
./gradlew assembleDebug        # works without an API key (STATIC mode)
./gradlew testDebugUnitTest    # JVM unit tests
python3 -m unittest discover tools
```

### Adding an API key

Copy `local.properties.example` to `local.properties` (it is git-ignored) and set:

```
GEMINI_API_KEY=your-key-from-aistudio.google.com
```

Or enter a key in the app under **Settings → Gemini API key**, which overrides the built-in one. Never
commit a key. The model name is `GeminiClient.MODEL`.

### Regenerating the dataset assets

The assets in `app/src/main/assets/paths*` are generated from
[roadmapsh-datasets](https://github.com/Tums2060/roadmapsh-datasets) (`out/` folder) and committed:

```bash
python3 tools/build_assets.py /path/to/roadmapsh-datasets
```

## Folder map

```
app/src/main/
├── assets/
│   ├── paths.json             all career paths (generated)
│   ├── paths/<path>.json      nodes + resolved links, one file per path (generated)
│   ├── articles/*.md          Hub guides
│   └── opportunities.json     sample opportunities (fictional)
└── java/com/techpath/
    ├── MainActivity.kt, TechPathApp.kt, AppContainer.kt   (hand-written dependency container)
    ├── data/       DatasetRepository (assets), UserRepository (DataStore), models
    ├── ai/         AiClient, GeminiClient, Prompts, RoadmapBuilder
    └── ui/
        ├── theme/        Color, Type, Shape, Theme (+ Dimens): all styling
        ├── components/   shared composables (SkillRow, StageHeader, ProgressBar, LinkItem, states)
        ├── navigation/   NavGraph, bottom bar, list of tabs
        └── onboarding/ roadmap/ skill/ opportunities/ hub/ settings/   (Screen + ViewModel each)
tools/build_assets.py           builds the path assets from the dataset CSVs
```

## How to restyle

Edit only `ui/theme/`. Colours are in `Color.kt` (light and dark schemes), fonts in `Type.kt`, corner
radii in `Shape.kt`, and spacing and sizes in `Dimens` (`Theme.kt`). Screens use only `MaterialTheme`
tokens and `Dimens`. All text is in `res/values/strings.xml`.

## How to add a screen

1. Create `ui/<name>/` with a `<Name>ViewModel` (one `<Name>UiState` data class, an event interface and a
   `Factory` that reads from `appContainer()`) and a `<Name>Screen.kt` containing a stateful `<Name>Route` and a
   stateless `<Name>Screen(state, onEvent)` with a `@Preview`.
2. Add its strings to `strings.xml`.
3. Register it in `ui/navigation/NavGraph.kt`. For a tab, add one `TopLevelDestination` to
   `TOP_LEVEL_DESTINATIONS`. For another screen, add one `composable(...)` entry.

## Extending the AI

`AiClient` is the only model interface. To use the team's fine-tuned model (for example an
OpenAI-compatible endpoint), write a second implementation and swap it in `AppContainer`. All prompt text
is in `ai/Prompts.kt`.
