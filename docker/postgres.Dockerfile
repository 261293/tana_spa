# PostgreSQL for local development.
#
# Why not plain `postgres:17`: that image ships Debian's tzdata without the `tzdata-legacy`
# package, so Postgres (built --with-system-tzdata) does not know the backward-compatibility
# zone names -- Asia/Saigon, US/Pacific, Europe/Kiev and friends.
#
# A JDBC client sends its JVM default zone in the startup packet, and the JDK still maps the
# Windows zone "SE Asia Standard Time" to the legacy id Asia/Saigon, which the stock image
# rejects outright:
#
#   FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
#
# That kills the connection before any query runs, from DataGrip/DBeaver as well as from the
# server when it is started with Gradle on the host. Installing tzdata-legacy restores the
# aliases so any client zone id resolves.
FROM postgres:17
RUN apt-get update && apt-get install -y --no-install-recommends tzdata-legacy && rm -rf /var/lib/apt/lists/*
