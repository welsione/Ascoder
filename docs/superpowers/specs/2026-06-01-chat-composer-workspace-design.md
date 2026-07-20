# Chat Composer Workspace Redesign

## Overview

This spec redesigns the chat composer area so the empty project-space state is no longer rendered outside the composer. The bottom input area becomes a single workspace panel that handles both the ready-to-ask state and the not-ready state.

The chosen direction is a flat, minimal workspace style:

- Keep a single composer panel as the visual container.
- Preserve the current top controls for project space and role.
- Move the empty-state guidance into the composer itself.
- Keep the textarea visible in all states, but disable it when no project space is available.
- Use a lighter, flatter presentation with reduced shadow, tighter radius, and fewer nested card treatments.

## Goals

- Merge the existing empty-state `div` into the composer `section`.
- Make the composer feel like a unified workbench instead of separate stacked blocks.
- Keep the input area visually primary even when the user cannot submit yet.
- Make the no-project-space state understandable without forcing users to inspect another part of the page.
- Minimize component sprawl and keep the state ownership clear.

## Non-Goals

- No change to question submission logic.
- No new store, API, or route behavior.
- No redesign of the chat thread or welcome area.
- No animation-heavy or marketing-style visuals.

## Current State

The current implementation splits responsibilities across two places:

- `frontend/web/src/views/ChatView.vue` renders the external empty-state block when `projectSpaceId` is missing.
- `frontend/web/src/components/chat/ChatComposer.vue` renders only the toolbar, textarea, and send action.

This causes two UX issues:

- The composer and the empty-state message do not read as one flow.
- The empty-state guidance competes with the composer rather than explaining it.

## Proposed Design

### Layout

The composer becomes a unified workspace panel with four vertical zones:

1. Context controls
2. Minimal status text area
3. Input shell
4. Action row

The empty-state experience stays inside this same panel rather than appearing as an external dashed block.

### Visual Style

The visual treatment should be flatter than the current version:

- Outer panel keeps a white surface but uses weaker shadow.
- Corners remain rounded but slightly tighter.
- Internal hierarchy relies mostly on spacing, thin borders, and subtle background differences.
- Avoid card-within-card styling for the empty state.
- The status area reads as inline workspace guidance, not as a separate promotional panel.

### Empty State

When no project space is selected or available:

- The project-space selector remains visible at the top.
- The textarea remains visible but is disabled.
- The send button is disabled.
- A minimal guidance line appears between the selectors and input area.
- The `Open Settings` action appears near the action row so the next step stays close to the primary controls.

Recommended copy shape:

- Guidance text: "还没有可用项目空间，请先在设置里创建项目空间、准备代码并完成 CodeGraph 索引。"
- Action: `打开设置`

### Ready State

When a valid project space is selected:

- The guidance line switches to a compact context summary.
- The textarea becomes interactive.
- The send button behaves as it does today.
- The settings action is hidden.

The ready-state summary remains intentionally lightweight so the input area stays dominant.

## Component Responsibilities

### `ChatView.vue`

`ChatView.vue` should only orchestrate the main chat content:

- Show welcome content when there is no active question.
- Show thread content when there is an active question.
- Always render the composer at the bottom.

It should no longer render a separate empty-state block for missing project spaces.

### `ChatComposer.vue`

`ChatComposer.vue` becomes the single owner of the bottom workspace presentation:

- Resolve whether the composer is in empty or ready state from existing store data.
- Render the minimal status text area.
- Disable or enable the textarea accordingly.
- Disable or enable the send button accordingly.
- Render the settings link when the composer is not ready.
- Keep existing error alert placement at the bottom of the composer.

No new global state is introduced. The component continues using existing values from `questionStore` and `projectSpaceStore`.

## Interaction Details

### No Project Space Available

- User can still see the expected workflow: select context, type, send.
- The textarea is disabled so the interface clearly communicates the blocked state.
- The action row shows why submission is unavailable and provides a direct route to settings.

### Project Space Selected

- The composer returns to its normal interaction mode without layout shift beyond the status text change.
- The action row becomes compact again with context text and the send button.

### Error Handling

- Existing `questionStore.error` behavior remains unchanged.
- Error alert stays inside the composer and below the main action row.
- Empty-state guidance must not suppress backend or submission errors.

## Data and State Flow

The redesign keeps the current state flow:

- `readySpaces` still derives from `projectSpaceStore.spaces`.
- `selectedSpace` still derives from `questionStore.form.projectSpaceId`.
- `syncProjectSpaceSelection()` still auto-selects a valid ready space when possible.
- Member fetching behavior remains unchanged.

The main new derived state inside the composer is conceptual:

- `hasAvailableProjectSpace`: whether the UI can enter a usable asking state.
- `isComposerReady`: whether the textarea and send action should be enabled.

This can be implemented from existing computed values without adding new store properties.

## Implementation Plan

### Template Changes

In `ChatView.vue`:

- Remove the external `empty-box empty-box-wide` block.

In `ChatComposer.vue`:

- Add a minimal status area between toolbar and input shell.
- Add a conditional settings action in the empty state.
- Bind textarea disabled state to composer readiness.
- Bind send button disabled state to composer readiness in addition to loading state.

### Styling Changes

In `frontend/web/src/style.css`:

- Reduce visual weight of `.composer-shell`.
- Keep `.composer-input-shell` as the main focus area.
- Add a flat status style for the empty or ready state.
- Add styling for a compact auxiliary action near the send area.
- Add disabled-state styling that stays readable and does not over-gray the panel.

The styling should reuse existing tokens and patterns where possible instead of creating a large new style subsystem.

## Testing and Verification

Primary verification is manual UI inspection:

- No project spaces available: composer shows inline guidance, disabled textarea, disabled send button, and settings action.
- Ready project space available: composer shows compact context summary, enabled textarea, and enabled send button.
- Switching selected project space updates the summary correctly.
- Error alert still renders correctly after a failed submit.
- Layout remains readable on narrower widths already supported by the page.

Automated testing is not required for this UI-only adjustment unless implementation reveals state logic that benefits from targeted coverage.

## Risks

- If the empty-state copy is too subtle, users may miss why the composer is disabled.
- If the disabled-state styling is too strong, the panel may look broken rather than intentionally unavailable.
- If the action row grows too crowded, the flat design may lose clarity on smaller widths.

These risks are mitigated by keeping the guidance text short, placing the settings action close to the send control, and preserving a clear disabled visual state.

## Acceptance Criteria

- The external empty-state block is removed from `ChatView.vue`.
- The composer alone communicates both ready and not-ready states.
- The no-project-space experience keeps the textarea visible but disabled.
- The design is flatter and more minimal than the current card-heavy presentation.
- The settings action is available from inside the composer when the user cannot ask yet.
- Existing submit, selection, polling, and error behavior remain intact.
