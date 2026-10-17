# Developer docs

Diagrams and walk-throughs for people working on the code. They are [Mermaid](https://mermaid.js.org/), which GitHub draws in the browser, so they live next to the code and change in the same pull request as it. They are developer docs: they are not part of the in-app docs site.

| Page | What it shows |
|------|---------------|
| [Components](components.md) | The parts of the one Spring Boot process and what talks to what |
| [Sequences](sequences.md) | A lap passing from decoder to browser, the race lifecycle and finish, results export, live feed, RaceHub entry import, sign-in |
| [Race lifecycle](race-lifecycle.md) | The race state machine and the round types around it |
| [Domain model](domain-model.md) | Class diagrams of the core domain and an entity relationship diagram of the tables |

The reference for the code itself is the Javadoc (tracker #346). The prose is in [architecture](../architecture.md) and [development](../development.md).

## Keeping them true

- Change a diagram in the same pull request as the code it describes.
- Name real classes and methods, so a search finds both.
- Draw the mechanism, not every class: a diagram that lists everything shows nothing.
