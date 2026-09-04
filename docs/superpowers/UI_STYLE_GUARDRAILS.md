# Weatherloom UI Style Guardrails

These guardrails apply to the production-readiness program increment and later UI work unless explicitly overridden by the owner.

## Canonical visual reference

The owner-provided Weatherloom screen set is the **authoritative visual target** for the application. The approved references now include full-size mobile examples of the core game states plus the tactile weather-front emblem.

### 1. Terrarium Home

The approved home state uses a large glass-cloche miniature world as the visual centerpiece. The terrarium is warm, photographic/macro-miniature in depth, filled with soft felt/clay/botanical detail, and occupies most of the screen. Two compact progress chips sit over the lower edge of the terrarium. Below it, a warm cream content surface contains:

- a large friendly status headline,
- a small botanical/collectible update line,
- one prominent coral primary action,
- compact specimen cards,
- and a quiet four-item bottom navigation.

The page should feel like a collectible physical terrarium sitting inside a calm handcrafted app, not a dashboard.

### 2. Draw the Front

The approved puzzle-drawing state uses a cream app shell around a richly textured miniature landscape. The world remains the hero. Its key traits are:

- compact objective pills near the title,
- a large rounded diorama/image container,
- tactile warm/cold/weather ribbons that look stitched or sewn onto the landscape,
- small weather-thread inventory chips below the board,
- restrained Undo/Clear/Reset controls,
- and one saturated coral **Simulate** action.

The weather ribbons must read as physical textile objects laid across the miniature world, not generic map lines, SVG paths, or neon overlays.

### 3. The Loom Runs / Playback

The approved simulation state becomes darker and more cinematic while preserving the same miniature-world language. The scene uses dramatic weather, felt-like clouds, visible rain/runoff, a large tactile wind/weather ribbon, and natural depth/lighting.

Supporting UI remains compact and secondary:

- event chips immediately below the world,
- a large rounded cream playback sheet,
- beat count and timeline/scrubber,
- three large but visually soft playback controls,
- and one compact objective/status card.

The darker atmosphere belongs to the simulated world, not to a new dark-theme UI system.

### 4. The Weather Settles / Result

The approved result state retains the atmospheric scene behind a large rounded cream sheet. The sheet contains, in order:

- a narrow scenic/banner illustration,
- a strong result headline,
- a softer secondary success line,
- Seedling / Bloom / Flourish rating medallions with the earned rating emphasized,
- a causal explanation card with an illustrated weather icon,
- a collectible/reward card with a botanical specimen,
- one coral **Next level** action,
- and one neutral/outlined **Replay** action.

The result sheet should feel like a physical cream card/paper object pulled over the miniature scene, not a generic modal dialog.

### 5. Weatherloom emblem / brand art

The owner-provided weather-front emblem is also a canonical style reference. It uses:

- warm coral felt for the warm front,
- cool blue felt for the cold front,
- a soft ivory wool cloud,
- stitched seams and raised textile edges,
- teal rain stitches,
- and a warm cream felt/paper background.

Brand marks, tutorial weather symbols, launch/splash treatment, empty states, and future decorative weather art should inherit this **handmade textile construction**, not switch to flat vector weather icons.

### What is *not* part of the app reference

Some owner screenshots were captured inside a mobile browser. Browser address bars, browser status/navigation controls, and surrounding black browser chrome are **not** Weatherloom UI and must not be reproduced in the native Android app. Only the Weatherloom viewport/content inside those screenshots is authoritative.

If a later mockup, generic component library, framework default, generated design, or implementation convenience conflicts with these references, **the approved Weatherloom references win** unless the owner explicitly approves a change.

## Preserve the existing visual identity

Do **not** redesign Weatherloom into a generic Material 3 app. The handcrafted miniature-diorama presentation remains authoritative.

