# Weatherloom UI Style Guardrails

These guardrails apply to the production-readiness program increment and later UI work unless explicitly overridden by the owner.

## Canonical visual reference

The owner-provided four-screen Weatherloom design reference is the **authoritative visual target** for the application. It shows the intended look of:

1. **Terrarium Home** — a large glass-cloche miniature world as the visual centerpiece, collectible/progress chips overlaid near the lower edge, a warm cream content panel, coral primary action, specimen cards, and quiet bottom navigation.
2. **Draw the Front** — a cream app shell around a richly textured miniature landscape, compact objective pills, colorful weather-thread chips, thick tactile weather ribbons laid over the diorama, restrained utility controls, and a coral Simulate action.
3. **The Loom Runs** — the same miniature world under darker atmospheric weather, with the world still visually dominant, compact event chips, a scrub/timeline surface, playback controls, and muted supporting chrome.
4. **The Weather Settles** — atmospheric scene retained behind a large rounded cream result sheet, botanical/result illustration, restrained rating treatment, causal explanation cards, collectible reward presentation, coral primary progression action, and outlined secondary replay action.

If a later mockup, generic component library, framework default, or implementation convenience conflicts with this reference, **the approved Weatherloom reference wins** unless the owner explicitly approves a change.

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

Figma concepts are implementation references for accessibility/adaptive behavior only. They must visually match the approved Weatherloom design reference. Do not import Material 3's visual appearance wholesale. Material components may be consulted for behavior, semantics, interaction sizing, and accessibility, but Weatherloom's own shapes, palette, typography, spacing, imagery, and tactile treatment remain the visual source of truth.

## Regression rule

When screenshot/regression tests are added, representative golden/reference screens should cover at least:
- Terrarium Home
- Draw the Front
- The Loom Runs / playback
- The Weather Settles / result sheet
- Almanac/Comfort once accessibility work is added

A feature may change content or add a required control while still preserving the established composition, palette relationships, tactile imagery, typography, and action hierarchy.

## Review gate

Any change that noticeably alters the established Weatherloom style, replaces a hero miniature scene with generic UI, changes the principal palette/action hierarchy, or introduces a new visual system requires explicit owner approval before implementation.
