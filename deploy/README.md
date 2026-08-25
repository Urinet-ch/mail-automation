# deploy/

Hier liegen **keine** Deployment-Manifeste mehr, nur die Secret-Vorlage.

Die produktiven Manifeste stehen als ArgoCD-App unter `apps/mail-automation/` im
privaten Infra-Repo `onprem-infrastructure`. Sie hier zusaetzlich zu fuehren hiess,
dieselbe Angabe an zwei Orten zu pflegen — und in der Praxis gewinnt dabei die
stillere Quelle: Die Kopie hier war nach wenigen Tagen bereits fuenffach veraltet
(altes Image, fehlender `TRIAGE_CRYPTO_KEY`, fehlender DB-Init-Job, fehlender
`runAsUser`, kein Ingress) und haette eine nicht startende Anwendung ausgerollt.

## Was ein eigenes Deployment braucht

Wer die Anwendung ohne dieses Infra-Repo betreibt, braucht:

1. **Image** `ghcr.io/urinet-ch/mail-automation:<tag>` (oeffentlich lesbar).
2. **PostgreSQL** mit einer eigenen Datenbank. Das Schema legt Liquibase beim Start
   selbst an — die **Datenbank und die Rolle** aber nicht, die muessen vorher
   existieren.
3. **Umgebungsvariablen** aus `secret.example.yaml`, dazu `LMSTUDIO_URL` auf den
   Rechner mit LM Studio. Die vollstaendige Liste steht in der
   [Konfigurationstabelle](../README.md#konfiguration).
4. **Nicht-root-Kontext:** Das Image laeuft als `USER 1001`. Bei `runAsNonRoot: true`
   muss `runAsUser: 1001` gesetzt sein.
5. **Zugriffsschutz fuer `/ui`.** Dort werden Mail-Zugangsdaten verwaltet. Die
   Anwendung bringt selbst keine Authentifizierung mit; das gehoert vor sie
   (Reverse Proxy, Basic Auth oder besser). `/triage/run` und `/triage/digest`
   sollten gar nicht erst nach aussen zeigen.
