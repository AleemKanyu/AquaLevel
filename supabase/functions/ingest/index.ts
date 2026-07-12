import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const INGEST_SECRET = Deno.env.get("INGEST_SECRET")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

serve(async (req) => {
    // Validate shared secret from ESP32
    const secret = req.headers.get("x-ingest-secret");
    if (secret !== INGEST_SECRET) {
        return new Response("Unauthorized", { status: 401 });
    }

    const { device_id, distance_cm } = await req.json();

    if (!device_id || typeof distance_cm !== "number") {
        return new Response("Bad Request", { status: 400 });
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

    // Verify device exists in DB (was registered by developer before shipping)
    const { data: device, error: devErr } = await supabase
        .from("devices")
        .select("id")
        .eq("id", device_id)
        .single();

    if (devErr || !device) {
        return new Response("Device not registered", { status: 404 });
    }

    // Insert raw reading
    const { error } = await supabase
        .from("readings")
        .insert({ device_id, distance_cm });

    if (error) return new Response(error.message, { status: 500 });

    // Update hourly_readings (upsert current hour's average)
    const now = new Date();
    const date = now.toISOString().split("T")[0];
    const hour = now.getUTCHours();

    await supabase.from("hourly_readings").upsert(
        { device_id, date, hour, avg_distance: distance_cm },
        { onConflict: "device_id,date,hour", ignoreDuplicates: false }
    );

    return new Response(JSON.stringify({ ok: true }), {
        headers: { "Content-Type": "application/json" },
    });
});
