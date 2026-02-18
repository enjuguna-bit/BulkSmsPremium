// UPDATED WITH CORS & SIGNATURE HANDLING
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    const normalizePhone = (value) => {
      if (!value) return null;
      const digits = String(value).replace(/\D/g, "");
      if (digits.startsWith("0") && digits.length === 10) {
        return "254" + digits.substring(1);
      }
      if (digits.startsWith("254") && digits.length === 12) {
        return digits;
      }
      if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length === 9) {
        return "254" + digits;
      }
      return null;
    };

    const normalizeDeviceId = (value) => {
      if (!value) return null;
      const cleaned = String(value).trim();
      if (cleaned.length < 6 || cleaned.length > 128) return null;
      if (!/^[A-Za-z0-9._:-]+$/.test(cleaned)) return null;
      return cleaned;
    };

    const toNumber = (value) => {
      if (typeof value === "number") return value;
      if (typeof value === "string") {
        const parsed = parseFloat(value);
        return Number.isFinite(parsed) ? parsed : null;
      }
      return null;
    };

    const extractPayload = (body) => {
      if (body && typeof body === "object") {
        return body.data || body.payload || body;
      }
      return {};
    };

    const extractPhone = (payload, reference) => {
      const candidate =
        payload.phone ||
        payload.customer_phone ||
        payload.customer_contact ||
        payload.phone_number ||
        payload.msisdn ||
        payload.payer_phone ||
        payload.customer?.phone ||
        payload.customer?.phone_number ||
        payload.customer?.contact ||
        payload.meta?.phone ||
        payload.extra?.phone;

      const normalized = normalizePhone(candidate);
      if (normalized) return normalized;

      if (reference) {
        const match = String(reference).match(/(\+?254|0)?(7\d{8}|1\d{8})/);
        if (match) {
          return normalizePhone(match[0]);
        }
      }

      return null;
    };

    const extractDeviceId = (payload, reference) => {
      const candidate =
        payload.device_id ||
        payload.deviceId ||
        payload.install_id ||
        payload.installId ||
        payload.app_instance_id ||
        payload.appInstanceId ||
        payload.meta?.device_id ||
        payload.meta?.install_id ||
        payload.meta?.app_instance_id ||
        payload.extra?.device_id ||
        payload.extra?.install_id ||
        payload.extra?.app_instance_id;

      const normalized = normalizeDeviceId(candidate);
      if (normalized) return normalized;

      if (reference) {
        const match = String(reference).match(
          /(?:device|install|instance|app|aid|did)[=:]([A-Za-z0-9._:-]{6,128})/i
        );
        if (match) {
          return normalizeDeviceId(match[1]);
        }
      }

      return null;
    };

    const isSuccessStatus = (status, event) => {
      const statusValue = status ? String(status).toLowerCase() : "";
      const eventValue = event ? String(event).toLowerCase() : "";
      const okStatuses = new Set([
        "success",
        "successful",
        "paid",
        "complete",
        "completed",
        "ok",
      ]);
      const okEvents = new Set([
        "payment.success",
        "payment.succeeded",
        "payment.completed",
        "charge.success",
        "collection.success",
      ]);
      return okStatuses.has(statusValue) || okEvents.has(eventValue);
    };

    const normalizePlan = (value) => {
      if (!value) return null;
      const cleaned = String(value).toLowerCase().replace(/[^a-z0-9]/g, "");
      if (cleaned === "weekly" || cleaned === "week") return "weekly";
      if (cleaned === "daily" || cleaned === "day") return "daily";
      if (cleaned === "sixhour" || cleaned === "6hour" || cleaned === "6hr" || cleaned === "6h") {
        return "six_hour";
      }
      if (cleaned === "onehour" || cleaned === "1hour" || cleaned === "1hr" || cleaned === "1h" || cleaned === "hour") {
        return "one_hour";
      }
      return null;
    };

    const extractPlan = (payload, reference, amount) => {
      const candidate =
        payload.plan ||
        payload.subscription_plan ||
        payload.meta?.plan ||
        payload.extra?.plan;
      const normalized = normalizePlan(candidate);
      if (normalized) return normalized;

      if (reference) {
        const match = String(reference).match(/plan=([a-z0-9_-]+)/i);
        if (match) {
          const refPlan = normalizePlan(match[1]);
          if (refPlan) return refPlan;
        }
      }

      if (amount != null) {
        if (amount >= 1000) return "weekly";
        if (amount >= 200) return "daily";
        if (amount >= 100) return "six_hour";
        if (amount >= 60) return "one_hour";
      }

      return "daily";
    };

    const addPlanDuration = (baseDate, plan) => {
      const date = new Date(baseDate.getTime());
      switch (plan) {
        case "weekly":
          date.setDate(date.getDate() + 7);
          return date;
        case "daily":
          date.setDate(date.getDate() + 1);
          return date;
        case "six_hour":
          date.setHours(date.getHours() + 6);
          return date;
        case "one_hour":
          date.setHours(date.getHours() + 1);
          return date;
        default:
          date.setDate(date.getDate() + 1);
          return date;
      }
    };

    const parsePaidUntil = (value) => {
      if (!value) return null;
      const ts = Date.parse(value);
      return Number.isFinite(ts) ? ts : null;
    };

    const isActiveSubscription = (subData) => {
      if (!subData) return false;
      if (subData.status && String(subData.status).toLowerCase() !== "active") {
        return false;
      }
      const paidUntil = parsePaidUntil(subData.paid_until);
      if (paidUntil && paidUntil < Date.now()) {
        return false;
      }
      return true;
    };

    const INTENT_TTL_SECONDS = 3 * 60 * 60;
    const INTENT_TXN_TTL_SECONDS = 7 * 24 * 60 * 60;

    const createIntentId = () => {
      if (globalThis.crypto?.randomUUID) {
        return globalThis.crypto.randomUUID();
      }
      return `intent_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
    };

    const extractIntentId = (payload, reference) => {
      const candidate =
        payload?.intent_id ||
        payload?.intentId ||
        payload?.meta?.intent_id ||
        payload?.extra?.intent_id;
      if (candidate) return String(candidate);

      if (reference) {
        const match = String(reference).match(/intent[=:]([A-Za-z0-9._:-]{6,128})/i);
        if (match) return match[1];
      }
      return null;
    };

    const isIntentExpired = (intentData) => {
      if (!intentData?.expires_at) return false;
      const expiresAt = Date.parse(intentData.expires_at);
      return Number.isFinite(expiresAt) && expiresAt < Date.now();
    };

    const getIntentById = async (intentId) => {
      if (!intentId) return null;
      const intent = await env.TRANSACTIONS.get(`intent:${intentId}`, { type: "json" });
      if (!intent) return null;
      if (isIntentExpired(intent)) return null;
      return intent;
    };

    const findIntentByPhoneAndAmount = async (phone, amount) => {
      if (!phone) return null;
      let intentId = null;
      if (amount != null) {
        intentId = await env.TRANSACTIONS.get(`intent:phone:${phone}:amount:${amount}`);
      }
      if (!intentId) {
        intentId = await env.TRANSACTIONS.get(`intent:phone:${phone}`);
      }
      const intent = await getIntentById(intentId);
      if (!intent) return null;
      if (amount != null && intent.amount != null && Number(intent.amount) !== Number(amount)) {
        return null;
      }
      return intent;
    };

    const saveIntent = async (intentData) => {
      if (!intentData?.id) return;
      const ttl = INTENT_TTL_SECONDS;
      await env.TRANSACTIONS.put(
        `intent:${intentData.id}`,
        JSON.stringify(intentData),
        { expirationTtl: ttl }
      );
      await env.TRANSACTIONS.put(
        `intent:phone:${intentData.phone}`,
        intentData.id,
        { expirationTtl: ttl }
      );
      if (intentData.amount != null) {
        await env.TRANSACTIONS.put(
          `intent:phone:${intentData.phone}:amount:${intentData.amount}`,
          intentData.id,
          { expirationTtl: ttl }
        );
      }
    };

    const markIntentUsed = async (intentData, txnId) => {
      if (!intentData?.id) return;
      const updated = {
        ...intentData,
        used_at: new Date().toISOString(),
        txn_id: txnId,
      };
      await env.TRANSACTIONS.put(
        `intent:${intentData.id}`,
        JSON.stringify(updated),
        { expirationTtl: INTENT_TTL_SECONDS }
      );
    };

    const isLegacyUuid = (value) =>
      typeof value === "string" &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

    const isAndroidId = (value) =>
      typeof value === "string" && /^[0-9a-f]{16}$/i.test(value);

    // ========== CORS PREFLIGHT ==========
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-Lipana-Signature, X-Lipana-Event",
        },
      });
    }

    // ========== STATUS CHECK ==========
    if (request.method === "GET" && path === "/status") {
      const phone = url.searchParams.get("phone");
      const cleanPhone = normalizePhone(phone);
      if (!cleanPhone) {
        return new Response(JSON.stringify({
          error: "Invalid phone number",
        }, null, 2), {
          status: 400,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }

      const deviceParam =
        url.searchParams.get("device_id") ||
        url.searchParams.get("deviceId") ||
        url.searchParams.get("install_id") ||
        url.searchParams.get("installId");
      const deviceId = normalizeDeviceId(deviceParam);
      if (!deviceId) {
        return new Response(JSON.stringify({
          error: "device_id is required",
        }, null, 2), {
          status: 400,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }

      // Get from KV
      const subData =
        (await env.SUBSCRIPTIONS.get(`sub:${cleanPhone}`, { type: "json" })) ||
        (await env.SUBSCRIPTIONS.get(`sub:+${cleanPhone}`, { type: "json" }));

      const response = {
        phone: cleanPhone,
        premium: false,
        message: "No active subscription",
        device_id: deviceId,
      };

      if (subData) {
        const active = isActiveSubscription(subData);
        response.plan = subData.plan;
        response.amount = subData.amount;
        response.paid_until = subData.paid_until;
        response.last_payment = subData.last_payment_at;
        response.last_txn = subData.last_txn;

        if (!active) {
          response.message = "Subscription expired or inactive";
        } else if (!subData.device_id) {
          response.message = "Payment received. Pending device binding.";
          response.reason = "device_unbound";
          response.claim_required = true;
        } else if (subData.device_id !== deviceId) {
          response.message = "Subscription is bound to a different device.";
          response.reason = "device_mismatch";
        } else {
          response.premium = true;
          response.message = "Active subscription";
        }
      }

      return new Response(JSON.stringify(response, null, 2), {
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // ========== INIT PAYMENT INTENT ==========
    if (request.method === "POST" && path === "/init") {
      try {
        const rawBody = await request.text();
        const body = rawBody ? JSON.parse(rawBody) : {};
        const payload = extractPayload(body);

        const phone = normalizePhone(payload.phone || body.phone);
        const deviceId = normalizeDeviceId(
          payload.device_id ||
          payload.deviceId ||
          body.device_id ||
          body.deviceId ||
          payload.install_id ||
          body.install_id ||
          payload.installId ||
          body.installId
        );
        const amount = toNumber(payload.amount || body.amount);
        const plan = normalizePlan(payload.plan || body.plan) ||
          extractPlan(payload, payload.reference, amount);

        if (!phone || !deviceId) {
          return new Response(JSON.stringify({
            success: false,
            error: "phone and device_id are required",
          }, null, 2), {
            status: 400,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        const intentId = createIntentId();
        const now = new Date();
        const expiresAt = new Date(now.getTime() + INTENT_TTL_SECONDS * 1000);

        const intentData = {
          id: intentId,
          phone,
          device_id: deviceId,
          plan,
          amount,
          created_at: now.toISOString(),
          expires_at: expiresAt.toISOString(),
        };

        await saveIntent(intentData);
        await env.LOGS.put(`log:${Date.now()}:intent:${intentId}`, JSON.stringify({
          type: "intent",
          phone,
          device_id: deviceId,
          amount,
          plan,
          intent_id: intentId,
          timestamp: now.toISOString(),
        }));

        return new Response(JSON.stringify({
          success: true,
          intent_id: intentId,
          expires_at: intentData.expires_at,
        }, null, 2), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      } catch (error) {
        return new Response(JSON.stringify({
          success: false,
          error: "Processing error",
          message: error.message,
        }, null, 2), {
          status: 500,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
    }

    // ========== CLAIM SUBSCRIPTION ==========
    if (request.method === "POST" && path === "/claim") {
      try {
        const rawBody = await request.text();
        const body = rawBody ? JSON.parse(rawBody) : {};
        const payload = extractPayload(body);

        const phone = normalizePhone(payload.phone || body.phone);
        const deviceId = normalizeDeviceId(
          payload.device_id ||
          payload.deviceId ||
          body.device_id ||
          body.deviceId ||
          payload.install_id ||
          body.install_id ||
          payload.installId ||
          body.installId
        );
        const receipt =
          payload.mpesa_receipt ||
          payload.receipt_number ||
          payload.mpesaReceipt ||
          payload.receipt ||
          body.mpesa_receipt ||
          body.receipt_number ||
          body.mpesaReceipt ||
          body.receipt;
        const intentId =
          payload.intent_id ||
          payload.intentId ||
          body.intent_id ||
          body.intentId ||
          extractIntentId(payload, payload.reference) ||
          extractIntentId(body, body.reference);
        let txnId =
          payload.transaction_id ||
          payload.id ||
          body.transaction_id ||
          body.id ||
          body.txn_id ||
          payload.txn_id;

        if (!deviceId || (!phone && !txnId && !receipt && !intentId)) {
          return new Response(JSON.stringify({
            success: false,
            error: "phone, device_id, and receipt, transaction_id, or intent_id are required",
          }, null, 2), {
            status: 400,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        if (!txnId && receipt) {
          txnId = await env.TRANSACTIONS.get(`receipt:${receipt}`);
        }

        if (!txnId && intentId) {
          txnId = await env.TRANSACTIONS.get(`intent_txn:${intentId}`);
        }

        if (!txnId && intentId) {
          const intent = await getIntentById(intentId);
          if (!intent) {
            return new Response(JSON.stringify({
              success: false,
              error: "Intent not found or expired",
            }, null, 2), {
              status: 404,
              headers: {
                "Content-Type": "application/json",
                "Access-Control-Allow-Origin": "*",
              },
            });
          }
          if (intent.device_id && intent.device_id !== deviceId) {
            return new Response(JSON.stringify({
              success: false,
              error: "Device does not match intent",
            }, null, 2), {
              status: 403,
              headers: {
                "Content-Type": "application/json",
                "Access-Control-Allow-Origin": "*",
              },
            });
          }
          return new Response(JSON.stringify({
            success: false,
            error: "Payment pending",
            reason: "payment_pending",
            retry_after_seconds: 20,
          }, null, 2), {
            status: 202,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        if (!txnId) {
          return new Response(JSON.stringify({
            success: false,
            error: "Transaction not found",
          }, null, 2), {
            status: 404,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        const txnData = await env.TRANSACTIONS.get(`txn:${txnId}`, { type: "json" });
        if (!txnData || !isSuccessStatus(txnData.status, txnData.event)) {
          return new Response(JSON.stringify({
            success: false,
            error: "Transaction not eligible",
          }, null, 2), {
            status: 400,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        let intentDataForClaim = null;
        if (intentId) {
          intentDataForClaim = await getIntentById(intentId);
        }
        if (intentDataForClaim?.device_id && intentDataForClaim.device_id !== deviceId) {
          return new Response(JSON.stringify({
            success: false,
            error: "Device does not match intent",
          }, null, 2), {
            status: 403,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        const txnPhone = normalizePhone(txnData.phone);
        const intentPhone = intentDataForClaim?.phone ? normalizePhone(intentDataForClaim.phone) : null;
        const cleanPhone = phone || txnPhone || intentPhone;
        if (!cleanPhone || (txnPhone && txnPhone !== cleanPhone)) {
          return new Response(JSON.stringify({
            success: false,
            error: "Phone does not match transaction",
          }, null, 2), {
            status: 403,
            headers: {
              "Content-Type": "application/json",
              "Access-Control-Allow-Origin": "*",
            },
          });
        }

        const plan = normalizePlan(txnData.plan) ||
          extractPlan({ plan: txnData.plan }, txnData.reference, txnData.amount);
        let expiry = txnData.paid_until ? new Date(txnData.paid_until) : null;
        if (!expiry || Number.isNaN(expiry.getTime())) {
          const base = txnData.timestamp ? new Date(txnData.timestamp) : new Date();
          expiry = addPlanDuration(base, plan);
        }

        const subData = {
          phone: cleanPhone,
          status: "active",
          plan,
          amount: txnData.amount,
          paid_until: expiry.toISOString(),
          last_txn: txnId,
          last_payment_at: txnData.timestamp || new Date().toISOString(),
          mpesa_receipt: txnData.mpesa_receipt || receipt || "",
          reference: txnData.reference || "",
          device_id: deviceId,
          updated_at: new Date().toISOString(),
        };

        await env.SUBSCRIPTIONS.put(`sub:${cleanPhone}`, JSON.stringify(subData));
        await env.SUBSCRIPTIONS.put(`sub:+${cleanPhone}`, JSON.stringify(subData));
        if (intentDataForClaim) {
          await markIntentUsed(intentDataForClaim, txnId);
        }

        return new Response(JSON.stringify({
          success: true,
          message: "Subscription bound to device",
          phone: cleanPhone,
          plan,
          paid_until: subData.paid_until,
        }, null, 2), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      } catch (error) {
        return new Response(JSON.stringify({
          success: false,
          error: "Processing error",
          message: error.message,
        }, null, 2), {
          status: 500,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
    }

    // ========== HOME PAGE ==========
    if (request.method === "GET" && path === "/") {
      const html = `<!DOCTYPE html>
<html>
<head>
  <title>Payment System Active</title>
  <style>
    body { font-family: Arial; padding: 20px; max-width: 800px; margin: 0 auto; }
    .success { color: #28a745; font-weight: bold; }
    .test-btn { background: #28a745; color: white; padding: 12px 24px; border: none; border-radius: 5px; cursor: pointer; margin: 10px; font-size: 16px; }
    .test-btn:hover { background: #218838; }
    #result { margin-top: 20px; padding: 15px; background: #f8f9fa; border-radius: 5px; border: 1px solid #dee2e6; }
    pre { background: #343a40; color: #f8f9fa; padding: 10px; border-radius: 5px; overflow: auto; }
  </style>
</head>
<body>
  <h1>Payment System Active</h1>
  <p class="success">Lipa Na Dev M-PESA payments are LIVE!</p>
  <p>Webhook URL: <code>https://bulksmsbilling.enjuguna794.workers.dev/</code></p>

  <h3>Test Endpoints:</h3>
  <button class="test-btn" onclick="testStatus()">Test Status API</button>
  <button class="test-btn" onclick="testPayment()">Test Webhook</button>
  <button class="test-btn" onclick="viewLogs()">View Recent Logs</button>

  <div id="result"></div>

  <script>
    async function testStatus() {
      const result = document.getElementById("result");
      result.innerHTML = "Testing status for 254712345678...";

      try {
        const response = await fetch("/status?phone=254712345678&device_id=test-device-123");
        const data = await response.json();
        result.innerHTML = "<h4>Status Result:</h4><pre>" + JSON.stringify(data, null, 2) + "</pre>";
      } catch (error) {
        result.innerHTML = "Error: " + error.message;
      }
    }

    async function testPayment() {
      const result = document.getElementById("result");
      result.innerHTML = "Simulating test payment...";

      try {
        const response = await fetch("/", {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({
            transaction_id: "TEST_" + Date.now(),
            phone: "254712345678",
            amount: 200,
            status: "success",
            mpesa_receipt: "MPESA" + Math.random().toString(36).substr(2, 8).toUpperCase(),
            reference: "user_123_daily_device=test-device-123_" + Date.now(),
            timestamp: new Date().toISOString()
          })
        });

        const data = await response.json();
        result.innerHTML = "<h4>Payment Test Result:</h4><pre>" + JSON.stringify(data, null, 2) + "</pre>";

        // Check status after payment
        setTimeout(testStatus, 1000);
      } catch (error) {
        result.innerHTML = "Error: " + error.message;
      }
    }

    async function viewLogs() {
      const result = document.getElementById("result");
      result.innerHTML = "Checking recent logs...<br><em>Note: Logs appear in Cloudflare dashboard</em>";
    }
  </script>
</body>
</html>`;

      return new Response(html, { headers: { "Content-Type": "text/html" } });
    }

    // ========== LIPA NA DEV WEBHOOK HANDLER ==========
    if (request.method === "POST" && path === "/") {
      try {
        // Get raw body for signature verification
        const rawBody = await request.text();
        const signature = request.headers.get("x-lipana-signature");
        const event = request.headers.get("x-lipana-event");

        // Parse JSON
        const body = JSON.parse(rawBody);
        const payload = extractPayload(body);

        // Log everything for debugging
        console.log("Lipa Na Dev Webhook Received");
        console.log("Event:", event);
        console.log("Signature:", signature);
        console.log("Payment Data:", JSON.stringify(body, null, 2));

        // Extract data
        const txnId = payload.transaction_id || payload.id || "txn_" + Date.now();
        const reference = payload.reference || payload.custom_reference || payload.account || "";
        let cleanPhone = extractPhone(payload, reference);
        let amount = toNumber(payload.amount || payload.total_amount || payload.amount_received);
        const status = payload.status || payload.state || "unknown";
        const receipt = payload.mpesa_receipt || payload.receipt_number || payload.mpesaReceipt || "";
        let deviceId = extractDeviceId(payload, reference);

        const intentId = extractIntentId(payload, reference);
        let intentData = intentId ? await getIntentById(intentId) : null;
        if (!intentData && cleanPhone) {
          intentData = await findIntentByPhoneAndAmount(cleanPhone, amount);
        }

        if (!cleanPhone && intentData?.phone) {
          cleanPhone = normalizePhone(intentData.phone);
        }
        if (amount == null && intentData?.amount != null) {
          amount = intentData.amount;
        }
        let plan = extractPlan(payload, reference, amount);
        const intentPlan = normalizePlan(intentData?.plan);
        if (intentPlan) {
          plan = intentPlan;
        } else if (amount != null) {
          plan = extractPlan(payload, reference, amount);
        }
        if (!deviceId && intentData?.device_id) {
          deviceId = intentData.device_id;
        }

        console.log("Extracted:", {
          txnId,
          phone: cleanPhone,
          amount,
          status,
          receipt,
          reference,
          intent_id: intentId || intentData?.id || null,
          device_id: deviceId || null,
        });

        // 1. Save to TRANSACTIONS KV
        const txnData = {
          id: txnId,
          phone: cleanPhone || null,
          amount: amount ?? 0,
          plan,
          status,
          mpesa_receipt: receipt,
          reference,
          device_id: deviceId || null,
          intent_id: intentId || intentData?.id || null,
          event,
          signature,
          raw_body: rawBody.length > 500 ? rawBody.substring(0, 500) + "..." : rawBody,
          timestamp: new Date().toISOString(),
        };

        await env.TRANSACTIONS.put(`txn:${txnId}`, JSON.stringify(txnData));
        if (receipt) {
          await env.TRANSACTIONS.put(`receipt:${receipt}`, txnId);
        }
        if (txnData.intent_id) {
          await env.TRANSACTIONS.put(
            `intent_txn:${txnData.intent_id}`,
            txnId,
            { expirationTtl: INTENT_TXN_TTL_SECONDS }
          );
        }
        console.log("Transaction saved to KV:", txnId);

        // 2. Update SUBSCRIPTIONS if payment successful
        if (cleanPhone && isSuccessStatus(status, event)) {
          const existing =
            (await env.SUBSCRIPTIONS.get(`sub:${cleanPhone}`, { type: "json" })) ||
            (await env.SUBSCRIPTIONS.get(`sub:+${cleanPhone}`, { type: "json" }));
          const existingPaidUntil = existing?.paid_until ? new Date(existing.paid_until) : null;
          const baseDate = existingPaidUntil && existingPaidUntil.getTime() > Date.now()
            ? existingPaidUntil
            : new Date();
          const expiry = addPlanDuration(baseDate, plan);

          let boundDevice = existing?.device_id || deviceId;
          if (existing?.device_id && deviceId && existing.device_id !== deviceId) {
            const intentMatches = intentData?.device_id && intentData.device_id === deviceId;
            const legacyToStable = isLegacyUuid(existing.device_id) && isAndroidId(deviceId);
            if (intentMatches || legacyToStable) {
              console.warn("Device mismatch on renewal; rebinding to new device.");
              boundDevice = deviceId;
            } else {
              console.warn("Device mismatch on renewal; keeping existing binding.");
              boundDevice = existing.device_id;
            }
          }

          const subData = {
            phone: cleanPhone,
            status: boundDevice ? "active" : "pending_device",
            plan,
            amount,
            paid_until: expiry.toISOString(),
            last_txn: txnId,
            last_payment_at: new Date().toISOString(),
            mpesa_receipt: receipt,
            reference,
            device_id: boundDevice || null,
            updated_at: new Date().toISOString(),
          };

          await env.SUBSCRIPTIONS.put(`sub:${cleanPhone}`, JSON.stringify(subData));
          await env.SUBSCRIPTIONS.put(`sub:+${cleanPhone}`, JSON.stringify(subData));
          console.log("Subscription updated for:", cleanPhone);
          console.log("Plan:", plan, "Valid until:", expiry.toISOString());
          if (intentData) {
            await markIntentUsed(intentData, txnId);
          }
        } else if (!cleanPhone) {
          console.warn("Missing phone in webhook payload; subscription not updated.");
        }

        // 3. Log to LOGS KV
        await env.LOGS.put(`log:${Date.now()}:${txnId}`, JSON.stringify({
          type: "webhook",
          event,
          phone: cleanPhone,
          amount,
          status,
          txnId,
          timestamp: new Date().toISOString(),
        }));

        // Return success to Lipa Na Dev
        return new Response(JSON.stringify({
          success: true,
          message: "Payment processed successfully",
          transaction_id: txnId,
          phone: cleanPhone,
          amount,
          status,
          processed_at: new Date().toISOString(),
        }, null, 2), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });

      } catch (error) {
        console.error("Webhook processing error:", error);

        return new Response(JSON.stringify({
          success: false,
          error: "Processing error",
          message: error.message,
        }, null, 2), {
          status: 500,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
    }

    // ========== DEFAULT / NOT FOUND ==========
    return new Response(JSON.stringify({
      message: "Lipa Na Dev Payment System",
      endpoints: {
        "GET /": "Info dashboard",
        "GET /status?phone=254...": "Check subscription status",
        "POST /": "Lipa Na Dev webhook endpoint",
      },
      status: "active",
      timestamp: new Date().toISOString(),
    }, null, 2), {
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
    });
  }
};
