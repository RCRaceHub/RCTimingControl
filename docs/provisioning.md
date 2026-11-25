# Skipping the setup wizard

A new install normally opens the setup wizard, which asks for the first official, the club and the decoder's address. A club that rebuilds its laptop often, or runs the Docker image, can give those answers in advance and skip the wizard.

A complete, annotated example to copy is `docs/examples/provisioning.example.properties` in the source, and carries the same settings with comments.

Put these in the settings file (`application.properties` in the data folder, see [installing.md](installing.md)), or set the matching environment variables:

```properties
rctiming.provisioning.admin.email=admin@example.club
rctiming.provisioning.admin.first-name=Ann
rctiming.provisioning.admin.last-name=Admin
# At least 8 characters. Better as an environment variable than in a file (see below)
rctiming.provisioning.admin.password=

rctiming.provisioning.club.name=Wyvern RC Club
rctiming.provisioning.club.timezone=Europe/London
rctiming.provisioning.club.email=info@example.club
rctiming.provisioning.club.phone=
rctiming.provisioning.club.website-url=

rctiming.provisioning.decoder.host=192.168.1.20
rctiming.provisioning.decoder.port=5100
rctiming.provisioning.decoder.protocol=RC4
```

Setting `admin.email` switches it on, and the password and both names are then needed. The club and decoder blocks are optional: leave `club.name` out to do the club step in the wizard afterwards. A decoder address needs a club name, because the address is kept in the club's details. `club.timezone` defaults to `UTC`, `decoder.port` to `5100` and `decoder.protocol` to `RC4`.

## When it applies

Only on a **first start**: while the database has no accounts. Once an official exists the settings are ignored (the log says so), so editing the file later never changes a club that is already set up, and a restart never overwrites what officials have changed in the app. To start again from the defaults, stop the app, move the database file out of the data folder (or use a new data folder, or `docker compose down -v`) and start it again.

The settings are applied before the app starts accepting connections, so nobody on the network can complete the wizard first. If the settings are incomplete or invalid, the app stops with a message naming the setting, rather than starting with an open wizard.

Everything is done in one step and recorded in the audit log as the system. Track, race formats, classes and more officials are still added in the app, or by an RaceHub import. The RaceHub key is set with `rctiming.racehub.token`.

A step-by-step version with pictures is [walkthrough 10](walkthroughs/10-config-file.md).

## Environment variables and Docker

Each setting is also an environment variable: upper-case it and replace the dots and dashes with underscores, so `rctiming.provisioning.admin.email` is `RCTIMING_PROVISIONING_ADMIN_EMAIL`. In Docker Compose:

```yaml
services:
  rctiming:
    environment:
      RCTIMING_PROVISIONING_ADMIN_EMAIL: admin@example.club
      RCTIMING_PROVISIONING_ADMIN_FIRST_NAME: Ann
      RCTIMING_PROVISIONING_ADMIN_LAST_NAME: Admin
      RCTIMING_PROVISIONING_ADMIN_PASSWORD: ${RCTIMING_ADMIN_PASSWORD}   # from a .env file not kept in git
      RCTIMING_PROVISIONING_CLUB_NAME: Wyvern RC Club
      RCTIMING_PROVISIONING_CLUB_TIMEZONE: Europe/London
      RCTIMING_PROVISIONING_DECODER_HOST: 192.168.1.20
```

The password is only used to create the account. After the first start, remove it from the file or environment; the admin can change it on their own page.

To use a settings file in a container instead, mount it as `/data/application.properties` and add `SPRING_CONFIG_ADDITIONAL_LOCATION: optional:file:/data/` to the environment: only the installed service looks in the data folder by itself. Running the jar by hand needs the same, as `--spring.config.additional-location=optional:file:/path/to/folder/`.
