# Project Home Layout Redesign

## Overview

This spec redesigns the `/projects` home page so it reads more like a focused entry page and less like a full application shell.

The selected direction is:

- Hide the top header on the project home page only.
- Keep the rest of the project-space route tree unchanged.
- Preserve the centered hero and project card grid.
- Add a lightweight fixed settings button at the lower-left corner of the viewport.
- Keep the visual style flat and low-noise.

## Goals

- Remove the top navigation bar from the `/projects` home page.
- Let the project home section become the clear visual focus.
- Keep access to settings without consuming top-of-page space.
- Minimize impact on other project pages.
- Maintain the current card-based project entry workflow.

## Non-Goals

- No route restructuring.
- No store or API changes.
- No redesign of project detail pages.
- No redesign of settings pages.
- No global removal of the `ProjectLayout` header.

## Current State

The current project home page is rendered inside `ProjectLayout`, which always includes a shared top header with:

- Brand link
- `项目空间` link
- `设置` link

This creates a more application-like chrome than the project home page needs. Since the home page already acts as a landing surface for choosing a project, the persistent header competes with the main content.

## Proposed Design

### Scope

The header is hidden only when the current route is the project home page (`/projects`).

Other routes that use `ProjectLayout` continue to show the existing header, including:

- project detail pages
- project configuration pages
- other project-space related pages under the same layout

### Home Page Structure

The home page keeps its current conceptual structure:

1. Centered logo and subtitle
2. Project card grid
3. Add-project card as the first primary action

What changes is the surrounding chrome:

- The top header disappears on the home page.
- Vertical spacing is rebalanced so the hero does not feel too high or too low.
- The page feels cleaner and more intentional as an entry screen.

### Settings Access

A new fixed settings button is added at the lower-left corner of the viewport on the home page only.

This button should:

- Be visible but low emphasis
- Use a flat, lightweight treatment
- Link to `/settings`
- Stay outside the main card grid so it does not compete with the primary project actions

Recommended visual behavior:

- Default state is subtle
- Hover state slightly increases contrast
- Corners are rounded, but not oversized
- No heavy floating panel treatment

## Component Responsibilities

### `ProjectLayout.vue`

`ProjectLayout.vue` remains the shared route shell, but it becomes responsible for conditionally rendering the top header.

It should:

- Detect whether the active route is the home page
- Hide the header only for that route
- Continue rendering the shared header everywhere else

This keeps the behavior localized to layout presentation without creating a second layout just for the home page.

### `ProjectSpacesView.vue`

`ProjectSpacesView.vue` remains the owner of the home page content.

It should:

- Render the centered logo and subtitle
- Render the add-project card and project list
- Render the fixed settings entry used only on the home page

The settings button belongs here because it is part of the home page experience, not a shared global control for the whole project layout.

## Interaction Details

### Home Page

- Top header is not visible
- Primary attention goes to choosing or creating a project
- Lower-left `设置` button remains available as a secondary path

### Other Project Pages

- Existing top header remains unchanged
- Existing navigation paths continue to work
- No new button is introduced unless later explicitly requested

### Small Screens

- The fixed settings button should not block the first project card or overlap important content
- The home page should preserve enough bottom padding so the button has breathing room

## Styling Direction

The styling should remain consistent with the recent flat adjustments:

- Minimal shadows
- Clear spacing
- Soft borders
- Lower-noise controls

The lower-left settings button should feel like a utility control, not a call-to-action louder than the project cards.

## Implementation Plan

### Layout Changes

In `frontend/web/src/layouts/ProjectLayout.vue`:

- Read the current route
- Compute whether the active route is the project home page
- Render the `project-topbar` only when the route is not the home page

### Home View Changes

In `frontend/web/src/views/ProjectSpacesView.vue`:

- Add a fixed settings button linked to `/settings`
- Keep the existing project home content structure unchanged aside from spacing adjustments

### Style Changes

In `frontend/web/src/style.css`:

- Adjust `.project-layout` or `.project-home-page` spacing for the headerless home page
- Add styles for the fixed lower-left settings button
- Ensure mobile-safe spacing around the button

## Testing and Verification

Manual verification is sufficient for this UI adjustment:

- `/projects` hides the top header
- `/projects` shows the fixed lower-left settings button
- Clicking the button navigates to `/settings`
- Other routes under `ProjectLayout` still show the top header
- Home page spacing still looks balanced on desktop and smaller widths

Automated tests are not required for this focused layout change unless implementation introduces route-logic complexity that merits coverage.

## Risks

- Hiding the header only on one route can create a visual jump when navigating between project pages
- A fixed lower-left button can overlap content if bottom spacing is too tight
- If the button is too subtle, users may miss the settings path

These risks are mitigated by keeping the change scoped to the landing page, reserving enough bottom padding, and using a visible but restrained button treatment.

## Acceptance Criteria

- The `/projects` home page no longer displays the top header
- Other pages under `ProjectLayout` still display the top header
- The home page includes a lower-left settings button
- The settings button links to `/settings`
- The main project selection section remains the primary focus of the page
- The page styling stays flat and visually lightweight
