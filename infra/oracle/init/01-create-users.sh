#!/bin/bash
# Runs once, the first time the Oracle container starts with an empty data volume
# (gvenzl/oracle-xe runs every script in /container-entrypoint-initdb.d).
# Creates one database user per service; passwords come from the environment (.env).

sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = ${ORACLE_DATABASE};

CREATE USER keycloakService IDENTIFIED BY "${KEYCLOAK_DB_PASSWORD}" QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE, CREATE VIEW TO keycloakService;

CREATE USER authService IDENTIFIED BY "${AUTH_DB_PASSWORD}" QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE TO authService;

CREATE USER billingService IDENTIFIED BY "${BILLING_DB_PASSWORD}" QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE TO billingService;
EXIT
SQL
