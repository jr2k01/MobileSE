// Verschickt die Push-Benachrichtigungen der Crew.
//
// Aufgerufen von einem Datenbank-Webhook, sobald in `activities` eine Zeile
// entsteht. Die Funktion schickt drei Arten von Nachrichten:
//
//   activity  - an alle anderen der Crew: jemand hat trainiert
//   overtake  - an den, der dadurch ueberholt wurde
//   lead      - an alle anderen, wenn die Spitze gewechselt hat
//
// Ausschliesslich Datennachrichten und keine notification-Nutzlast: sonst wuerde
// Android sie bei geschlossener App selbst anzeigen, mit den Texten von hier
// statt denen aus den Sprachdateien der App, ohne den richtigen Kanal und ohne
// Ziel beim Antippen.
//
// ACHTUNG - bewusste Doppelung: die Punkte werden hier ein zweites Mal
// gerechnet, weil der Rang in der App aus Aktivitaeten, Belohnungen und
// Schritten entsteht und die Datenbank ihn nicht kennt. Aendert sich die Regel
// in PointsCalculator, StepGoal, Streak oder Scoreboard, muss sie hier
// nachgezogen werden. Sauberer waere eine Datenbankfunktion als einzige Quelle -
// fuer ein Kursprojekt mehr Aufwand als Nutzen, aber es ist eine Entscheidung
// und kein Versehen.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// --- Punkte, gespiegelt aus der App ---

const INTENSITY: Record<string, number> = { LOW: 1, MEDIUM: 2, HIGH: 3 };
const DAILY_STEPS = 10000;
const STEP_BONUS = 15;

/** Entspricht PointsCalculator.calculateWorkoutPoints. */
function workoutPoints(duration: number, intensity: string): number {
  if (duration < 10) return 0;
  const multiplier = INTENSITY[(intensity ?? "").toUpperCase()] ?? INTENSITY.MEDIUM;
  return Math.round((duration / 10) * multiplier + 5);
}

/** Entspricht Streak.TIERS, laengste Stufe zuerst. */
const STREAK_TIERS: Array<[number, number]> = [
  [30, 1.5],
  [20, 1.3],
  [10, 1.2],
  [5, 1.1],
];

/** Entspricht Streak.multiplierFor. */
function streakMultiplier(days: number): number {
  for (const [from, multiplier] of STREAK_TIERS) {
    if (days >= from) return multiplier;
  }
  return 1.0;
}

/**
 * Entspricht ActivityTime.dayOf: der Tag eines Zeitstempels als ISO-Datum.
 *
 * Beide Formate, weil in der Datenbank beide stehen: neuere Eintraege in ISO,
 * aeltere im deutschen Format aus einer frueheren Fassung der App. Die ersten
 * zehn Zeichen abzuschneiden genuegt nicht - "10.08.2026" ist ebenfalls zehn
 * Zeichen lang und ergibt als Datum gelesen Unsinn.
 *
 * Leer, wenn sich nichts lesen laesst; der Eintrag zaehlt dann zu keinem Tag.
 */
