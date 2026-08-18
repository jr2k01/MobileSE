# Push-Benachrichtigungen einrichten

Die App kann benachrichtigen, wenn jemand aus der Crew ein Workout eintraegt und
wenn sich dadurch die Rangliste verschiebt. Verschickt werden die Nachrichten
nicht von der App, sondern von Supabase - ausgeloest von einem Webhook, sobald
in `activities` eine Zeile entsteht.

**Ohne die folgenden Schritte laeuft die App vollstaendig, nur eben ohne
Benachrichtigungen.** Das Projekt baut auch ohne Firebase-Datei: das
google-services-Plugin wird nur angewendet, wenn `app/google-services.json`
tatsaechlich daliegt (siehe `app/build.gradle.kts`).

Was wo hingehoert, in einem Satz: der **Firebase-Client** kommt in die App, der
**Firebase-Dienstkontoschluessel** ausschliesslich in die Edge Function.

---

## 1. Tabelle fuer die Geraetekennungen

Supabase → **SQL Editor**:

```sql
create table if not exists device_tokens (
    token      text primary key,
    user_id    uuid not null references profiles(id) on delete cascade,
    updated_at timestamptz not null default now()
);

alter table device_tokens enable row level security;

-- Jeder verwaltet nur seine eigenen Geraete. Lesen muss die App sie nicht -
-- das tut die Edge Function mit dem Service-Role-Key.
drop policy if exists "device_tokens_write_own" on device_tokens;
create policy "device_tokens_write_own" on device_tokens
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "device_tokens_update_own" on device_tokens;
create policy "device_tokens_update_own" on device_tokens
    for update to authenticated using (auth.uid() = user_id);

drop policy if exists "device_tokens_delete_own" on device_tokens;
create policy "device_tokens_delete_own" on device_tokens
    for delete to authenticated using (auth.uid() = user_id);
```

Der Schluessel ist die Kennung und nicht der Nutzer: wer auf Telefon und Tablet
angemeldet ist, soll auf beiden benachrichtigt werden. Meldet sich auf einem
Geraet jemand anderes an, wandert die Zeile per Upsert zum neuen Konto.

---

## 2. Firebase-Projekt anlegen

1. https://console.firebase.google.com → **Projekt hinzufuegen**, Name z. B.
   `CrewFit`. Google Analytics kann aus bleiben.
2. Im Projekt auf das **Android**-Symbol: Paketname genau
   `com.example.mobilese` eintragen, registrieren.
3. Die angebotene **`google-services.json`** herunterladen und in den Ordner
   **`app/`** legen - also `app/google-services.json`, neben `build.gradle.kts`.
4. Android Studio → Gradle-Sync. Ab jetzt wird das Plugin angewendet.

Die Datei enthaelt keinen geheimen Schluessel; sie gehoert in die App. Trotzdem
steht sie in `.gitignore`, weil sie zu deinem Firebase-Projekt gehoert und nicht
zum Quelltext.

---

## 3. Dienstkontoschluessel holen

Firebase-Konsole → Zahnrad → **Projekteinstellungen** → **Dienstkonten** →
**Neuen privaten Schluessel generieren**. Es faellt eine JSON-Datei an.

**Diese Datei ist geheim.** Mit ihr laesst sich an jedes Geraet des Projekts
senden. Sie darf nicht ins Repository und niemals in die App.

---

## 4. Edge Function ausrollen

Die Funktion liegt im Projekt unter `supabase/functions/notify/`.

Einmalig die Supabase-CLI einrichten und anmelden:

```bash
npx supabase login
```

Dann im Projektordner:

```bash
npx supabase link --project-ref ghhtaaoedlvhipmnuziu
```

Den Dienstkontoschluessel als Secret hinterlegen - der ganze Inhalt der JSON aus
Schritt 3 als eine Zeile:

```bash
npx supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat pfad/zur/serviceaccount.json)"
```

Und ausrollen:

```bash
npx supabase functions deploy notify --no-verify-jwt
```

`--no-verify-jwt` ist noetig, weil der Aufruf vom Datenbank-Webhook kommt und
nicht von einem angemeldeten Nutzer.

`SUPABASE_URL` und `SUPABASE_SERVICE_ROLE_KEY` setzt Supabase in Edge Functions
von selbst; die muessen nicht hinterlegt werden.

---

## 5. Webhook auf die Aktivitaeten

Supabase → **Database** → **Webhooks** → **Create a new hook**:

| Feld | Wert |
| --- | --- |
| Name | `notify_on_activity` |
| Table | `activities` |
| Events | nur **Insert** |
| Type | **Supabase Edge Functions** |
| Edge Function | `notify` |
| Method | `POST` |

Damit laeuft die Funktion bei jedem neuen Workout an.

---

## 6. Ausprobieren

1. App auf zwei Geraeten mit zwei Konten derselben Crew anmelden. Beim Anmelden
   hinterlegt die App ihre Kennung - in `device_tokens` sollten danach zwei
   Zeilen stehen.
2. Auf dem einen Geraet in den Einstellungen unter **NOTIFICATIONS** die
   Erlaubnis erteilen (ab Android 13 fragt das System danach).
3. Auf dem anderen Geraet ein Workout eintragen.
4. Auf dem ersten Geraet sollte die Meldung erscheinen - auch bei geschlossener
   App.

Kommt nichts an, in dieser Reihenfolge nachsehen:

- Supabase → **Edge Functions** → `notify` → **Logs**. Dort steht, ob die
  Funktion ueberhaupt lief und was FCM geantwortet hat.
- Steht in `device_tokens` eine Zeile fuer den Empfaenger?
- Ist die Benachrichtigungserlaubnis erteilt? Einstellungen → NOTIFICATIONS.
- Ein Emulator **ohne Google Play** kann kein FCM. Es braucht ein Abbild mit
  Play Store oder ein echtes Geraet.

---

## Was die Nachrichten enthalten

Der Server schickt ausschliesslich Datennachrichten, keine
`notification`-Nutzlast. Sonst wuerde Android sie bei geschlossener App selbst
anzeigen - mit den Texten vom Server statt aus den Sprachdateien der App, im
falschen Kanal und ohne Ziel beim Antippen. So geht jede Nachricht durch
`CrewFitMessagingService` und damit durch `PushMessages`.

| Art | Wann | Feld |
| --- | --- | --- |
| `activity` | jemand traegt ein Workout ein | `name`, `sport`, `duration` |
| `overtake` | der Empfaenger wurde dadurch ueberholt | `name`, `rank` |
| `lead` | die Spitze der Crew hat gewechselt | `name` |

Zwei Kanaele, `crew_activities` und `crew_ranking`, damit sich beides getrennt
abschalten laesst: wen die Workouts der anderen nerven, soll trotzdem erfahren
koennen, dass er ueberholt wurde.

## Die Doppelung der Punkteregel

Die Edge Function rechnet die Punkte ein zweites Mal, in TypeScript. Sie muss
das, weil der Rang in der App aus Aktivitaeten, Belohnungen und Schritten
entsteht und die Datenbank ihn nicht kennt - dieselbe Lage wie beim Bild der
Nummer eins.

**Aendert sich die Punkteregel in `PointsCalculator`, `StepGoal` oder
`Scoreboard`, muss sie in `supabase/functions/notify/index.ts` nachgezogen
werden.** Sonst behauptet eine Benachrichtigung einen Rangwechsel, den die App
nicht zeigt. Sauberer waere eine Datenbankfunktion als einzige Quelle beider
Seiten; fuer ein Kursprojekt ist das mehr Aufwand als Nutzen, aber es ist eine
Entscheidung und kein Versehen.