Preserve:
- **Handcrafted miniature-world aesthetic:** felt, wool, embroidered, botanical, clay/wood/stone-like tactile surfaces and macro miniature depth rather than flat vector-only scenery.
- **Rich world imagery as the hero:** the terrarium and weather diorama should occupy most of the visual attention; application chrome supports the world instead of competing with it.
- **Warm cream/parchment UI surfaces** surrounding or overlaying the world.
- **Deep natural ink/forest text** rather than stark black wherever existing contrast allows.
- **Coral/orange-red as the principal action accent**, especially for primary progression/simulation actions.
- **Muted natural weather accents:** warm orange/red, cold blue, wind cream/gold, moisture teal/green, plus lavender/botanical accents where already established.
- **Soft dimensionality:** rounded cards, sheets, chips and controls with gentle borders/shadows rather than hard rectangular panels.
- **Nunito typography** and the existing rounded, friendly hierarchy.
- **Compact pill/chip language** for objectives, weather tools, status, streaks, events, and progress.
- **Large rounded landscape/image containers** with tight integration between artwork and controls.
- **Bottom navigation that remains visually quiet** and secondary to the terrarium/world.
- Existing navigation structure and labels unless a usability requirement forces a narrowly scoped change.
- Existing board art, terrarium art, ribbons/thread visuals, and botanical specimen style.
- Existing tone of copy: calm, handmade, clear, causal, and non-technical.
- Existing motion character where motion is allowed.

## Composition rules visible in the approved reference

- Prefer **one dominant world/art region** per screen instead of many equally weighted panels.
- Keep information density compact through small rounded chips and short supporting copy.
- Use **cream sheets/cards over atmospheric or scenic backgrounds** for results, playback information, settings, and explanations.
- Keep primary actions visually obvious but limited; do not turn every action into a saturated button.
- Secondary actions should generally remain neutral/outlined/soft-surface treatments.
- Weather ribbons should read as tactile physical threads laid across the miniature world, not generic map polylines.
- Collectibles should read as botanical specimens/miniatures, not generic achievement icons.
- Simulation playback may become darker and more cinematic, but control surfaces should remain recognizably Weatherloom.
- Accessibility additions should be visually absorbed into existing cards/chips/sheets rather than added as foreign-looking system panels.
- Maintain the strong vertical hierarchy seen in the approved mobile reference: world first, concise status second, actions/supporting content third.
- Preserve generous breathing room around the hero art even when layouts become adaptive.

## Accessibility changes must extend the style, not replace it

Examples:
- High Contrast should be a Weatherloom high-contrast variant using the same semantic palette families, not black/white generic system styling unless required for a specific accessibility mode.
- 48dp touch-target fixes should expand hit areas without visually inflating every control.
- TalkBack semantics should add accessible meaning without altering visible layout unless necessary.
- Reduced Motion should remove or simplify motion while preserving the same static visual composition.
- Adaptive layouts should reflow the same components and visual language across widths, not introduce a different tablet visual system.
- The accessible non-drag puzzle interaction should reuse existing thread, board, card, chip, and control motifs.
- Additional settings should look like Weatherloom surfaces and controls, not a generic Android Settings screen.

## Figma rule

Figma concepts are implementation references for accessibility/adaptive behavior only. They must visually match the approved Weatherloom design references. Do not import Material 3's visual appearance wholesale. Material components may be consulted for behavior, semantics, interaction sizing, and accessibility, but Weatherloom's own shapes, palette, typography, spacing, imagery, textile construction, and tactile treatment remain the visual source of truth.

## Regression rule

When screenshot/regression tests are added, representative golden/reference screens should cover at least:
- Terrarium Home
- Draw the Front
- The Loom Runs / playback
- The Weather Settles / result sheet
- Almanac/Comfort once accessibility work is added
- Weatherloom emblem/brand treatment where applicable

A feature may change content or add a required control while still preserving the established composition, palette relationships, tactile imagery, typography, and action hierarchy.

## Review gate

Any change that noticeably alters the established Weatherloom style, replaces a hero miniature scene with generic UI, changes the principal palette/action hierarchy, flattens the tactile textile treatment, or introduces a new visual system requires explicit owner approval before implementation.
