# mail-automation

Automatische Triage einer Gmail-Inbox mit einem **lokal laufenden LLM** — kein Mail-Inhalt
verlässt das eigene Netz Richtung KI-Anbieter.

Alle 10 Minuten werden neue Mails geholt, klassifiziert (Kategorie + **Aktion** + Frist) und
in Gmail unter `AI/…`-Labels sichtbar gemacht. Täglich um 07:00 kommt ein Digest als Mail an
das eigene Konto: „Was muss ich tun" zuerst, sortiert nach Frist.

**Mehrere Postfächer** werden über eine kleine Weboberfläche unter `/ui` verwaltet — anlegen,
pausieren, löschen, ohne Deployment. Jedes Konto hat seinen eigenen Verarbeitungszustand,
seinen eigenen Digest und seinen eigenen Label-Namensraum.

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
7. **Konten sind getrennt.** Zustand, Audit-Log und Digest sind je Postfach eigen; ein
   Digest geht immer nur an das Konto, aus dem er stammt. Postfächer sehen einander nicht.
   Ein Konto, dessen Anmeldung scheitert, hält die anderen nicht auf.

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

## Kontoverwaltung (`/ui`)

Serverseitig gerenderte Oberfläche, bewusst schlicht — Liste der Postfächer mit Kennzahlen
(verarbeitet, wartend, letzter Lauf, aktuelle UID) und ein Formular zum Anlegen.

- **Beim Anlegen wird die Anmeldung geprüft.** Schlägt sie fehl, wird nichts gespeichert —
  sonst stünde ein totes Konto in der Liste und der Fehler erschiene erst Minuten später
  im Log.
- **App-Passwörter liegen AES-256-GCM-verschlüsselt in der Datenbank.** Der Schlüssel
  (`TRIAGE_CRYPTO_KEY`) kommt aus der Umgebung und nie aus der Datenbank: wer nur einen
  Dump hat — etwa aus dem Backup — kann die Passwörter nicht lesen. Erzeugen mit
  `openssl rand -base64 32`.
- **Die Adresse eines Kontos ist unveränderlich.** An ihr hängt der gesamte
  Verarbeitungszustand; ein anderes Postfach ist ein neues Konto.
- **Löschen entfernt Konto und Verlauf**, nicht aber die in Gmail gesetzten Labels — die
  Anwendung nimmt grundsätzlich nichts aus einem Postfach heraus.
- **Zugriffsschutz liegt beim Ingress** (Basic Auth). Zusätzlich weist die Anwendung
  schreibende Aufrufe fremder Herkunft ab: der Browser sendet Basic-Auth-Daten bei jeder
  Anfrage an den Host automatisch mit, auch bei einem Formular, das eine fremde Seite
  abschickt — und SameSite hilft nicht, weil Basic Auth kein Cookie ist.

Ohne Ingress erreichbar über
`kubectl -n smarthome port-forward svc/mail-automation 8080:8080` → <http://localhost:8080/ui>.

## Einstellungen (`/ui/einstellungen`)

Was sich im Betrieb ändert, ohne dass sich das Deployment ändert — allen voran die
**Adresse des Inferenz-Rechners**. Ein Wechsel des Macs verlangte sonst einen Git-Push
und einen Pod-Neustart.

| Einstellung | Bedeutung |
|---|---|
| Modell-URL | Adresse von LM Studio. Ein Tailscale-Name ist stabiler als eine DHCP-Adresse |
| Modellname | wie in `lms ps` |
| Temperatur | 0 = reproduzierbar, für Klassifikation gewollt |
| Confidence-Schwelle | darunter wird nur `AI/Unsicher` gesetzt |
| Mails pro Lauf | Obergrenze je Lauf und Konto |
| Absender → Werbung / Benachrichtigung | Vorfilter, ohne Modell entschieden |

Alles gilt **sofort für den nächsten Lauf**, ohne Neustart. Gespeicherte Werte
überstimmen die Umgebungsvariablen (`LMSTUDIO_URL`, `LMSTUDIO_MODEL` …); diese sind nur
noch der Ausgangswert. Ein Knopf setzt auf sie zurück.

Der Verbindungstest ist **keine Bedingung fürs Speichern**: Ein schlafender Mac ist
Normalbetrieb (Grundsatz 6) — wäre die Erreichbarkeit Pflicht, liesse sich die Adresse
eines ausgeschalteten Rechners nie eintragen, also genau dann nicht, wenn man es braucht.

Poll-Intervall und Digest-Zeitpunkt bleiben bewusst Deployment-Sache: sie werden beim
Start in den Scheduler eingehängt.

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

CI baut bei jedem Push auf `main` ein Image nach `ghcr.io/urinet-ch/mail-automation`
und taggt es mit `main-<sha>` (siehe `.github/workflows/build.yml`). Image-Automation
ist bewusst aus — der Tag wird von Hand gehoben.

**Die Manifeste liegen nicht in diesem Repo**, sondern als ArgoCD-App unter
`apps/mail-automation/` im Infra-Repo `onprem-infrastructure`; ein `git push` dort löst
den Rollout aus. Sie zusätzlich hier zu führen hiesse, dieselbe Angabe an zwei Orten zu
pflegen — und dabei gewinnt in der Praxis die stillere Quelle. Was ein eigenes Deployment
braucht, steht in [`deploy/README.md`](deploy/README.md).

Metriken liegen auf `/q/metrics` (Prometheus), Health auf `/q/health`. Sinnvoller Alarm:
`mail_triage_pending > 0` für länger als 24 h (Inferenz-Rechner dauerhaft weg).

## Konfiguration

| Env | Default | Bedeutung |
|---|---|---|
| `TRIAGE_CRYPTO_KEY` | — | 32 Byte base64, verschlüsselt die Kontopasswörter (Pflicht, sobald ein Konto existiert) |
| `GMAIL_USER` | leer | **Alt-Konfiguration.** Steht sie noch, wird sie beim Start einmalig als Konto übernommen; danach entfernbar |
| `GMAIL_APP_PASSWORD` | leer | ebenso |
| `LMSTUDIO_URL` | `http://localhost:1234` | **Ausgangswert** für die Modell-Adresse; danach in `/ui/einstellungen` gepflegt |
| `LMSTUDIO_MODEL` | `google/gemma-4-26b-a4b` | ebenso, Modellname wie in `lms ps` |
| `TRIAGE_POLL_INTERVAL` | `10m` | Poll-Intervall |
| `DIGEST_CRON` | `0 0 7 * * ?` | Digest-Zeitpunkt (Quartz, TZ des Containers) |
| `TRIAGE_WERBUNG_ABSENDER` | leer | Ausgangswert; danach in `/ui/einstellungen` |
| `TRIAGE_BENACHRICHTIGUNG_ABSENDER` | leer | ebenso |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | — | PostgreSQL |

## Entstehung

Etappe 1 des Projekts „Lokale Mail-Triage": Die Klassifikations-Taxonomie (insbesondere
die Aktion-Achse statt eines binären „wichtig") wurde vorab interaktiv mit LM Studio
Bionic und einem eigenen Gmail-MCP-Server an der echten Inbox validiert.
