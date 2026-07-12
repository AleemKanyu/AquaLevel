
// We can just use REST API for simplicity since we don't have the service account key easily available.
// Actually, we can just use fetch to hit the Firestore REST endpoint.
const projectId = "aqualevel-383e2";
const baseUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/devices/esp32_01`;

async function checkCollections() {
    try {
        const mainRes = await fetch(baseUrl);
        const mainData = await mainRes.json();
        console.log("MAIN DOCUMENT (LIVE):", JSON.stringify(mainData, null, 2));

        const hourlyRes = await fetch(`${baseUrl}/hourly_current`);
        const hourlyData = await hourlyRes.json();
        console.log("HOURLY CURRENT:", JSON.stringify(hourlyData, null, 2).substring(0, 500) + '...');

        const dailyRes = await fetch(`${baseUrl}/daily`);
        const dailyData = await dailyRes.json();
        console.log("DAILY:", JSON.stringify(dailyData, null, 2).substring(0, 500) + '...');
    } catch (e) {
        console.error(e);
    }
}

checkCollections();