function dayOf(timestamp: string | null | undefined): string {
  const value = (timestamp ?? "").trim();

  const iso = value.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`;

  const legacy = value.match(/^(\d{2})\.(\d{2})\.(\d{4})/);
  if (legacy) return `${legacy[3]}-${legacy[2]}-${legacy[1]}`;

  return "";
}

/** Entspricht Streak.activeDays: Tage mit Workout oder erreichtem Schrittziel. */
function activeDays(userId: string, activities: any[], stepDays: any[]): Set<string> {
  const days = new Set<string>();
  for (const a of activities) {
    if (a.user_id !== userId) continue;
    const day = dayOf(a.timestamp);
    if (day) days.add(day);
  }
  for (const s of stepDays) {
    if (s.user_id === userId && (s.steps ?? 0) >= DAILY_STEPS) days.add(s.day);
  }
  return days;
}

/** Entspricht Streak.endingOn: Laenge der Serie, die an diesem Tag endet. */
function streakEndingOn(days: Set<string>, day: string): number {
  const current = new Date(`${day}T00:00:00Z`);
  // Ein unlesbares Datum darf die Funktion nicht zum Absturz bringen:
  // toISOString wirft bei einem ungueltigen Date, und ein Absturz hier haette
  // alle Benachrichtigungen der Crew verschluckt.
  if (Number.isNaN(current.getTime())) return 0;

  let length = 0;
  while (days.has(current.toISOString().slice(0, 10))) {
    length++;
    current.setUTCDate(current.getUTCDate() - 1);
  }
  return length;
}

/**
 * Entspricht Scoreboard.build: Training, Belohnungen und Schrittbonus.
 *
 * Der Aufschlag der Serie gilt je Tag mit dem Stand von damals - deshalb wird
 * je Aktivitaet gerechnet und nicht auf die Summe.
 */
function pointsOf(userId: string, activities: any[], rewards: any[], stepDays: any[]): number {
  const days = activeDays(userId, activities, stepDays);

  const training = activities
    .filter((a) => a.user_id === userId)
    .reduce((sum, a) => {
      const base = workoutPoints(a.duration ?? 0, a.intensity);
      const day = dayOf(a.timestamp);
      if (!day) return sum + base;
      return sum + Math.round(base * streakMultiplier(streakEndingOn(days, day)));
    }, 0);

  const challenges = rewards
    .filter((r) => r.user_id === userId)
    .reduce((sum, r) => sum + (r.points ?? 0), 0);

  const steps =
    stepDays.filter((s) => s.user_id === userId && (s.steps ?? 0) >= DAILY_STEPS).length *
    STEP_BONUS;

  return training + challenges + steps;
}

/** Die Rangliste, absteigend. Bei Gleichstand entscheidet der Name - wie in der App. */
function ranking(members: any[], activities: any[], rewards: any[], stepDays: any[]) {
  return members
    .map((m) => ({
      id: m.id,
      name: (m.display_name || m.name || "").trim(),
      points: pointsOf(m.id, activities, rewards, stepDays),
    }))
    .sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

// --- Firebase ---

function base64url(input: string): string {
  return btoa(input).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToBytes(pem: string): ArrayBuffer {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0)).buffer;
}

/**
 * Holt ein Zugriffstoken fuer die FCM-HTTP-v1-Schnittstelle.
 *
 * Der Dienstkontoschluessel steht als Secret FIREBASE_SERVICE_ACCOUNT in der
 * Funktion. Er darf niemals in die App: mit ihm laesst sich an jedes Geraet
 * des Projekts senden.
 */
async function accessToken(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64url(
    JSON.stringify({
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claim}`),
  );
  const signed = base64url(String.fromCharCode(...new Uint8Array(signature)));

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claim}.${signed}`,
    }),
  });
  return (await response.json()).access_token;
}

/**
 * Schickt eine Nachricht an eine Kennung.
 *
 * Ein Fehlschlag wird nur vermerkt: eine abgelaufene Kennung eines Geraets darf
 * nicht verhindern, dass die uebrigen ihre Nachricht bekommen.
 */
async function send(
  token: string,
  projectId: string,
  bearer: string,
  data: Record<string, string>,
) {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data } }),
    },
  );
  if (!response.ok) {
    console.error(`FCM refused a token: ${response.status} ${await response.text()}`);
  }
}

// --- Ablauf ---

/** Der Zugang zur Datenbank mit vollen Rechten. */
function admin() {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    // Der Service-Role-Key darf nur hier liegen, nie in der App: er umgeht
    // saemtliche Row-Level-Security-Regeln.
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
}

/**
 * Eine Crew wurde herausgefordert.
 *
 * Benachrichtigt wird nur die herausgeforderte Seite: die andere hat den
 * Battle gerade selbst angelegt. Und nur bei einer neuen Herausforderung -
 * spaetere Aenderungen an derselben Zeile, etwa das Annehmen, loesen den
 * Trigger nicht aus, weil er an INSERT haengt.
 */
async function handleBattle(challenge: any): Promise<Response> {
  if (!challenge?.opponent_crew_id) return new Response("no opponent", { status: 200 });

  const supabase = admin();

  const [{ data: memberRows }, { data: crew }] = await Promise.all([
    supabase.from("crew_members").select("user_id").eq("crew_id", challenge.opponent_crew_id),
    supabase.from("crews").select("name").eq("id", challenge.crew_id).maybeSingle(),
  ]);

  const memberIds = (memberRows ?? []).map((m: any) => m.user_id);
  if (memberIds.length === 0) return new Response("empty crew", { status: 200 });

  const { data: tokens } = await supabase
    .from("device_tokens")
    .select("token, user_id")
    .in("user_id", memberIds);

  if (!tokens || tokens.length === 0) return new Response("no devices", { status: 200 });

  const serviceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const bearer = await accessToken(serviceAccount);

  for (const row of tokens) {
    // Wer in beiden Crews ist, hat den Battle womoeglich selbst angelegt.
    // Ihn trotzdem zu benachrichtigen ist harmlos - er darf ihn ja auch
    // annehmen, und die App zeigt ihm dieselbe Karte.
    await send(row.token, serviceAccount.project_id, bearer, {
      type: "battle",
      crew: crew?.name ?? "",
      // Die Art bleibt der gespeicherte Name; uebersetzt wird sie in der App,
      // die als einzige die Sprachdateien kennt.
      challenge_type: challenge.type ?? "",
      goal: String(challenge.goal ?? 0),
    });
  }

  return new Response("ok", { status: 200 });
}

Deno.serve(async (request) => {
  const payload = await request.json();
  const record = payload.record;

  // Zwei Ausloeser, eine Funktion: eine neue Aktivitaet und eine neue
  // Herausforderung. Erkennbar an der Zeile selbst - eine Challenge mit
  // Gegner hat ein Feld, das eine Aktivitaet nie hat. Eine zweite Funktion
  // waere ein zweiter Namen, ein zweites Ausrollen und ein zweiter Ort fuer
  // denselben Firebase-Schluessel.
  if (payload.table === "challenges" || record?.opponent_crew_id) {
    return await handleBattle(record);
  }

  const activity = record;
  if (!activity?.crew_id) return new Response("no crew", { status: 200 });

  const supabase = admin();

  const { data: memberRows } = await supabase
    .from("crew_members")
    .select("user_id")
    .eq("crew_id", activity.crew_id);

  const memberIds = (memberRows ?? []).map((m: any) => m.user_id);
  if (memberIds.length === 0) return new Response("empty crew", { status: 200 });

  const [{ data: members }, { data: activities }, { data: rewards }, { data: stepDays }] =
    await Promise.all([
      supabase.from("profiles").select("id, name, display_name").in("id", memberIds),
      supabase
        .from("activities")
        .select("id, user_id, duration, intensity, timestamp")
        .eq("crew_id", activity.crew_id),
      supabase.from("challenge_rewards").select("user_id, points").in("user_id", memberIds),
      supabase.from("step_days").select("user_id, steps").in("user_id", memberIds),
    ]);

  const after = ranking(members ?? [], activities ?? [], rewards ?? [], stepDays ?? []);
  // Die Rangliste vor diesem Workout: dieselbe Rechnung ohne die neue Zeile.
  const before = ranking(
    members ?? [],
    (activities ?? []).filter((a: any) => a.id !== activity.id),
    rewards ?? [],
    stepDays ?? [],
  );

  const authorName = after.find((e) => e.id === activity.user_id)?.name ?? "";

  const serviceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
  const bearer = await accessToken(serviceAccount);
  const projectId = serviceAccount.project_id;

  const { data: tokens } = await supabase
    .from("device_tokens")
    .select("token, user_id")
    .in("user_id", memberIds);

  const rankOf = (list: typeof after, id: string) => list.findIndex((e) => e.id === id);

  for (const row of tokens ?? []) {
    // Wer das Workout eingetragen hat, weiss davon.
    if (row.user_id === activity.user_id) continue;

    await send(row.token, projectId, bearer, {
      type: "activity",
      name: authorName,
      sport: activity.sport ?? "",
      duration: String(activity.duration ?? ""),
    });

    // Ueberholt: vorher vor dem Autor, jetzt dahinter.
    const wasAhead = rankOf(before, row.user_id) < rankOf(before, activity.user_id);
    const isBehind = rankOf(after, row.user_id) > rankOf(after, activity.user_id);

    if (wasAhead && isBehind) {
      await send(row.token, projectId, bearer, {
        type: "overtake",
        name: authorName,
        rank: String(rankOf(after, row.user_id) + 1),
      });
    } else if (before[0]?.id !== after[0]?.id && after[0]?.id === activity.user_id) {
      // Die Spitze hat gewechselt, ohne dass gerade dieser Empfaenger
      // ueberholt wurde - er soll es trotzdem erfahren.
      await send(row.token, projectId, bearer, { type: "lead", name: authorName });
    }
  }

  return new Response("ok", { status: 200 });
});
