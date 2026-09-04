# Weatherloom UI Style Guardrails

These guardrails apply to the production-readiness program increment and later UI work unless explicitly overridden by the owner.

## Preserve the existing visual identity

Do **not** redesign Weatherloom into a generic Material 3 app. The current felt/wool handcrafted style remains authoritative.

Preserve:
- Felt/wool handcrafted aesthetic
- Existing cozy natural palette and color relationships
- Nunito typography and existing type hierarchy
- Current spacing rhythm and rounded-surface language
- Existing navigation structure and labels unless a usability requirement forces a narrowly scoped change
- Existing board art, terrarium art, ribbons/thread visuals, and botanical specimen style
- Existing tone of copy: calm, handmade, clear, and non-technical
- Existing motion character where motion is allowed

## Accessibility changes must extend the style, not replace it

Examples:
- High Contrast should be a Weatherloom high-contrast variant using the same semantic palette families, not black/white generic system styling unless required for a specific accessibility mode.
- 48dp touch-target fixes should expand hit areas without visually inflating every control.
- TalkBack semantics should add accessible meaning without altering visible layout unless necessary.
- Reduced Motion should remove or simplify motion while preserving the same static visual composition.
- Adaptive layouts should reflow the same components and visual language across widths, not introduce a different tablet visual system.
- The accessible non-drag puzzle interaction should reuse existing thread, board, card, and control motifs.

## Figma rule

Figma concepts are implementation references for accessibility/adaptive behavior only. They must visually match the current app style. Do not import generic Material 3 visual styling beyond using components as structural references where useful.

## Review gate

Any change that noticeably alters the established Weatherloom style requires explicit owner approval before implementation.
