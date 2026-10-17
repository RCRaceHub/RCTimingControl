# Developer docs

Diagrams and walk-throughs for people working on the code. They are [Mermaid](https://mermaid.js.org/), which GitHub draws in the browser, so they live next to the code and change in the same pull request as it. They are developer docs: they are not part of the in-app docs site.

| Page | What it shows |
|------|---------------|
| [Components](components.md) | The parts of the one Spring Boot process and what talks to what |
| [Sequences](sequences.md) | A lap passing from decoder to browser, the race lifecycle and finish, results export, live feed, RaceHub entry import, sign-in |
| [Race lifecycle](race-lifecycle.md) | The race state machine and the round types around it |
| [Domain model](domain-model.md) | Class diagrams of the core domain and an entity relationship diagram of the tables |

The reference for the code itself is the Javadoc (tracker #346); see [Javadoc](#javadoc) below. The prose is in [architecture](../architecture.md) and [development](../development.md).

## Javadoc

- Build it with `./gradlew :app:javadoc -x generateJooq`; it lands in `app/build/docs/javadoc`. The generated jOOQ code is left out.
- The build uses doclint, so a malformed comment or a broken `{@link}` fails it. A GitHub workflow (`javadoc.yml`) runs this on pushes and pull requests that touch the app's code or its build file, and on `main` keeps the result as a downloadable build artifact named `javadoc`. On a pull request it also fails when a class the PR adds or changes has no class comment (`.github/scripts/check-class-javadoc.py`); untouched classes are not checked.
- To read it in the app, build the jar with `-PbundleJavadoc` and set `rctiming.docs.javadoc.enabled=true`; it is then served at `/javadoc`, signed in or not. The flag is off by default and on in the demo. The Docker image is always built with the Javadoc inside, so it ships in every image, but nothing serves it unless the flag is on. Leave it off on a club install: the pages are public to the whole venue network and show the code's structure.

## Keeping them true

- Change a diagram in the same pull request as the code it describes.
- Name real classes and methods, so a search finds both.
- Draw the mechanism, not every class: a diagram that lists everything shows nothing.
