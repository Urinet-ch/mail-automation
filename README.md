# mail-automation

Automatische Triage einer Gmail-Inbox mit einem **lokal laufenden LLM** — kein Mail-Inhalt
verlässt das eigene Netz Richtung KI-Anbieter.

Alle 10 Minuten werden neue Mails geholt, klassifiziert (Kategorie + **Aktion** + Frist) und
in Gmail unter `AI/…`-Labels sichtbar gemacht. Täglich um 07:00 kommt ein Digest als Mail an
das eigene Konto: „Was muss ich tun" zuerst, sortiert nach Frist.

```
                       ┌─────────────────────────────┐
   @Scheduled 10min ──▶│  TriageService              │
   REST /triage/run ──▶│  1. Vorfilter (determin.)   │
   @Scheduled 07:00 ──▶│  2. LLM nur fuer den Rest   │──▶ Digest an sich selbst
                       └──┬──────────┬──────────┬────┘
                          ▼          ▼          ▼
                    MailboxPort ClassifierPort TriageStorePort
                          │          │          │
                     Gmail IMAP  LM Studio   PostgreSQL
                     (lesen +    (Gemma 4,   (Audit-Log,
                      labeln)     lokal)      Pending-Queue)
```

## Grundsätze

1. **Kein Cloud-LLM.** Klassifiziert wird ausschliesslich über die OpenAI-kompatible API
   eines lokalen LM-Studio-Servers (getestet mit Gemma 4 26B A4B auf Apple Silicon).
2. **Das Modell hat keine Tools.** Es bekommt eine Mail, es liefert ein JSON — mehr nicht.
   Was mit der Mail passiert, entscheidet deterministischer Java-Code. Prompt Injection
   aus Mail-Inhalten läuft dadurch ins Leere: der Mailtext ist markiert als nicht
   vertrauenswürdige Daten, und selbst ein „überzeugtes" Modell kann nichts aufrufen.
3. **Nur reversible Aktionen.** Labeln unter dem `AI/`-Präfix. Kein Senden (ausser Digest
   an die eigene Adresse), kein Löschen, kein Archivieren, kein Weiterleiten — im Code
   nicht vorhanden, nicht nur abgeschaltet.
4. **Vorfilter vor Modell.** Selbst-Mails (= Erinnerungen → `Aktion-Erledigen`),
   `List-Unsubscribe`-Massenversand und konfigurierte Absenderlisten werden ohne
   Modell entschieden — schneller, und ein Fehler ist konstruktiv unmöglich.
5. **Alles auditiert.** Jede Entscheidung landet in Postgres: wer, was, welche Quelle
   (Vorfilter/Modell), welche Confidence, welches Modell. Fehlklassifikationen lassen
   sich damit auswerten statt nur beklagen.
6. **Schlafender Mac ist Normalbetrieb.** Ist LM Studio nicht erreichbar, bleiben Mails
   in der Pending-Queue und werden nachgeholt. Kein Datenverlust, nur Latenz — sichtbar
   über die Metrik `mail_triage_pending`.

## Klassifikation

| Feld | Werte |
|---|---|
| `kategorie` | Rechnung, Arbeit, Persoenlich, Behoerde, Newsletter, Werbung, Benachrichtigung, Unsicher |
| `aktion` | **Antworten**, **Bezahlen**, **Erledigen**, **Termin**, Keine |
| `frist` | Datum aus der Mail (Zahlungsziel, Anmeldeschluss …) oder leer |
| `confidence` | 0–1; unter der Schwelle (default 0.7) wird nur `AI/Unsicher` gesetzt |

Gesetzte Labels: `AI/<Kategorie>` und bei Handlungsbedarf zusätzlich `AI/Aktion-<Aktion>`.
Tipp: Gmail-Suche `label:ai-aktion-bezahlen OR label:ai-aktion-antworten OR
label:ai-aktion-erledigen OR label:ai-aktion-termin` als Lesezeichen = To-do-Sicht.

## Voraussetzungen

- Gmail-Konto mit 2FA und App-Passwort (Konto → Sicherheit → App-Passwörter)
- LM Studio mit geladenem Modell, als Server erreichbar: `lms server start`
  — vom Cluster aus erreichbar über LAN-IP oder Tailscale
- PostgreSQL (eigene Datenbank, z. B. `mailtriage`)
- JDK 21+, Maven (Build), Kubernetes optional

## Lokal starten

```bash
export GMAIL_USER=me@gmail.com
export GMAIL_APP_PASSWORD=xxxxxxxxxxxxxxxx
export LMSTUDIO_URL=http://localhost:1234
mvn quarkus:dev   # startet ein Dev-Postgres via Docker automatisch
```

Manuell auslösen: `curl -X POST localhost:8080/triage/run` ·
Status: `curl localhost:8080/triage/status` ·
Digest testen: `curl -X POST localhost:8080/triage/digest`

## Deployment (k3s)

1. CI baut das Image nach `ghcr.io/<owner>/mail_automation` (siehe `.github/workflows/build.yml`).
2. `deploy/secret.example.yaml` kopieren, echte Werte eintragen, als Secret anlegen
   (oder SOPS/age-verschlüsselt ins Infra-Repo).
3. In `deploy/kustomization.yaml` Owner und Image-Tag pinnen, `LMSTUDIO_URL` in
   `deployment.yaml` auf die Adresse des Inferenz-Rechners setzen.
4. `kubectl apply -k deploy/`

Metriken liegen auf `/q/metrics` (Prometheus), Health auf `/q/health`. Sinnvoller Alarm:
`mail_triage_pending > 0` für länger als 24 h (Inferenz-Rechner dauerhaft weg).

## Konfiguration

| Env | Default | Bedeutung |
|---|---|---|
| `GMAIL_USER` | — | Gmail-Adresse (Pflicht) |
| `GMAIL_APP_PASSWORD` | — | App-Passwort (Pflicht) |
| `LMSTUDIO_URL` | `http://localhost:1234` | LM-Studio-Server |
| `LMSTUDIO_MODEL` | `google/gemma-4-26b-a4b` | Modellname wie in `lms ps` |
| `TRIAGE_POLL_INTERVAL` | `10m` | Poll-Intervall |
| `DIGEST_CRON` | `0 0 7 * * ?` | Digest-Zeitpunkt (Quartz, TZ des Containers) |
| `TRIAGE_WERBUNG_ABSENDER` | leer | Absender-Muster → Werbung ohne Modell |
| `TRIAGE_BENACHRICHTIGUNG_ABSENDER` | leer | Absender-Muster → Benachrichtigung ohne Modell |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | — | PostgreSQL |

## Entstehung

Etappe 1 des Projekts „Lokale Mail-Triage": Die Klassifikations-Taxonomie (insbesondere
die Aktion-Achse statt eines binären „wichtig") wurde vorab interaktiv mit LM Studio
Bionic und einem eigenen Gmail-MCP-Server an der echten Inbox validiert.
